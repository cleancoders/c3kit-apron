# Schema

### c3kit.apron.schema

## Overview

The `schema` library aims to solve the following problems:

* What is expected shape of the data?
* How can I `coerce` data into the desired shape?
* How can I `validate` data?
* How can I `present` data?

## Example

Consider these maps that represent geometry.

``` clojure
{:kind :point
 :x    1
 :y    2}

{:kind  :line
 :start {:x 1 :y 2}
 :end   {:x 5 :y 6}}

{:kind :circle
 :center {:x 1 :y 1}
 :radius 5}

{:kind   :polygon
 :points [{:x 1 :y 1} {:x 2 :y 2} {:x 1 :y 2} {:x 1 :y 1}]}
```

Each data structure includes a `:kind` to tell us what the data represents.  Isn't that nice?  And they each contain their necessary data.

We can define the shape of these data structures, their schemas, like so:  

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int}
            :y    {:type :int}})

(def line {:kind  {:type :keyword}
           :start {:type point}
           :end   {:type point}})

(def circle {:kind   {:type :keyword}
             :center {:type point}
             :radius {:type :int}})

(def polygon {:kind   {:type :keyword}
              :points {:type [point]}})
```

Yes, it's just data; a map where each key is mapped to a `spec` (specification).  This is the `schema` schema; the shape of data used to define the shape of other data.

Let's start using these schemas.

## Coercion

Assume we were given the data below, and told the data represents a `point`.

``` clojure
(def data {:kind :point
           :x    "1"
           :y    "2"})
```

Note that the `:x` and `:y` values are strings.  That's not right.  They're supposed to be integers. No worries. Using our `point` schema, we can `coerce` the data in the shape we want.

``` clojure
(require '[c3kit.apron.schema :as schema])
(schema/coerce point data)
=> {:kind :point, :x 1, :y 2}         
```

Now, imagine we were given this strange `point` data.

``` clojure
(def data {:kind :point
           :x    ["1"]
           :y    ["2"]})
```

When we try to `coerce` this, we get the following.

``` clojure
(schema/coerce point data)
=>
{:kind :point,
 :x #c3kit.apron.schema.CoerceError{:message "can't coerce [\"1\"] to int"},
 :y #c3kit.apron.schema.CoerceError{:message "can't coerce [\"2\"] to int"}}
```

Note how the `:x` and `:y` keys map to `CoerceError`s containing friendly messages.  We'll use those later.

With some enhancements to our `point` schema, we can handle this type of `point` data.

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :coerce (fn [v] (schema/->int (first v)))}
            :y    {:type :int :coerce first}})
            
(schema/coerce point data)
=> {:kind :point, :x 1, :y 2}
```

For `:x` we add `:coerce` to the `spec` mapping to a **coerce function**, and we are very explicit about how to handle the data.  But we take shortcuts for `:y`.  The value for `:coerce` can be a **coerce function** or a list of **coerce function**s.  Each **coerce function** must take the value as a parameter, and return the coerced value.  So by using the function `first` for `:y` we'll end up with the string `"2"`, yet our result has the integer `2`. The final step of the `coerce` operation is **type-coercion**, using the `:type` of the spec.  `schema` knows how to convert a string to an int, so it takes care of that for you.

## Validation

Given these bits of data:   

``` clojure
(def data1 {:kind :point
            :x    1
            :y    2})
(def data2 {:kind :point
            :x    "1"
            :y    "2"})
```

Let's use `schema` to validate them.

``` clojure
(schema/validate point data1)
=> {:kind :point, :x 1, :y 2}

(schema/validate point data2)
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "is invalid"},
 :y #c3kit.apron.schema.ValidateError{:message "is invalid"}}
```

`data1` looks good but `data2` has problems.  `:x` and `:y` are strings, not ints!  `schema` is performing **type-validation** on the values based on the `:type` of the spec.

Again you can see the problematic fields are mapped to errors, `ValidationError`s this time.  However, the errors message "is invalid" is not terribly useful.  Let's improve that.

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :message "must be an int"}
            :y    {:type :int :message "must be an int"}})
            
(schema/validate point data2)
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "must be an int"},
 :y #c3kit.apron.schema.ValidateError{:message "must be an int"}}
```

We added a `:message` string to the spec and now that is being used for `coerce` and `validate` error messages.

Let's be more restrictive about our points.  For the fun of it, `:x` must be even and `:y` must be odd. 

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type     :int
                   :message  "must be an even int"
                   :validate even?}
            :y    {:type     :int
                   :message  "must be an odd int"
                   :validate odd?}})
                   
(schema/validate point {:kind :point :x "2" :y "1"})
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "must be an even int"},
 :y #c3kit.apron.schema.ValidateError{:message "must be an odd int"}}   
                 
(schema/validate point {:kind :point :x 1 :y 2})
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "must be an even int"},
 :y #c3kit.apron.schema.ValidateError{:message "must be an odd int"}}
```

We added new validations using the `:validate` entry in the spec that maps to a **validate function**.  `:validate` can also map to a list of **validate function**s that will be performed in order.  Each **validate function** must take the value as a parameter and return a truthy value if the input is valid, and falsy otherwise.   

This example demonstrates that there are two validations being performed on both `:x` and `:y`; the `even?`/`odd?` validations specified by the `:validate` entry in the spec, and that the automatic **type-validation**.  The **type-validation** is the first step in the `validate` operation, whereas **type-coercion** is that last step of the `coerce` operation.  The is demonstrated in the example below.  

Also, what if we wanted different error messages for each validation?

```clojure
(def point {:kind {:type :keyword}
            :x    {:type        :int
                   :message     "must be an int"
                   :validations [{:validate even? :message "must be even"}
                                 {:validate #(<= 0 % 100) :message "out of range"}]}
            :y    {:type        :int
                   :message     "must be an int"
                   :validations [{:validate odd? :message "must be odd"}
                                 {:validate #(<= 0 % 100) :message "out of range"}]}})

(schema/validate point {:kind :point :x "101" :y "102"})
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "must be an int"},
 :y #c3kit.apron.schema.ValidateError{:message "must be an int"}}

(schema/validate point {:kind :point :x 1 :y 2})
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "must be even"},
 :y #c3kit.apron.schema.ValidateError{:message "must be odd"}}

(schema/validate point {:kind :point :x 102 :y 101})
=>
{:kind :point,
 :x #c3kit.apron.schema.ValidateError{:message "out of range"},
 :y #c3kit.apron.schema.ValidateError{:message "out of range"}}
```

The `:validations` entry in the spec allows us to have any number of validations, each with their own message.  Each validation is a map that must have `:validate` that maps to a **validate function**, and an optional `:message`.  If `:message` is not provided in the validation map, `schema` will use the `:message` value in the `spec` if it exists, or `"is invalid"`.

`{:kind :point :x "101" :y "102"}` is invalid in all the ways we check.  But the message we get is `"must be an int"` which is our root `:message`.  This demonstrates that the **type-validation** has to pass first before any other validations take place.

## Conform

Often, we need to `coerce` data and then `validate` the coerced data.  'schema' offers a `conform` operation to take care of this for you. 

```clojure
(schema/conform point {:kind :point :x "2" :y "1"})
=> {:kind :point, :x 2, :y 1}

(schema/conform point {:kind :point :x "blah" :y "2"})
=>
{:kind :point,
 :x #c3kit.apron.schema.CoerceError{:message "must be an int"},
 :y #c3kit.apron.schema.ValidateError{:message "must be odd"}}
```

That first point is perfect and the result of `conform` is map shaped just how we like it.  The second point has a couple problems and the result of `conform` separates them nicely. `:x` cannot be coerced and `:y` is invalid.

## Present

`schema` also provides a `present` operation to make our data presentable to a user, or an API, or whatever.   

``` clojure
(schema/present point {:kind :point :x 1 :y 2})
=> {:kind :point, :x 1, :y 2}
```

Yeah, that looks the same.  We need to tell `schema` how to make the data presentable.

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :present #(str "X=" %)}
            :y    {:type :int :present #(str "Y=" %)}})
(schema/present point {:kind :point :x 1 :y 2})
=> {:kind :point, :x "X=1", :y "Y=2"}
```

The `:present` entry of the spec must map to a **present function** that takes the value as a parameter and returns a presentable value.  Unlike `:coerce` and `:validate`, `:present` may NOT map to a list of **present function**s.  

## Entity Level Specs

We have a new requirements.  A `point` may not be close to the origin (0, 0).  If a `point` has a distance from the origin of less than 5 it is invalid.  So far we've only been able to validate a single field.  Now we need both `:x` and `:y` for this validation.  We need to add an **entity level spec**.

``` clojure
(defn square [n] (* n n))
(defn distance [point] (Math/sqrt (+ (square (:x point)) (square (:y point)))))
(def point {:kind {:type :keyword}
            :x    {:type :int}
            :y    {:type :int}
            :*    {:distance {:coerce distance
                              :validate #(>= (distance %) 5) 
                              :message "too close to origin"}}})

(schema/coerce point {:kind :point :x 1 :y 2})
=> {:kind :point, :x 1, :y 2, :distance 2.23606797749979}

(schema/validate point {:kind :point :x 1 :y 2})
=> {:kind :point, :x 1, :y 2, :distance #c3kit.apron.schema.ValidateError{:message "too close to origin"}}

(schema/coerce point {:kind :point :x 4 :y 4})
=> {:kind :point, :x 4, :y 4, :distance 5.656854249492381}

(schema/validate point {:kind :point :x 4 :y 4})
=> {:kind :point, :x 4, :y 4}
```

`:*` is a special key in `schema` that maps to **entity level spec**s.  The **entity level spec**s can have all the same entries as normal field specs with one important difference: All the **coerce functions**, **validate functions**, and **present functions** take the whole entity as a parameter.  This allows them to access any or all of the fields in the entity to perform the desired operation.   

Also notice that we added a new field, `:distance`, in the **entity level spec**s.   That works.  **Entity level spec**s may also be applied to existing fields. 

## Nested Structures



## Dealing With Errors

## Good to Know
### Unspecified Fields are Lost
### Merging Schema
### Self Referencing Schema
### Integration with Bucket
### Types
### Shorthands
