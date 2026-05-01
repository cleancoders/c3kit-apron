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
