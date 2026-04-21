(ns c3kit.apron.schema.path
  "Coordinate-based traversal of schemas and data. The grammar matches
   what schema/message-seq produces:

   - dot-separated keyword keys:    a.b.c
   - [N] for seq indices:            points[0]
   - [\"...\"] for string keys:       crew[\"bill\"]
   - [:kw] for explicit keywords:    a[:joe]

   For schemas, two reserved segment names provide access to dynamic
   templates:
   - :value  — the :value-spec of a :map, or the :spec of a :seq
   - :key    — the :key-spec of a :map

   If the spec has no matching template (e.g. a :map with no :value-spec),
   :value and :key fall back to ordinary field lookup in :schema.

   schema-at walks a schema tree (template semantics).
   data-at walks concrete data (supports an optional :lenient? option
   for keyword/string key equivalence)."
  (:require [clojure.string :as s]))

(defn- classify [token]
  (cond
    (or (= "*" token) (= "[*]" token))
    (throw (ex-info "wildcard (* or [*]) is no longer supported; use [:value] for a map value-spec or seq entry, [:key] for a map key-spec"
                    {:token token}))

    (s/starts-with? token "[")
    (let [inside (subs token 1 (dec (count token)))]
      (cond
        (re-matches #"-?\d+" inside)               [:index #?(:clj (Long/parseLong inside) :cljs (js/parseInt inside))]
        (and (s/starts-with? inside "\"")
             (s/ends-with?   inside "\""))         [:str (subs inside 1 (dec (count inside)))]
        (s/starts-with? inside ":")                [:key (keyword (subs inside 1))]))

    :else
    [:key (keyword token)]))

(defn parse [path]
  (mapv classify (re-seq #"[a-zA-Z_\-][a-zA-Z0-9_\-]*|\*|\[[^\]]*\]" path)))

(defn- identifier-safe? [s]
  (boolean (re-matches #"[a-zA-Z_\-][a-zA-Z0-9_\-]*" s)))

(defn- segment->string [[kind v]]
  (case kind
    :key   (if (identifier-safe? (name v))
             (str "." (name v))
             (str "[:" (name v) "]"))
    :index (str "[" v "]")
    :str   (str "[" (pr-str v) "]")
    (str "[" (pr-str v) "]")))

(defn unparse [segments]
  (let [joined (apply str (map segment->string segments))]
    (if (s/starts-with? joined ".")
      (subs joined 1)
      joined)))

(defn- parse-int [s]
  (when (re-matches #"-?\d+" s)
    #?(:clj (Long/parseLong s) :cljs (js/parseInt s))))

(defn- lenient-get [m k]
  "Lenient is one-way: a keyword path segment can also match a string key
   of the same name, or an integer key whose decimal form matches the
   keyword's name. String and integer segments are never coerced up."
  (or (get m k)
      (when (keyword? k)
        (let [nm (name k)]
          (or (get m nm)
              (when-let [n (parse-int nm)]
                (get m n)))))))

(defn- descend-data [lenient? value segment]
  (case (first segment)
    :key   (if lenient?
             (lenient-get value (second segment))
             (get value (second segment)))
    :str   (get value (second segment))
    :index (when (sequential? value) (nth value (second segment) nil))))

(defn data-at
  "Walk data along a path and return the value at that location. Returns
   nil for missing keys.

   Options:
     :lenient?  when true, a keyword path segment matches a string key of
                the same name (or an integer key whose decimal form
                matches). Default false."
  ([data path] (data-at data path {}))
  ([data path {:keys [lenient?] :or {lenient? false}}]
   (reduce (fn [v seg] (descend-data lenient? v seg)) data (parse path))))

(defn- key-segment-for-schema [spec k]
  (cond
    (nil? spec)             nil
    ;; Reserved :key and :value — template access when the spec supports it.
    (and (= k :value) (or (:value-spec spec) (= :seq (:type spec))))
    (or (:value-spec spec) (:spec spec))

    (and (= k :key) (:key-spec spec))
    (:key-spec spec)

    ;; Ordinary field lookup.
    (map? (:schema spec)) (get (:schema spec) k)
    :else                 (get spec k)))

(defn- descend-schema [spec segment]
  (case (first segment)
    :key   (key-segment-for-schema spec (second segment))
    :index (case (:type spec)
             :map (:value-spec spec)
             :seq (:spec spec)
             nil)
    :str   (when (= :map (:type spec)) (:value-spec spec))))

(defn schema-at [schema path]
  (reduce descend-schema schema (parse path)))
