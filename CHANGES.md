### 2.6.0
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
   split into three namespaces: `c3kit.apron.schema.doc` holds shared
   infrastructure (route/doc schemas and format-agnostic helpers),
   `c3kit.apron.schema.openapi` holds the OpenAPI renderer (including the
   `->doc` entry point), and `c3kit.apron.schema.markdown` is a new
   markdown renderer. The OpenAPI output now emits `additionalProperties`
   when a `:map` spec has `:value-spec`.
 * New `c3kit.apron.schema/walk-schema` — a reusable post-order tree
   walker over specs. Normalizes each node, recurses into its children by
   `:type`, and calls an emit function with `(spec children)`. The OpenAPI
   and markdown renderers are both built on it.
 * Two new optional spec fields — `:description` (string) and `:example`
   (any). Both flow into OpenAPI output (as `description` / `example`) and
   into the markdown renderer.

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
