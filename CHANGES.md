### 3.0.0

Major restructure of the ref/registry system into a single extensible
**lexicon**, with breaking changes throughout. The "ref" vocabulary is
gone — replaced by "lex" — and the validation/coercion bundles are
now first-class data in `c3kit.apron.schema/*lexicon*` that you can
extend at load time or scope to a request.

 * **Single `*lexicon*` with four slots.** `c3kit.apron.schema/*lexicon*`
   is a dynamic var holding `{:types {} :validations {} :coercions {}
   :presentations {}}`. Each slot is a `{name → lex-map}` map.
   Replaces the old `*ref-registry*` atom.
 * **Extensibility API.**
   - `update-lexicon!` mutates a slot at the root (load-time extension by
     client namespaces). `(s/update-lexicon! :validations assoc :positive?
     {:validate pos? :message "must be positive"})`.
   - `with-lexicon` scopes overrides for a body via dynamic binding; a
     partial lexicon map merges into the current binding.
   - `coerce-with` / `validate-with` / `conform-with` / `present-with`
     thread a lexicon map through the bang form for explicit per-call
     overrides.
 * **`lex` / `lex!` lookup.** Slot-aware. `(s/lex! :validations :string?)`
   returns the lex map. Names normalize from keywords, symbols, or
   strings; factory invocation via `[name & args]` vectors.
 * **Default vocabulary lives in bundle namespaces.** Requiring each
   bundle registers its lexes via `update-lexicon!` at load.
   - `c3kit.apron.schema.types` — `default-types`. Type lexes have shape
     `{:validations [...] :coercions [...]}`; both lists run alongside
     spec-level entries (validations type-first, coercions type-last).
     Custom types are first-class: `(s/update-lexicon! :types assoc
     :money {:validations [...] :coercions [...]})`.
   - `c3kit.apron.schema.validations` — `default-validations`. Type
     predicates, numeric predicates, factories (`:>`, `:between`,
     `:matches`, `:one-of`, ...), and combinators (`:nil-or?`, `:maybe?`,
     `:not?`, `:and?`, `:or?`).
   - `c3kit.apron.schema.coercions` — `default-coercions`. String
     transforms, type coercers, the `:default` factory.
   - `c3kit.apron.schema.presentations` — **new.** `default-presentations`
     ships `:trim`, `:upper-case`, `:lower-case`, `:capitalize`,
     `:->string`, `:omit`, and the `:default` factory.
 * **Helper namespaces.** `c3kit.apron.schema.validators` and
   `c3kit.apron.schema.coercers` hold the bare predicate / coercion
   fns that the bundles compose. Pure data, no schema dep. `coercers`
   depends on `validators` for shared predicates (`bigdec?`,
   `multiple?`, `date?`, etc.). `schema.refs` is gone.
 * **`:presentations` spec slot.** Parallel to `:validations` and
   `:coercions`. Resolves via the `:presentations` lexicon slot.
   `present-field-spec` and `present-entity-level-spec` apply inline
   `:present` fns then bundled presentations in order.
 * **Type extensibility ends the closed type tables.** `type-validators`,
   `type-coercers`, the static `valid-types` set, `type-validator!`,
   and `type-coercer!` are gone. `(schema/valid-types)` is now a fn
   that reads from `(:types *lexicon*)`. New types can be added by
   anyone — `(s/update-lexicon! :types assoc :my-money {...})` — and
   they're recognized by `conform-schema!`'s meta-validation.
 * **Spec-schema declares `:coercions` and `:presentations`.** Previously
   only `:validations` was declared in the meta-schema. `conform-schema!`
   now validates all three slots.
 * **Validation message precedence unified.** One rule for every entry
   shape (inline map, bare lex ref, factory vector): final `:message =
   (or entry's-:message resolved-(lex/factory)-:message spec's-:message
   "is invalid")`. Spec `:message` is the fallback used when nothing
   more specific is available. Previously bare-ref and vector forms
   let spec `:message` override the lex's; that asymmetry is gone.
 * **`:maybe?` combinator.** Like `:nil-or?` (wraps a predicate with
   nil tolerance) but uses the inner predicate's `:message` directly
   without the `"may be nil or "` prefix. Default types use `:maybe?`
   so the per-type messages stay terse ("must be a string", not "may
   be nil or must be a string"). `:nil-or?` keeps its original prefix
   for user-facing explicitness.

**Removed (breaking).** Migrate before upgrading:
 * `register-ref!` / `reset-ref-registry!` / `get-ref!` / `*ref-registry*`
   → `(s/update-lexicon! :validations assoc :name lex-map)` etc., or
   `(s/with-lexicon {:validations {:name lex-map}} ...)` for scoped.
 * `c3kit.apron.schema.refs` namespace → `c3kit.apron.schema.validations`
   for predicate / factory / combinator lexes, `.coercions` for
   coercion lexes. Built-in vocabulary loads at the bundle ns' load.
 * `type-validator!` / `type-coercer!` / static `valid-types` →
   types live in `(:types *lexicon*)` now; the public `(s/valid-types)`
   fn returns the current set.
 * `schema/coerce-value` / `conform-value` / `present-value` (no bang)
   → use the bang versions.
 * `schema/error-message-map` → `schema/message-map`.
 * `schema/messages` → `schema/message-seq`.
 * `schema/coerce-errors` / `validate-errors` / `validation-errors` /
   `conform-errors` → use the `*-message-map` fns.
 * `schema/nil-or` and `validators/nil-or` → `schema/nil?-or` /
   `validators/nil?-or`.
 * `corec/map-some` → `clojure.core/keep`.

**Behavioral changes worth flagging:**
 * Spec `:message` no longer overrides per-entry `:message`. If you
   wrote `{:type :string :validations [...] :message "custom"}`
   expecting "custom" to surface for every validation failure,
   migrate the message onto each entry — e.g. `{:validations
   [{:validate fn :message "custom"} ...]}` — or accept that the
   entry's own message wins.
 * Coercion failure messages: when a type's coercer fn throws,
   `coerce-ex`'s specific "can't coerce VALUE to TYPE" message reaches
   the caller. Previously a spec `:message` could suppress it; now
   only an entry-level `:message` on the specific coercion entry does.
 * `:nil-or?` failure messages prefix with `"may be nil or "` as
   before. New `:maybe?` is the prefix-free alternative.

**Spec-test reorganization.** Each production namespace has its own
spec file:

   c3kit.apron.schema                ↔ schema_spec
   c3kit.apron.schema.validators     ↔ validators_spec
   c3kit.apron.schema.validations    ↔ validations_spec
   c3kit.apron.schema.coercers       ↔ coercers_spec
   c3kit.apron.schema.coercions      ↔ coercions_spec
   c3kit.apron.schema.presentations  ↔ presentations_spec
   c3kit.apron.schema.types          ↔ types_spec

### 2.8.0
 * Entity-scoped refs. A registered ref can set `:scope :entity`; its
   `:validate`/`:coerce` fn then receives `(entity field-key)` instead of
   `(value)` and runs after the field-level pass. Lets cross-field rules
   like `[:required-when :species "dog"]` live next to the field they
   constrain rather than being lifted into `:*`.
 * Pipeline order for an entity is now: (1) per-field value-scoped
   coerce → validate, (2) per-field entity-scoped coerce → validate,
   (3) `:*` entity-level coerce → validate. `validate-value!`,
   `coerce-value!`, and `conform-value!` bypass entity-scoped entries
   since they operate on a single value with no entity context.
 * New combinator refs in `c3kit.apron.schema.refs`: `:nil-or?`, `:not?`,
   `:and?`, `:or?`. They take other refs (or inline fns) as arguments and
   compose them — e.g. `[:nil-or? :pos?]`, `[:and? :integer? [:between 0 10]]`.
   Default messages compose from inner refs' `:message`.
 * New public helpers `schema/->validate-fn` and `schema/->coerce-fn`.
   Combinator factories call these to resolve a ref name, factory
   invocation, or inline fn into the underlying fn.

### 2.7.0
 * New ref registry for EDN-loadable validations and coercions. Two new
   field-spec keys, `:validations` and `:coercions`, accept registered refs
   by name (keyword, symbol, or string), factory vectors `[:ref & args]`,
   or map entries — so schemas can live entirely as data with no fn
   literals. Existing `:validate` and `:coerce` remain function-only and
   are unchanged.
 * `c3kit.apron.schema.refs` ships a standard catalog of refs:
   type/numeric/apron predicates, comparison and shape factories, string
   and type coercers, and a `:default` coercion factory. Each ref is its
   own var for à-la-carte use; `(refs/install!)` drains the catalog into
   the registry. The registry is empty by default.
 * Public API in `c3kit.apron.schema`: `register-ref!` (1- or 2-arg —
   1-arg pulls `:key` from `(meta v)`), `get-ref!`, `reset-ref-registry!`,
   `*ref-registry*` (bindable for plugin and test isolation), `*warn-fn*`,
   and `verify-schema-refs` (walks a schema and throws on the first
   unresolved or wrong-slot ref).
 * Missing-ref errors surface as `"missing ref :X"` field errors rather
   than blowing up the call chain, so partial failures are visible in the
   error map.
 * Per-entry `:message` honored on `:coercions` (matching `:validations`).
   On a failure the precedence is entry → resolved ref → spec → ex-message.

### 2.6.0
 * `coerce`, `validate`, `conform`, and `present` now accept either a
   bare field map (`{:field spec ...}`) or a wrapped `:map` spec
   (`{:type :map :schema {...}}`) as the root schema. Both forms
   produce the same output (a transformed entity, never wrapped).
   Wrapped form allows carrying top-level `:coerce`, `:validate`,
   `:validations`, `:description`, and `:name` on the outer spec,
   which previously had nowhere to live.
 * `c3kit.apron.schema.path/schema-at` accepts the same two forms as
   its input, so paths produced by `message-seq` navigate either form
   without the caller unwrapping.
 * `c3kit.apron.schema` `:map` type now supports dynamic keys via two new
   optional spec keys: `:key-spec` (spec applied to every dynamic key) and
   `:value-spec` (spec applied to every dynamic value). Known keys listed in
   `:schema` win; any other entries flow through the dynamic specs. Key
   coerce/validate errors land at the original key; value errors land at the
   coerced key. When neither is present, unknown keys continue to be dropped.
 * `message-seq` path format: keyword keys remain dot-separated
   (`start.x`), while seq indices and non-keyword keys now use bracket
   notation (`points[0].x`, `crew["bill"]`). Consistent dot-vs-bracket
   language across known keys, dynamic keys, and seq indices.
 * `c3kit.apron.doc` has been relocated under the `schema` umbrella and
   split into two namespaces: `c3kit.apron.schema.doc` holds shared
   infrastructure (route/doc schemas and format-agnostic helpers) and
   `c3kit.apron.schema.openapi` holds the OpenAPI renderer (including
   the `->doc` entry point). The OpenAPI output now emits
   `additionalProperties` when a `:map` spec has `:value-spec`.
 * New `c3kit.apron.schema/walk-schema` — a reusable post-order tree
   walker over specs. Normalizes each node, recurses into its children
   by `:type`, and calls an emit function with `(spec children)`. The
   OpenAPI renderer is built on it.
 * Three new optional spec fields — `:description` (string),
   `:example` (any), and `:name` (keyword). `:description` and
   `:example` flow into OpenAPI output (as `description` / `example`).
   `:name` marks a spec as a named, reusable definition; the OpenAPI
   `->doc` renderer collects named schemas into `components.schemas`
   and uses `$ref` at each use site.
 * New `schema.path` namespace — coordinate-based traversal of schemas
   and data using the same grammar as `schema/message-seq`: dots for
   keyword keys (`a.b.c`), brackets for indices (`points[0]`), string
   keys (`crew["bill"]`), keyword literals (`crew[:joe]`). Two reserved
   segment names access dynamic-entry templates on a `:map` or `:seq`:
   `:value` (→ `:value-spec` on `:map`, `:spec` on `:seq`) and `:key`
   (→ `:key-spec` on `:map`). `:value` / `:key` fall back to ordinary
   field lookup when the spec has no matching template.
   `schema-at` walks schemas; `data-at` walks concrete data, with an
   optional `{:lenient? true}` for keyword/string key equivalence.
   `message-seq` uses `schema.path/unparse` for path rendering, so
   error paths round-trip cleanly through `parse`.
 * New `c3kit.apron.version/current` — runtime access to the apron
   version string. The `VERSION` file moved from the project root to
   `resources/c3kit/apron/VERSION` and now ships inside the jar.

### 2.5.1
 * `c3kit.apron.refresh` now works under Babashka. The final re-evaluation
   step uses `load-file` (via a new bb-compatible backend) instead of
   `require :reload`, which is a no-op under SCI. JVM behavior is unchanged
   — the `:clj` reader-conditional branch still calls
   `clojure.tools.namespace.reload/track-reload` with the same tracker. File
   moved from `src/clj/c3kit/apron/refresh.clj` to
   `src/cljc/c3kit/apron/refresh.cljc` (git rename preserved).

### 2.5.0
 * Adds doc ns to convert apron schemas to JSON/OpenAPI schemas

### 2.4.2
 * Fixes issue where dates cannot be created with overflow or underflow values

### 2.4.1
 * Adds Babashka support to `.clj` namespaces
 * Replaces `StringBuffer` with `StringBuilder` in `c3kit.apron.verbose`

### 2.4.0
 * Adds support for Babashka
 * Replaces use of `java.util` calendars with `java.time`
 * Improves schema coercion exception reporting (macro for line numbers)
 * Adds `toString` override to `Cursor` in ClojureScript
 * Upgrades `timbre` and `clojure` dependencies
 * Adds `org.babashka/json` to dependencies

### 2.3.0 (Sept 18, 2025)
 * adds bad-words/contains-profanity? function

### 2.2.3
 * Fixes issue where `count-by` throws when no parameters are provided.

### 2.2.2
 * Fixes case where test-fabric fails to query seqable attributes by pattern matching

### 2.2.1
 * Fixes c3kit.apron.log `time` override warning

### 2.2.0 
 * adds log/time (Feb 10, 2025)
 * pickle namespace (Apr 22, 2025)

### 2.1.5 (Nov 18, 2024)
 * schema conform can handle nested coercion

### 2.1.4 (May 28, 2024)
 * Upgrades deps
 * Passes all specs under advanced cljs optimizations
 * Fixes discrepancy with `c3kit.apron.corec/oset-in` between `:none` and `:advanced` optimizations where it would throw in `:none`, but not `:advanced`.

### 2.1.3 (Mar 18, 2024)
 * c3kit.apron.schema changed
   * c3kit.apron.schema is now self defining. Schema provides a schema for schema definitions.
   * new :seq type allows more clear specification of sequential values, but also allows the processing of the seq itself
   * new :map type clarifies the use of nested schemas
   * new :one-of type allows a field to take any number of shapes
   * adds normalize-schema and normalize-spec to expand shorthands (backwards compatibility)
   * adds conform-schema to normalize, coerce, and validate schemas
   * legend build will conform-schema! on all schema
   * rename `<process>-errors` to `<process>-message-map`
   * backwards compatible fns have been DEPRECATED 

### 2.1.0 (Feb 3, 2024)
 * c3kit.apron.schema overhaul - minor breaking changes
   * For 98% of the usage, there should be no visible changes, however there are some breaking changes.
   * Failed operation no longer return the unintelligible SchemaError. Instead, it returns a map where the problematic keys have FieldErrors as values.  This should be much more printable and readable.
   * Fixes bug where coerce, validate and conform returned different results
   * Error messages should be more detailed where possible

### July 14, 2023
 * c3kit.apron.app/start-env app must be the first parameter
