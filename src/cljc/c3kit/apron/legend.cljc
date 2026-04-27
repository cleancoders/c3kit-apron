(ns c3kit.apron.legend
  "Registry mapping `:kind` keywords to their conformed schemas. Provides `for-kind` lookup and throwing variants of schema's `present!`/`coerce!`/`conform!` that auto-resolve the schema from `(:kind entity)`."
  (:require
    [c3kit.apron.schema :as schema]))

(def retract {:kind (schema/kind :db/retract) :id schema/id})

(def ^:dynamic index {})

(defn build
  "Build a `{kind -> schema}` registry from a (possibly nested) sequence of schemas.
  Schemas without a `:kind` are filtered out; remaining schemas are conformed via
  `schema/conform-schema!` and indexed by `(-> schema :kind :value)`."
  [schemas]
  (->> (flatten schemas)
       (filter :kind)
       (map schema/conform-schema!)
       (reduce #(assoc %1 (-> %2 :kind :value) %2) {})))

(defn init!
  "Install `schemas` (a `{kind -> schema}` map, typically from `build`) as the
  global default registry used by the arity-1 `for-kind`/`present!`/`coerce!`/`conform!`."
  [schemas]
  #?(:clj  (alter-var-root #'index (fn [_] schemas))
     :cljs (set! index schemas)))

(defn for-kind
  "Look up the schema for `kind`. Arity-1 uses the global registry installed by
  `init!`; arity-2 takes an explicit registry. Throws `ex-info` if no schema is
  registered for `kind`."
  ([kind] (for-kind index kind))
  ([index kind]
   (or (get index kind)
       (throw (ex-info (str "Missing legend for kind: " (pr-str kind)) {:kind kind})))))

(defn present!
  "Run `schema/present!` for `entity` using the schema registered for
  `(:kind entity)`. Returns nil when `entity` is nil. Throws if no schema is
  registered for that kind, or if presentation fails."
  ([entity] (present! index entity))
  ([index entity]
   (when entity
     (schema/present! (for-kind index (:kind entity)) entity))))

(defn coerce!
  "Run `schema/coerce!` for `entity` using the schema registered for
  `(:kind entity)`. Returns nil when `entity` is nil. Throws if no schema is
  registered for that kind, or if coercion fails."
  ([entity] (coerce! index entity))
  ([index entity]
   (when entity
     (schema/coerce! (for-kind index (:kind entity)) entity))))

(defn conform!
  "Run `schema/conform!` (coerce + validate) for `entity` using the schema
  registered for `(:kind entity)`. Returns nil when `entity` is nil. Throws if no
  schema is registered for that kind, or if conformance fails."
  ([entity] (conform! index entity))
  ([index entity]
   (when entity
     (schema/conform! (for-kind index (:kind entity)) entity))))


