(ns c3kit.apron.schema.path
  "Coordinate-based traversal of schemas and data. The grammar matches
   what schema/message-seq produces:

   - dot-separated keyword keys:    a.b.c
   - [N] for seq indices:            points[0]
   - [\"...\"] for string keys:       crew[\"bill\"]
   - [*] or .* for wildcards:        crew[*]
   - [:kw] for explicit keywords:    a[:joe]

   schema-at walks a schema tree (template semantics).
   data-at walks concrete data (supports an optional :lenient? option
   for keyword/string key equivalence)."
  (:require [clojure.string :as s]))

(defn- classify [token]
  (cond
    (= "*" token)
    [:wildcard]

    (s/starts-with? token "[")
    (let [inside (subs token 1 (dec (count token)))]
      (cond
        (= "*" inside)                             [:wildcard]
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
    :key      (if (identifier-safe? (name v))
                (str "." (name v))
                (str "[:" (name v) "]"))
    :index    (str "[" v "]")
    :str      (str "[" (pr-str v) "]")
    :wildcard "[*]"
    (str "[" (pr-str v) "]")))

(defn unparse [segments]
  (let [joined (apply str (map segment->string segments))]
    (if (s/starts-with? joined ".")
      (subs joined 1)
      joined)))

(defn- lenient-get [m k]
  (or (get m k)
      (cond
        (keyword? k) (get m (name k))
        (string? k)  (get m (keyword k))
        :else        nil)))

(defn- descend-data [lenient? value segment]
  (case (first segment)
    :key      (if lenient?
                (lenient-get value (second segment))
                (get value (second segment)))
    :str      (if lenient?
                (lenient-get value (second segment))
                (get value (second segment)))
    :index    (when (sequential? value) (nth value (second segment) nil))
    :wildcard (throw (ex-info "data-at: wildcard paths are not supported for data traversal"
                              {:value value}))))

(defn data-at
  "Walk data along a path and return the value at that location. Returns
   nil for missing keys. Wildcards throw.

   Options:
     :lenient?  when true, a keyword path segment matches a string key of
                the same name (and vice versa). Default false."
  ([data path] (data-at data path {}))
  ([data path {:keys [lenient?] :or {lenient? false}}]
   (reduce (fn [v seg] (descend-data lenient? v seg)) data (parse path))))

(defn- descend-schema [spec segment]
  (case (first segment)
    :key       (cond
                 (nil? spec)          nil
                 (map? (:schema spec)) (get (:schema spec) (second segment))
                 :else                 (get spec (second segment)))
    (:index :wildcard)
    (case (:type spec)
      :map (:value-spec spec)
      :seq (:spec spec)
      nil)
    :str  (when (= :map (:type spec)) (:value-spec spec))))

(defn schema-at [schema path]
  (let [segments (parse path)
        [seg0 & rest-segs] segments
        start    (case (first seg0)
                   :key (get schema (second seg0))
                   nil)]
    (reduce (fn [s seg] (descend-schema s seg)) start rest-segs)))
