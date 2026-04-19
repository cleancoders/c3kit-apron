(ns c3kit.apron.schema.path
  "Coordinate-based traversal of schemas. The grammar matches what
   schema/message-seq produces:

   - dot-separated keyword keys:    a.b.c
   - [N] for seq indices:            points[0]
   - [\"...\"] for string keys:       crew[\"bill\"]
   - [*] or .* for wildcards:        crew[*]
   - [:kw] for explicit keywords:    a[:joe]

   Data traversal is intentionally out of scope — use get-in, Specter,
   or any other tree-walker with your own paths. This namespace exists
   for schema trees, whose template semantics (value-spec, key-spec,
   spec) are apron-specific and have no off-the-shelf navigator."
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
