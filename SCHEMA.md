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

{:type   :polygon
 :points [{:x 1 :y 1} {:x 2 :y 2} {:x 1 :y 2} {:x 1 :y 1}]}
```

We can define the shape of these data structures using `schema`.

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

## Coercion

Assume we were given the data below, and told the data represents a `point`.

``` clojure
(def data {:kind :point
           :x    "1"
           :y    "2"})
```

Not that the `x` and `y` values are strings.  That's not right.  No worries.  Using our `point` schema, we can `coerce` 
the data in the shape we want like so.

``` clojure
> (schema/coerce point data)
{:kind :point, :x 1, :y 2}         
```

Now imagine we were given the following `point` data.

``` clojure
(def data {:kind :point
           :x    ["1"]
           :y    ["2"]})
```

If we try to `coerce` this, we get the following.  Note the errors as values in the map.

``` clojure
> (schema/coerce point data)
{:kind :point, :x #c3kit.apron.schema.CoerceError{:message "can't coerce [\"1\"] to int"}, :y #c3kit.apron.schema.CoerceError{:message "can't coerce [\"2\"] to int"}}
```

We can tell our schema how to handle this.

``` clojure
> (def point {:kind {:type :keyword}
              :x    {:type :int :coerce (fn [v] (schema/->int (first v)))}
              :y    {:type :int :coerce first}})
> (schema/coerce point data)
{:kind :point, :x 1, :y 2}
```

For `x` we add a `:coerce` function to the `spec`, and we are very explicit about how to handle the data.  But we take 
some shortcuts for `y`.  The value for `:coerce` can be a function or a list of functions that take the value as
a parameter, and return the coerced value.  So by using the function `first` for `y` we'll end up with the string "2".
The type coerce is the final step in the `coerce` operation and `schema` knows how to convert a string to an int.

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
> (schema/validate point data1)
{:kind :point, :x 1, :y 2}
> (schema/validate point data2)
{:kind :point, :x #c3kit.apron.schema.ValidateError{:message "is invalid"}, :y #c3kit.apron.schema.ValidateError{:message "is invalid"}}
```

`data1` looks good but `data2` has problems.  `x` and `y` are strings, not ints! The message "is invalid" is not
terribly useful.  Let's 

## Conform

## Present

## Dealing With Errors

## Entity Level Specs

## Self Referencing Schema
