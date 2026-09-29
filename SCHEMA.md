# Schema

### c3kit.apron.schema

## Contents

- [Overview](#overview)
- [Example](#example)
- [Coercion](#coercion)
  - [`:default`](#default)
- [Validation](#validation)
  - [`:required`](#required)
- [Reusable Refs](#reusable-refs)
  - [The standard catalog](#the-standard-catalog)
  - [Factory lexes](#factory-lexes)
  - [Combinator factories](#combinator-factories)
  - [Map entries with overrides](#map-entries-with-overrides)
  - [Defining your own lexes](#defining-your-own-lexes)
  - [Entity-scoped lexes](#entity-scoped-lexes)
  - [Verifying lexes early](#verifying-lexes-early)
  - [Scoping the lexicon](#scoping-the-lexicon)
- [Conform](#conform)
- [Present](#present)
- [Entity Level Specs](#entity-level-specs)
- [Nested Structures](#nested-structures)
  - [Maps](#maps)
  - [Seq](#seq)
  - [One Of](#one-of)
  - [Dynamic Keys](#dynamic-keys)
- [Dealing With Errors](#dealing-with-errors)
- [Path Traversal](#path-traversal)
  - [Path grammar](#path-grammar)
  - [Semantics: data vs schema](#semantics-data-vs-schema)
  - [Known vs dynamic keys](#known-vs-dynamic-keys)
  - [Invalid paths](#invalid-paths)
- [Rendering Schemas](#rendering-schemas)
  - [Annotations](#annotations)
  - [OpenAPI — `c3kit.apron.schema.openapi`](#openapi--c3kitapronschemaopenapi)
  - [Shared infrastructure — `c3kit.apron.schema.doc`](#shared-infrastructure--c3kitapronschemadoc)
  - [Describing a Schema](#describing-a-schema)
- [Good to Know](#good-to-know)
  - [Bare vs Wrapped Schemas](#bare-vs-wrapped-schemas)
  - [Unspecified Fields are Lost](#unspecified-fields-are-lost)
  - [Merging Schema](#merging-schema)
  - [Self Referencing Schema](#self-referencing-schema)
  - [Shorthands](#shorthands)
  - [Types](#types)
  - [Integration with Bucket](#integration-with-bucket)

## Overview

The `schema` library aims to solve the following problems:

* What is the expected shape of the data?
* How can I `coerce` data into the desired shape?
* How can I `validate` data?
* How can I `present` data?

## Example

Consider this map that represents a point.

``` clojure
{:kind :point
 :x    1
 :y    2}
```

The data structure includes a `:kind` to tell us what the data represents.  Isn't that nice?  And it contains the necessary data, `:x` and `:y`, to represent a point.

We can define the shape of this data structure, its schema, like so:  

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int}
            :y    {:type :int}})
```

Yes, it's just data; a map where each key is mapped to a `spec` (specification).  This is the `schema` schema; the shape of data used to define the shape of other data.  *(The terminology in this document is circular and self-referencing. Bare with me.)*

Let's start using this schema.

## Coercion

Assume we were given the data below, with the assertion that the data represents a `point`.

``` clojure
(def data {:kind :point
           :x    "1"
           :y    "2"})
```

Notice that the `:x` and `:y` values are strings.  That's not right.  They're supposed to be integers. No worries. Using our `point` schema, we can `coerce` the data in the shape we want.

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

Notice how the `:x` and `:y` keys map to `CoerceError` objects containing friendly messages.  We'll use those later.

With some enhancements to our `point` schema, we can handle this type of `point` data.

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :coerce (fn [v] (schema/->int (first v)))}
            :y    {:type :int :coerce first}})
            
(schema/coerce point data)
=> {:kind :point, :x 1, :y 2}
```

For `:x` we add `:coerce` to the `spec` mapping to a **coerce function**, and we are very explicit about how to handle the data.  But for `:y` we take a shortcut.  The value for `:coerce` must be a **coerce function** or a list of **coerce function**s.  Each **coerce function** must take the value as a parameter, and return the coerced value.  So by using the function `first` for `:y` we end up with the string `"2"`... yet our result has the integer `2`. The final step of the `coerce` operation is **type-coercion**, using the `:type` of the spec.  `schema` knows how to convert a string to an int, so it takes care of that for you.

### `:default`

Sometimes a missing field should be filled in rather than left out.  The `:default` spec key supplies a value to use whenever the field's map key is **absent**, *before* any of the spec's own coercions run — so the default gets normalized right alongside real input.

```clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :default 0}
            :y    {:type :int :default "0"}})

(schema/coerce point {:kind :point})
=> {:kind :point, :x 0, :y 0}
```

Notice `:y`'s default is the string `"0"` — it still goes through **type-coercion** afterward and comes out as the integer `0`.

**Missing is not the same as `nil`.**  `:default` only fills a key that is *absent* from the map.  A key that is explicitly present with value `nil` is **not** defaulted — it's processed as `nil` and, like any field that comes out `nil`, dropped from the result, same as it always has been:

```clojure
(schema/coerce point {:kind :point :x nil})
=> {:kind :point, :y 0}
```

`:x` was given as `nil`, so it isn't defaulted — it just drops, the way any `nil`-valued field does; `:y` was never mentioned, so it got its default.  A value that is present and non-`nil`, even `false`, is of course never replaced either.

**Only coercion applies defaults** — `coerce`, and `conform` through its coerce step.  Plain `validate` never does (see [`:required`](#required) below); it reads exactly the map it was given.  There is also no "missing" for a bare value (`coerce-value!`) or for an entry inside a `:seq` — both are always some concrete value, present or `nil`, never absent — so `:default` on a `:seq`'s entry `:spec` is never applied during processing, only on the seq *field* itself when that field's key is absent.

`:default` works on a top-level field, a nested `:map` field (recursively — a present nested map defaults its own absent keys the same way), and shorthands.  In `{:type [:int] :default []}` the default describes the *seq field* (an empty list of ints if the field is absent), not its entries — see [Shorthands](#shorthands).  The `[:default v]` coercion factory (a `:coercions` entry, see [Factory lexes](#factory-lexes)) is unrelated and still works exactly as before; `:default` is the spec-level annotation, `[:default v]` is one coercion step among others.

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

Again you can see the problematic fields are mapped to errors, `ValidationError` objects this time.  However, the error message `"is invalid"` is not terribly useful.  Let's improve that.

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

We added a `:message` string to the spec. Now the `:message` value is being used for `coerce` and `validate` error messages.

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

This example demonstrates that there are two validations being performed on both `:x` and `:y`; the `even?`/`odd?` validations specified by the `:validate` entry in the spec, and the automatic **type-validation**.  The **type-validation** is the first step in the `validate` operation, whereas **type-coercion** is that last step of the `coerce` operation.  This demonstrated in the example below.  

Also, let's make sure we get a descriptive error messages for each validation?

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

### `:required`

`:required true` is shorthand for the common case of requiring a value to be present.  It behaves exactly like `:validations [:required]` — the value must satisfy `present?` (non-`nil`, non-blank-string), with the message `"is required"`.

```clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :required true}
            :y    {:type :int}})

(schema/validate-message-map point {:kind :point :y 2})
=> {:x "is required"}
```

`:required` works with nested `:map` fields, `:seq` entries, and shorthands the same way `:description`, `:default`, and `:validations` do — it describes whichever spec it's attached to.  On the `[:type]` seq shorthand in particular, that means `:required` and `:validations` describe the seq field itself, not its entries — see [Shorthands](#shorthands).

For doc/OpenAPI purposes (`doc/required?`, `doc/required-fields`, and `describe` below), a field also counts as required when it says so the "manual" way — `:validate schema/present?`, `:validations [{:validate schema/present?}]` — or via the keyword refs `:validations [:present?]` / `:validations [:required]`, which is how a data-only (EDN) schema with no functions in it expresses the same rule. `doc/required?` resolves those refs through the schema lexicon rather than only recognizing the raw `present?` fn.

**A `:default` satisfies `:required` — in `conform` only.**  `conform` coerces before it validates, and coercion is what fills an absent key with its `:default` (see [`:default`](#default) above); by the time `:required` is checked, the key is no longer absent:

```clojure
(def point {:kind {:type :keyword}
            :x    {:type :int :required true :default 0}})

(schema/conform-message-map point {:kind :point})
=> nil
```

**Plain `validate` is strict.**  It doesn't coerce, so it never fills an absent key and never substitutes a `:default` for anything — it just checks exactly the value it was given.  (Like `coerce` and `conform`, a field that comes out `nil` — because it was absent, or explicitly `nil`, or failed some other way — still drops from the result; that's unrelated to `:default` and unchanged from before this annotation existed.)  A required field that's absent, or explicitly `nil`, is an error either way, `:default` or no `:default`:

```clojure
(schema/validate-message-map point {:kind :point})
=> {:x "is required"}

(schema/validate-message-map point {:kind :point :x nil})
=> {:x "is required"}
```

If you want `validate` to see a filled-in value, `conform` (or `coerce` first, then `validate` the result) — plain `validate` won't do it for you.

## Reusable Refs

Up to this point every `:validate`, `:coerce`, `:validations`, `:coercions`, and `:presentations` entry has been a Clojure value written inline. That works for application code but breaks down when you need schemas as **data** — loaded from EDN or JSON, shipped between systems, or carried by plugins that the host doesn't load code from.

`schema` solves this with the **lexicon**: `c3kit.apron.schema/*lexicon*`, a dynamic var holding four slots — `:types`, `:validations`, `:coercions`, `:presentations` — each a `{name → lex}` map. A **lex** is a small map (`{:validate ...}`, `{:coerce ...}`, or `{:present ...}`, each with an optional `:message`), or a **factory** function that returns one. `:validations`, `:coercions`, and `:presentations` entries reference a lex by name; the original `:validate`, `:coerce`, and `:present` keys stay function-only — lex names only resolve inside those three plural slots.

```clojure
(require '[c3kit.apron.schema :as s])

(s/validate-value! {:type :int :validations [:pos?]} -1)
;; => throws ExceptionInfo "must be positive"
```

Unlike the pre-3.0 ref registry, the lexicon isn't empty by default — requiring `c3kit.apron.schema` also loads the standard catalog (below), so `:pos?` above resolves with no install step.

A lex is a map containing some subset of `:validate`, `:coerce`, `:present`, and `:message`. Which slot an entry appears in determines which key gets pulled from it:

| Slot | Pulls from the lex |
| --- | --- |
| `:validations [:foo?]` | `:validate` |
| `:coercions [:trim]` | `:coerce` |
| `:presentations [:trim]` | `:present` |

If a lex doesn't carry the key its slot needs, you get a clear error — e.g. `lex :coerce-only has no :validate`. And if a name isn't registered in that slot at all, the failure doesn't blow up the whole `coerce` / `validate` / `conform` call — it surfaces as an ordinary field-level error with the message `"missing lex :X in :slot"`, alongside whatever other fields did resolve.

### The standard catalog

Requiring `c3kit.apron.schema` transitively requires four bundle namespaces — `c3kit.apron.schema.types`, `.validations`, `.coercions`, `.presentations` — and merges their catalogs into `*lexicon*` at load time. There's no separate install step:

```clojure
(s/validate-value! {:type :string :validations [:string?]} 42)
;; => throws "must be a string"

(s/coerce-value! {:type :string :coercions [:trim]} "  hi  ")
;; => "hi"
```

Categories shipped (see each namespace's source for the exhaustive list):

- **Type predicates** — `:string?`, `:integer?`, `:keyword?`, `:number?`, `:boolean?`, `:map?`, `:uuid?`, `:ifn?`, `:float?`
- **Numeric predicates** — `:pos?`, `:neg?`, `:zero?`, `:pos-int?`, `:neg-int?`, `:nat-int?`
- **Apron predicates** — `:present?` (alias `:required`), `:email?`, `:bigdec?`, `:uri?`, `:multiple?`, `:date?`, `:instant?`, `:timestamp?`
- **Comparison factories** — `:>`, `:<`, `:>=`, `:<=`, `:=`, `:not=`, `:between`
- **Shape factories** — `:min-length`, `:max-length`, `:length`, `:matches`, `:one-of`, `:not-one-of`
- **Combinator factories** — `:nil-or?`, `:maybe?`, `:not?`, `:and?`, `:or?` (compose other lexes or fns)
- **String coercers** — `:trim`, `:upper-case`, `:lower-case`, `:capitalize`
- **Type coercers** — `:->string`, `:->int`, `:->float`, `:->bigdec`, `:->boolean`, `:->keyword`, `:->date`, `:->sql-date`, `:->timestamp`, `:->uri`, `:->uuid`, `:->map`
- **Coercion factory** — `:default` (substitutes a value whenever the *value* is `nil`; not to be confused with the `:default` **spec key** from [above](#default), which fills in the *key* when it's absent)
- **String/type presentations** — `:trim`, `:upper-case`, `:lower-case`, `:capitalize`, `:->string`, `:omit` (drops the field from output), and a `:default` factory (substitutes a display value when the field is `nil`)

Each lex is also exported as its own var for à-la-carte use without going through the lexicon:

```clojure
(require '[c3kit.apron.schema.validations :as v]
         '[c3kit.apron.schema.coercions :as c])

{:type :string
 :validations [v/string? (v/min-length 3)]
 :coercions   [c/trim]}
```

### Factory lexes

Some lexes take parameters. They register as a **function** that returns a validation/coercion map; you invoke them through the lexicon as a vector:

```clojure
(s/validate-value! {:type :int :validations [[:> 5]]}        3)   ;; throws "must be > 5"
(s/validate-value! {:type :string :validations [[:max-length 3]]} "abcd")
(s/coerce-value!   {:type :any :coercions   [[:default 99]]} nil) ;; => 99
```

The first element of the vector is the lex name; the rest are the factory's arguments.

### Combinator factories

The standard catalog includes five combinators — `:nil-or?`, `:maybe?`, `:not?`, `:and?`, `:or?` — that take other predicates as arguments. The argument may be a lex name, a factory invocation, or an inline function:

```clojure
;; nil-tolerant version of any predicate
(s/validate-value! {:type :any :validations [[:nil-or? :pos?]]} nil)         ;; => nil
(s/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} 5) ;; => 5

;; like nil-or?, but keeps the inner predicate's own message (no "may be nil or " prefix)
(s/validate-value! {:type :any :validations [[:maybe? :pos?]]} nil) ;; => nil
(s/validate-value! {:type :any :validations [[:maybe? :pos?]]} -1)
;; => throws "must be positive"

;; compose multiple constraints
(s/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} 4)
(s/validate-value! {:type :any :validations [[:or?  :pos? :zero?]]} 0)

;; invert a predicate
(s/validate-value! {:type :any :validations [[:not? :pos?]]} -1)
```

Combinator factories use `s/->validate-fn` to resolve their arguments. Custom combinators do the same:

```clojure
(s/update-lexicon! :validations assoc :every-of?
  (fn [pred-ref]
    (let [pred (s/->validate-fn pred-ref)]
      {:validate (fn [coll] (every? pred coll))
       :message  "every element must satisfy predicate"})))

(s/validate-value! {:type :any :validations [[:every-of? :pos?]]} [1 2 3])
```

The companion `s/->coerce-fn` handles the same job for combinator coercers.

### Map entries with overrides

A `:validations` or `:coercions` entry can also be a full map, which lets you reuse a lex's predicate but override the message at the call site:

```clojure
{:type :string
 :validations [{:validate :present? :message "Name is mandatory"}]}
```

When the message is omitted, the lex's `:message` is used; the spec-level `:message` fills in if neither is present.

### Defining your own lexes

Application or plugin code extends the lexicon with `update-lexicon!`, which mutates the root binding — so the addition is visible everywhere, the same way requiring a library is:

```clojure
(s/update-lexicon! :validations assoc :app/non-empty-vec
  {:validate (every-pred vector? seq)
   :message  "must be a non-empty vector"})
```

Factories are plain functions that return a validation/coercion map:

```clojure
(s/update-lexicon! :coercions assoc :app/clamp
  (fn [lo hi]
    {:coerce  #(max lo (min hi %))
     :message (str "clamped to [" lo ", " hi "]")}))

(s/coerce-value! {:type :int :coercions [[:app/clamp 0 10]]} 99) ;; => 10
```

`update-lexicon!` takes a slot, an update fn, and args — the same shape as `clojure.core/update` — so re-registering an existing name is just `assoc` again; it silently replaces the old lex, with no warning.

For scoped overrides — a test, a single request, a plugin sandbox — bind rather than mutate; see [Scoping the lexicon](#scoping-the-lexicon) below.

### Entity-scoped lexes

Sometimes a field's validity depends on other fields in the entity — e.g. `:tail-length` is only required when `:species` is `"dog"`. `:*` (see [Entity Level Specs](#entity-level-specs)) handles this, but it pulls the rule away from the field it belongs to.

A lex can opt into **entity scope** by setting `:scope :entity`. Its `:validate`/`:coerce` fn then receives `(entity field-key)` instead of `(value)`, and runs after the field-level pass:

```clojure
(s/update-lexicon! :validations assoc :required-when
  (fn [other-key expected]
    {:validate (fn [entity field-key]
                 (or (not= expected (get entity other-key))
                     (s/present? (get entity field-key))))
     :scope    :entity
     :message  (str "is required when " other-key " is " expected)}))

(def pet
  {:species     {:type :string}
   :tail-length {:type :int :validations [[:required-when :species "dog"]]}})

(s/validate-message-map pet {:species "dog"})
;; => {:tail-length "is required when :species is dog"}

(s/validate-message-map pet {:species "cat"})
;; => nil
```

The same `:scope :entity` flag works inside `:coercions`, letting a lex derive a value from sibling fields:

```clojure
(s/update-lexicon! :coercions assoc :full-name-from-parts
  {:coerce (fn [entity _field-key]
             (str (:first-name entity) " " (:last-name entity)))
   :scope  :entity})

(s/coerce {:first-name {:type :string}
           :last-name  {:type :string}
           :full-name  {:type :string :coercions [:full-name-from-parts]}}
          {:first-name "Ada" :last-name "Lovelace"})
;; => {:first-name "Ada", :last-name "Lovelace", :full-name "Ada Lovelace"}
```

Pipeline order for an entity:

1. **Per-field, value-scoped**: coerce → validate
2. **Per-field, entity-scoped**: coerce → validate (runs only after step 1 finishes for every field)
3. **`:*` entity-level**: coerce → validate

`validate-value!`, `coerce-value!`, and `conform-value!` operate on a single value with no entity context, so entity-scoped entries are silently bypassed in those calls — only the full-entity APIs (`validate`/`coerce`/`conform`) run them.

### Verifying lexes early

`verify-schema-lexes` walks a schema and throws on the first `:validations`, `:coercions`, or `:presentations` entry whose lex is unregistered or missing the key its slot needs. Plugin hosts call it once after extending the lexicon, to fail fast before any data flows:

```clojure
(s/verify-schema-lexes my-plugin-schema)   ;; => true, or throws
```

### Scoping the lexicon

`*lexicon*` is an ordinary dynamic var, so tests, plugin sandboxes, and request-scoped overrides isolate their additions with `with-lexicon` instead of touching the root. It merges a partial lexicon map over the current binding for the duration of a body:

```clojure
(def my-schema {:n {:type :int :validations [:app/pos2?]}})

(s/with-lexicon {:validations {:app/pos2? {:validate pos? :message "must be positive"}}}
  (s/validate-message-map my-schema {:n -1}))
;; => {:n "must be positive"}

;; outside the binding, the lex is gone again — surfaces as a field error, not a crash
(s/validate-message-map my-schema {:n -1})
;; => {:n "missing lex :app/pos2? in :validations"}
```

`with-lexicon` never touches the root, so there's nothing to reset afterward — this is what replaces binding a fresh registry atom in the pre-3.0 API.

For per-call overrides instead of a whole body, use the `-with` variants — `coerce-with`, `validate-with`, `conform-with`, `present-with` — each takes a partial lexicon map, scopes it, and runs the corresponding `!` fn (so, like `validate!`, `validate-with` throws on invalid data rather than returning a message map):

```clojure
(s/validate-with {:validations {:app/pos2? {:validate pos? :message "must be positive"}}}
                  my-schema {:n 5})
;; => {:n 5}
```

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

That first `point` is perfect and the result of `conform` is a map shaped just how we like it.  The second point has a couple problems and the result of `conform` identifies them nicely. `:x` cannot be coerced and `:y` is invalid.

## Present

`schema` provides a `present` operation to make our data presentable to a user, or an API, or whatever.   

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

We have a new requirement for our geometry data.  A `point` may not be close to the origin (0, 0).  If a `point` has a distance from the origin of less than 5, it is invalid.  So far, we've only been able to validate a single field.  We need both `:x` and `:y` to calculate distance, and **entity level spec** give that ability.

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

### Maps

What would a `line` data structure look like?  A `line` consists of two `points`; a start and an end.

```clojure
(def point {:kind {:type :keyword}
            :x    {:type :int}
            :y    {:type :int}})

(def line {:kind  {:type :keyword}
           :start {:type :map :schema point}
           :end   {:type :map :schema point}})

(schema/conform line {:kind :line
                      :start {:kind :point :x "1" :y "2"}
                      :end {:kind :point :x 3.45 :y 6.78}})
=> {:kind :line, :start {:kind :point, :x 1, :y 2}, :end {:kind :point, :x 3, :y 6}}
```

Take a close look at the `:start` and `:end` of `line`.  The `:type` is set to `:map`.  `schema` will happily dive into nested data structures, however it needs to know what to expect.  So included in the spec is the `:schema`.  For a `line`, we want both `:start` and `:end` to be `points`.

### Seq

Now consider a `polygon` that may consist of many `points`.

``` clojure
(def polygon {:kind   {:type :keyword}
              :points {:type        :seq
                       :spec        {:type :map :schema point}
                       :validations [{:validate #(>= (count %) 4) :message "must have at least 4 points"}
                                     {:validate #(= (first %) (last %)) :message "not closed"}]}})

(schema/conform polygon {:kind :polygon})
=> {:kind :polygon, :points #c3kit.apron.schema.ValidateError{:message "must have at least 4 points"}}

(schema/conform polygon {:kind  :polygon
                         :points [{:kind :point :x "1" :y "2"}
                                  {:kind :point :x 3.45 :y 6.78}
                                  {:kind :point :x 6 :y 4}
                                  {:kind :point :x 99 :y 99}]})
=> {:kind :polygon, :points #c3kit.apron.schema.ValidateError{:message "not closed"}}

(schema/conform polygon {:kind  :polygon
                         :points [{:kind :point :x "1" :y "2"}
                                  {:kind :point :x 3.45 :y 6.78}
                                  {:kind :point :x 6 :y 4}
                                  {:kind :point :x 1 :y 2}]})
=>
{:kind :polygon,
 :points [{:kind :point, :x 1, :y 2} {:kind :point, :x 3, :y 6} {:kind :point, :x 6, :y 4} {:kind :point, :x 1, :y 2}]}
```

`polygon` has a `:points` field that is rather sophisticated.  We can see that the `:type` is `:seq`.  This tells `schema` that the `:points` value should be a sequential value (list, vector, set, ...), but `schema` needs to know what each value in the sequence should look like.  Therefor we also include the `:spec` in the spec.  In the case of the `polygon`, each value in `:points` should be a `point`.   

`schema` will expect seqs to hold a single type of value.  Said in another way, all the values in a seq must conform to the same spec.  If you need to process data where lists store values of different types, you can either use `:type :map` and specify a `:schema` where the keys are indexes of the list, or use `:type :ignore` and manually handle the operations. 

### One Of

We've got all these cool geometry data structures.  Wouldn't it be nice if we could have a `geometry` data structure that could hold on to one of the other geometry data structures?  Let's update our schemas to handle this.

``` clojure
(def point {:kind (schema/kind :point)
            :x    {:type :int}
            :y    {:type :int}})

(def line {:kind  (schema/kind :line)
           :start {:type :map :schema point}
           :end   {:type :map :schema point}})

(def circle {:kind   (schema/kind :circle)
             :center {:type :map :schema point}
             :radius {:type :int}})

(def geometry {:kind     (schema/kind :geometry)
               :geometry {:type :one-of :specs [{:type :map :schema point}
                                                {:type :map :schema line}
                                                {:type :map :schema circle}]}})
```

First, we set the `:kind` of each schema using `schema/kind`.  This helper function returns a `spec` that conforms and validates the value such that it must be the specified keyword.  Here's the implementation:

```clojure
(defn kind [key]
  {:type     :keyword
   :value    key
   :validate [#(or (nil? %) (= key %))]
   :coerce   [#(or % key)]
   :message  (str "mismatch; must be " key)})
```

Second, we added a `circle` schema.

And lastly, we have `geometry`. The `:type` of it's `:geometry` field is `:one-of`.  This allows the value to take on any "one of" the geometries specified by the `:specs` list.  `schema` will run the operation (`conform` in the case below) on the value against each `spec` in the `:specs` list until one succeeds.  If none of the operations succeed, the result will be an error.

```clojure
(schema/conform geometry {:kind :geometry :geometry {:kind :point :x "1" :y "2"}})
=> {:kind :geometry, :geometry {:kind :point, :x 1, :y 2}}

(schema/conform geometry {:kind :geometry :geometry {:kind  :line
                                                     :start {:kind :point :x "1" :y "2"}
                                                     :end   {:kind :point :x 3.45 :y 6.78}}})
=> {:kind :geometry, :geometry {:kind :line, :start {:kind :point, :x 1, :y 2}, :end {:kind :point, :x 3, :y 6}}}

(schema/conform geometry {:kind :geometry :geometry {:kind   :circle
                                                     :center {:kind :point :x "1" :y "2"}
                                                     :radius 42}})
=> {:kind :geometry, :geometry {:kind :circle, :center {:kind :point, :x 1, :y 2}, :radius 42}}

(schema/conform geometry {:kind :geometry :geometry {:kind :squiggle}})
=> {:kind :geometry, :geometry #c3kit.apron.schema.ConformError{:message "one-of: no matching spec"}}
```

### Dynamic Keys

Sometimes a map has keys that aren't known ahead of time.  Imagine a `ship` with a `:crew` map whose keys are crew-member ids and whose values are crew-member records.

```clojure
{:kind :ship
 :crew {:joe  {:name "Joe"}
        :bill {:name "Bill"}}}
```

We know `:crew` and `:name` in advance, but `:joe` and `:bill` are data.  `:schema` alone can't describe this.  The `:map` type accepts two extra spec keys — `:key-spec` and `:value-spec` — that apply to every entry in the map.

```clojure
(def crew-member {:name {:type :string}})

(def ship {:kind (schema/kind :ship)
           :crew {:type       :map
                  :key-spec   {:type :keyword}
                  :value-spec {:type :map :schema crew-member}}})

(schema/conform ship {:kind :ship :crew {:joe {:name "Joe"} :bill {:name "Bill"}}})
=> {:kind :ship, :crew {:joe {:name "Joe"}, :bill {:name "Bill"}}}
```

`:key-spec` and `:value-spec` can be combined with a regular `:schema`, in which case keys listed in `:schema` win and everything else flows through the dynamic specs.

```clojure
{:type       :map
 :schema     {:captain {:type :map :schema crew-member}}    ; always required
 :key-spec   {:type :keyword}
 :value-spec {:type :map :schema crew-member}}              ; all other entries
```

Error paths use the actual runtime key.  Value errors appear under the coerced key; errors coercing or validating the key itself appear under the original key.

```clojure
(schema/conform-message-map ship
  {:kind :ship :crew {:joe {:name 42} "bill" {:name "Bill"}}})
=> {:crew {:joe  {:name "is invalid"}
           "bill" "must be a keyword"}}
```

When neither `:key-spec` nor `:value-spec` is present, unknown keys continue to be dropped.

## Dealing With Errors

We've seen that when errors occur during `schema` operations, they appear as the value the offending field in the result. `schema/error?` makes it easy to tell if a result has an error.

``` clojure
(schema/error? (schema/validate point {:kind :point :x 1 :y 2}))
=> false
(schema/error? (schema/validate point {:kind :point :x "blah" :y 2}))
=> true
```

Sometimes you want a list of all the errors. 

``` clojure
(schema/message-seq (schema/validate point {:kind :point :x 1 :y 2}))
=> nil

(schema/message-seq (schema/validate point {:kind :point :x "blah" :y 2}))
=> ["x is invalid"]

(schema/message-seq (schema/conform line {:kind  :line
                                          :start {:kind :point :x "blah" :y "2"}
                                          :end   {:kind :point :x 3.45 :y "blah"}}))
=> ["start.x can't coerce \"blah\" to int" "end.y can't coerce \"blah\" to int"]
```

Notice how the keys from nested errors are joined with a `.`, as in `start.x`.  Seq indices and non-keyword keys use bracket notation instead — `points[0].x` for a seq entry, `crew["bill"]` for a string key.

More often you want a map of the errors.

``` clojure
(schema/message-map (schema/validate point {:kind :point :x 1 :y 2}))
=> nil

(schema/message-map (schema/validate point {:kind :point :x "blah" :y 2}))
=> {:x "is invalid"}

(schema/message-map (schema/conform line {:kind  :line
                                          :start {:kind :point :x "blah" :y "2"}
                                          :end   {:kind :point :x 3.45 :y "blah"}}))
=> {:start {:x "can't coerce \"blah\" to int"}, :end {:y "can't coerce \"blah\" to int"}}
```

Notice that errors from nested data maintain the nested structure.  

`schema/message-map` is so frequently used that shortcuts exist for all the operations.

``` clojure
(schema/coerce-message-map point {:kind :point :x "blah" :y 2})
=> {:x "can't coerce \"blah\" to int"}

(schema/validate-message-map point {:kind :point :x "blah" :y 2})
=> {:x "is invalid"}

(schema/conform-message-map point {:kind :point :x "blah" :y 2})
=> {:x "can't coerce \"blah\" to int"}
```

## Path Traversal

`c3kit.apron.schema.path` provides coordinate-based traversal of both schemas and data using the same path grammar produced by `message-seq`. Two public functions:

- `schema-at` — walk a schema tree
- `data-at`   — walk a concrete value

This closes the loop for error-driven navigation: validate → get a path from `message-seq` → `data-at` to the offending value in the input, or `schema-at` to the spec that described it.

### Path grammar

| Form | Example | Meaning |
|---|---|---|
| Dot keyword | `user.name` | descend via keyword key |
| `[N]` | `points[0]` | seq index |
| `["str"]` | `crew["bill"]` | string key |
| `[:kw]` | `crew[:joe]` | explicit keyword literal |
| `.value` or `[:value]` | `crew.value` | template for dynamic entries (schema only) |
| `.key` or `[:key]` | `crew.key` | template for dynamic keys (schema only) |

### Schema traversal semantics

`schema-at` resolves each path segment against the schema tree. Two reserved segment names — `:value` and `:key` — access the dynamic-entry templates on a `:map` (`:value-spec`, `:key-spec`) or the entry template on a `:seq` (`:spec`).

```clojure
(require '[c3kit.apron.schema.path :as path])

(def ship {:kind {:type :keyword}
           :crew {:type :map
                  :key-spec   {:type :keyword}
                  :value-spec {:type :map :schema {:name {:type :string}}}}})

(path/schema-at ship "crew.value")
;; => {:type :map :schema {:name {:type :string}}}

(path/schema-at ship "crew.value.name")
;; => {:type :string}

(path/schema-at ship "crew.key")
;; => {:type :keyword}
```

`:value` and `:key` are context-dependent: when the spec has `:value-spec` / `:key-spec`, they resolve to those templates. When the spec doesn't, they fall back to ordinary field lookup in `:schema`. A schema whose literal fields are named `:value` or `:key` is reachable normally as long as the spec isn't also dynamic-keyed.

### Known vs dynamic keys

When the path names a keyword that is a known field in `:schema`, `schema-at` descends into that field's spec. When the keyword is not in `:schema`, `schema-at` returns `nil`. Use `.value` or `.key` when you mean the dynamic-entry template.

### Data traversal

`data-at` walks concrete values using the same path grammar:

```clojure
(def data {:kind :ship :crew {:joe  {:name "Joe"}
                              :bill {:name "Bill"}}})

(path/data-at data "crew.joe.name")
;; => "Joe"

(path/data-at data "crew[\"joe\"]" {:lenient? true})
;; => works even if data had {"joe" ...} instead of {:joe ...}
```

`:lenient?` makes `data-at` treat keyword and string key forms as equivalent. Default is strict.

### Invalid paths

- `schema-at` returns `nil` for missing fields.
- `data-at` returns `nil` for missing keys.

## Rendering Schemas

### Annotations

Optional spec fields drive human-facing output:

| Field | Type | Used by |
|---|---|---|
| `:description` | string | OpenAPI `description` |
| `:example` | any | OpenAPI `example` |
| `:default` | any | OpenAPI `default`; also fills an absent map key during `coerce`/`conform` (see [`:default`](#default) and [`:required`](#required)) |
| `:required` | boolean | OpenAPI `required` (via `doc/required?`); also drives field-level validation (see [`:required`](#required)) |
| `:name` | keyword | Marks a spec as reusable; the OpenAPI renderer emits it once and references it elsewhere via `$ref` |

Example spec with annotations:

```clojure
(def pet {:type :map :name :pet
          :description "A pet in the store."
          :schema {:name    {:type :string :description "Pet's name." :example "Rex"}
                   :species {:type :string :description "Species."}}})
```

### OpenAPI — `c3kit.apron.schema.openapi`

- `apron->openapi-schema`       — single spec → OpenAPI schema object (inlines everything).
- `apron->openapi-schema+refs`  — single spec → `{:schema <schema-or-$ref> :refs {<name> <schema>}}`. Named sub-specs are pulled into `:refs` and referenced via `$ref` at use sites.
- `->doc`                       — full OpenAPI 3.0 document from a route/doc spec; collects named schemas into `components.schemas`.

```clojure
(require '[c3kit.apron.schema.openapi :as openapi])

(openapi/apron->openapi-schema pet)
;; => {:type "object", :description "A pet in the store.",
;;     :properties {:name    {:type "string", :description "...", :example "Rex"}
;;                  :species {:type "string", :description "..."}}}

(openapi/->doc {:title "Pet API" :version "1.0.0" :routes [...]})
;; => {:openapi "3.0.0", :info {...}, :paths {...},
;;     :components {:schemas {"pet" {...}}}}
```

Output aligns with JSON Schema Draft 2020-12 (which OpenAPI 3.1 uses) for the schema portion; `->doc` wraps it in an OpenAPI 3.0 envelope. Named specs use OpenAPI's standard `"$ref": "#/components/schemas/<name>"` form.

### Shared infrastructure — `c3kit.apron.schema.doc`

Small namespace holding the route/doc input schemas (`route-schema`, `doc-schema`) and format-agnostic helpers (`required?`, `required-fields`, `integer-keys?`, `schema-map?`, `maybe-invalid-doc`, `describe`). Used internally by the OpenAPI renderer; the only reason to require it directly is if you're writing a new renderer, or calling `describe` (below) yourself.

### Describing a Schema

`c3kit.apron.schema.doc/describe` walks a schema (or a single spec) and returns a flat seq of maps, one per field, for humans or tooling that want a plain-data summary instead of an OpenAPI document:

```clojure
(require '[c3kit.apron.schema.doc :as doc])

(doc/describe {:name {:type :string :required true :description "Pet's name."}
              :age  {:type :int :default 0}})
=> [{:path "name" :type :string :required true :description "Pet's name."}
    {:path "age"  :type :int :required false :default 0}]
```

Each entry has `:path` (in the [path grammar](#path-grammar)), `:type`, `:required`, and `:default`/`:description` when present. `:map` fields and `:seq` entries are walked recursively — a `:seq`'s entry spec is reported at a trailing `.value` segment, matching how [`schema-at`](#schema-traversal-semantics) addresses it (there's no concrete index to report against a schema, only against data); a `:map`'s dynamic `:key-spec`/`:value-spec` are reported at `.key`/`.value`.

`:one-of` is reported as a single entry at its own path — the path grammar has no segment for "which alternative", so its `:specs` aren't expanded individually. `describe` accepts a bare schema or a wrapped `{:type :map :schema {...}}` spec interchangeably, reads only spec-level data (no function in the schema is ever called), and so works on schemas loaded straight from EDN.

## Good to Know

We've covered all the major facets of the `schema` library.  There's just a few things you should know before we part ways.    

### Bare vs Wrapped Schemas

So far we've defined schemas as bare field maps — `{:name {...} :age {...}}`. That's convenient for flat structures. When you nest, you reach for the wrapped form — `{:type :map :schema {...}}` — so the nested field can carry its own `:type :map`, `:validations`, etc.

Both forms are accepted anywhere a schema can go: `coerce`, `validate`, `conform`, `present`, and `schema.path/schema-at` all take either form.

```clojure
(def bare    {:name {:type :string} :age {:type :int}})
(def wrapped {:type :map :name :user :schema bare
              :description "A user record."})

(schema/coerce bare    {:name "Joe" :age "30"})
;; => {:name "Joe" :age 30}

(schema/coerce wrapped {:name "Joe" :age "30"})
;; => {:name "Joe" :age 30}   ; identical
```

Use the wrapped form when you need the outer spec itself to carry `:description`, `:name` (for doc renderers), `:validations`, `:coerce`, or a `:value-spec` for dynamic keys. Use bare when a flat field map is enough.

The return value is always the transformed entity, never wrapped — regardless of which form you passed in.

### Unspecified Fields are Lost

Any extra data you have in your maps, that is not specified in the schema, will be tossed out by the `schema` operations.

``` clojure
(schema/coerce point {:kind :point :x 1 :y 2 :my-extra-data "goes bye bye"})
=> {:kind :point, :x 1, :y 2}
```

### Merging Schema

There are myriad ways to validating or coerce data.  For example, validating form data on the client demands different validations than on the backend where we may need to check for uniqueness in the database. It'd be a shame to duplicate the schemas.  `schema/merge-schemas` allows you to easily patch or modify existing schemas.

``` clojure
(def point {:kind {:type :keyword}
            :x    {:type :int}
            :y    {:type :int}})

(schema/merge-schemas point {:x {:validate even? :message "must be even"}
                             :y {:validate odd? :message "must be odd"}})
=>
{:kind {:type :keyword},
 :x {:type :int,
     :message "must be even",
     :validations [{:validate #object[clojure.core$even_QMARK_ 0x53a9d520 "clojure.core$even_QMARK_@53a9d520"],
                    :message "must be even"}]},
 :y {:type :int,
     :message "must be odd",
     :validations [{:validate #object[clojure.core$odd_QMARK_ 0xe551581 "clojure.core$odd_QMARK_@e551581"],
                    :message "must be odd"}]}}
```

### Self Referencing Schema

Schemas are data.  The shape of the schema data is important.  So `schema` provides a schema to conform your schemas.

``` clojure
(schema/conform-schema! point)
=> {:kind {:type :keyword}, :x {:type :int}, :y {:type :int}}
(schema/conform-schema! {:foo {:type :blah}})
Execution error (ExceptionInfo) at c3kit.apron.schema/conform! (schema.cljc:786).
Unconformable entity
```

One caveat here is that `schema` can only validate one level deep.  This is because the `schema` schema is theoretically infinitely recursive, but in reality it's impossible to make an infinitely nested data structure.

### Shorthands

There are several "shorthands" that abbreviate spec definitions, but they are technically invalid according to the `spec` schema.  `schema/normalize-spec` can be used to expand a single spec and `schema/normalize-schema` will expand all shorthands in the schema.  `schema/conform-schema!` will also normalize shorthands.

```clojure
(schema/normalize-spec {:type [:int] :validate even?})
=>
{:type :seq,
 :spec {:validate #object[clojure.core$even_QMARK_ 0x53a9d520 "clojure.core$even_QMARK_@53a9d520"], :type :int}}

(schema/normalize-spec {:type [{:type :int}] :validate seq})
=>
{:type :seq, :validate #object[clojure.core$seq__5467 0x24f5684c "clojure.core$seq__5467@24f5684c"], :spec {:type :int}}

(schema/normalize-spec {:type {:foo {:type :string}}})
=> {:type :map, :schema {:foo {:type :string}}}

(schema/normalize-spec {:type #{:string :int}})
=> {:type :one-of, :specs [{:type :int} {:type :string}]}
```

Using these shorthands we could define `line` and `polygon` like so:

``` clojure
(def line {:kind  {:type :keyword}
           :start {:type point}
           :end   {:type point}})

(def polygon {:kind   {:type :keyword}
              :points {:type [point]}})
```

This is smaller.  But, as mentioned, it is not valid schema data, and its intent is less clear.  `schema` will accept these shorthands and expand them on the fly. 

**Seq shorthand key placement.**  `{:type [:int] ...}` normalizes to `{:type :seq :spec {:type :int} ...}`, and where each key in `...` ends up depends on what it means:

- `:validate`, `:coerce`, `:present`, and `:message` move down onto the synthesized element `:spec` — they describe each *entry* in the seq.  `{:type [:int] :validate even?}` means "each int must be even," not "the seq as a whole passes `even?`."
- `:validations`, `:description`, `:default`, and `:required` stay on the outer `:seq` spec — they describe the *field* itself.  `{:type [:keyword] :validations [:required]}` means the `:permissions` list must be present, the same as `{:type :seq :spec {:type :keyword} :validations [:required]}` — not "each keyword must be present."

If you want an element-level check with `:validations` instead of a bare `:validate` fn, or a field-level check with `:validate`, use the long `:type :seq` / `:spec` form and put the key exactly where it belongs.

```clojure
(schema/normalize-spec {:type [:keyword] :validations [:required]})
=> {:type :seq, :spec {:type :keyword}, :validations [:required]}
```

### Types

Here is a list of all the possible `:type` values.

``` clojure
:any        ;; no type-coercion or type-validation 
:bigdec     ;; BigDecimal in clj, number in cljs
:boolean    
:date       ;; only use with sql database, java.sql.Date in clj, js/Date in cljs 
:double     ;; number in cljs
:float      ;; number in cljs
:fn         ;; implements iFn
:ignore     ;; same as :any
:instant    ;; java.util.Date in clj, js/Date in cljs
:int        
:keyword        
:kw-ref     ;; same as :keyword, meant to represent a Datomic ident
:long       
:one-of     ;; must also specify :specs
:map        ;; must also specify :schema
:ref        ;; same as :int, meant to store a datomic reference
:seq        ;; must also specify :spec
:string   
:timestamp  ;; only use with sql database, java.sql.Timestamp in clj, js/Date in cljs
:uri        ;; java.net.URI in clj, string in cljs
:uuid       ;; java.util.UUID in clj, cljs.core/UUID in cljs
```

### Integration with Bucket

One of the original uses of `schema` was to move data in and out of databases.  You can see this legacy in several of the `:types` like `:date`, `:timestamp`, `:kw-ref`, and `:ref`.  

[c3kit.bucket](https://github.com/cleancoders/c3kit-bucket) is a library that provides a simple abstract interface that works with a variety of databases including Datomic, Postgresql, MSSQL, H2 or any other JDBC database.  It also includes an in-memory database implementation that makes unit tests screaming fast.  Of course, `bucket` uses `schema` to make sure the data is in the right shape.

![Bucket](https://github.com/cleancoders/c3kit/blob/master/img/bucket_200.png?raw=true)

