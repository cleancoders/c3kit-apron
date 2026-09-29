(ns c3kit.apron.schema.doc
  "Shared infrastructure for doc-format renderers (OpenAPI, markdown, ...).
   Describes the expected shape of a route/doc spec and provides helpers that
   are format-agnostic."
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.path :as path]
            [clojure.string :as s]))

(defn- resolve-validate-fn
  "Resolves a :validate / :validations entry to the validate fn it ultimately
   means -- an inline fn as-is, an inline map's :validate (recursively, in
   case that is itself a lex name), or a lex name (keyword/symbol/string, or
   a [name & args] factory vector) resolved through the schema lexicon
   (c3kit.apron.schema/lex). This is how required? sees through data-only EDN
   schemas that spell required-ness as :validations [:present?] or
   :validations [:required] rather than a literal fn. Returns nil -- never
   throws -- when nothing resolves, so required? stays safe on arbitrary
   spec data."
  [e]
  (cond
    (fn? e)  e
    (nil? e) nil
    (map? e) (recur (:validate e))
    :else    (:validate (try (schema/lex :validations e)
                             (catch #?(:clj Exception :cljs :default) _ nil)))))

(defn- present?-fn? [f]
  (= schema/present? f))

(defn required? [{:keys [validate validations required]}]
  (boolean (or required
               (present?-fn? (resolve-validate-fn validate))
               (some (comp present?-fn? resolve-validate-fn) validations))))

(defn required-fields [schema]
  (keys (filter (fn [[_ v]] (required? v)) schema)))

(defn integer-keys? [m]
  (every? integer? (keys m)))

(def nil?-or-map? (schema/nil?-or map?))

(defn schema-map? [m]
  (every? (comp nil?-or-map? :schema) (vals m)))

(def route-schema
  {:path            {:type :string :validate schema/present? :message "is a required string"}
   :method          {:type :keyword :validate schema/present? :message "is a required keyword"}
   :request-schema  {:type {:params {:type :map}
                            :body   {:type :map}}}
   :response-schema {:type        :any                      ; :map would short-circuit our custom :validations
                     :validations [{:validate nil?-or-map?  :message "must be a map"}
                                   {:validate integer-keys? :message "keys must be response codes (integers)"}
                                   {:validate schema-map?   :message ":schema must be a map"}]}})

(def doc-schema
  {:title   {:type :string :validate schema/present? :message "is required"}
   :version {:type :string :validate schema/present? :message "is required"}
   :routes  {:type :seq :spec {:type route-schema}}})

(defn maybe-invalid-doc [spec]
  (let [validated (schema/validate doc-schema spec)]
    (when (schema/error? validated)
      (throw (ex-info (s/join "; " (schema/message-seq validated)) spec)))))

;; region ----- describe -----

(defn- describe-entry [path-segments spec]
  (cond-> {:path (path/unparse path-segments) :type (:type spec) :required (required? spec)}
    (contains? spec :default) (assoc :default (:default spec))
    (:description spec) (assoc :description (:description spec))))

(defn- describe-node
  "Returns the flattened seq of describe entries for `spec` (already
   normalized) and everything reachable beneath it, with `path-segments`
   as the path/unparse-style prefix leading to it."
  [path-segments spec]
  (let [spec (schema/normalize-spec spec)]
    (cons (describe-entry path-segments spec)
          (case (:type spec)
            :map (concat
                   (mapcat (fn [[k sub]] (describe-node (conj path-segments [:key k]) sub))
                           (dissoc (:schema spec) :*))
                   (when-let [key-spec (:key-spec spec)]
                     (describe-node (conj path-segments [:key :key]) key-spec))
                   (when-let [value-spec (:value-spec spec)]
                     (describe-node (conj path-segments [:key :value]) value-spec)))
            :seq (when-let [entry-spec (:spec spec)]
                   (describe-node (conj path-segments [:key :value]) entry-spec))
            ;; :one-of has no path segment for "which alternative" in the
            ;; path grammar, so its :specs are not individually expanded.
            nil))))

(defn describe
  "Walks `spec-or-schema` and returns a seq of maps, one per reachable field
   path:

     {:path <path-string> :type <type> :default <if any> :required <bool> :description <if any>}

   Paths use the c3kit.apron.schema.path grammar (see SCHEMA.md 'Path
   Traversal'). A :map's declared fields are walked by name; its dynamic
   :key-spec / :value-spec (when present) are reported at the `.key` /
   `.value` template segments. A :seq's entry :spec is reported at `.value`
   -- schemas have no concrete index to report against, only data does.

   :one-of specs are reported as a single entry at their own path; the path
   grammar has no segment for 'which alternative', so the individual :specs
   are not expanded into separate entries.

   `spec-or-schema` may be a bare schema (a map of field name -> spec, the
   form passed to coerce/validate/conform) or a wrapped spec (e.g.
   {:type :map :schema {...}}); either way only its fields are described --
   there's no path for the root itself. Pure data in, pure data out: no
   function in the schema (:coerce, :validate, ...) is ever called."
  [spec-or-schema]
  (let [{:keys [schema key-spec value-spec]}
        (if (contains? spec-or-schema :type)
          (schema/normalize-spec spec-or-schema)
          {:schema spec-or-schema})]
    (vec (concat
           (mapcat (fn [[k sub]] (describe-node [[:key k]] sub)) (dissoc schema :*))
           (when key-spec (describe-node [[:key :key]] key-spec))
           (when value-spec (describe-node [[:key :value]] value-spec))))))

;; endregion ^^^^^ describe ^^^^^
