(ns c3kit.apron.test-fabric
  "Truth is like the sun. You can shut it out for a time, but it ain't goin' away."
  (:require [clojure.string :as str]))

(defn- ->pattern [s]
  (-> s
      (str/replace "%" ".*")
      (str/replace "_" ".")
      re-pattern))

(defn- pattern-comparator [v case-sensitive?]
  (let [pattern (->pattern v)]
    (fn [ev]
      (when ev
        (let [ev (if case-sensitive? ev (str/upper-case ev))]
          (boolean (re-matches pattern ev)))))))

(defn- multi? [v] (or (sequential? v) (set? v)))

(defn- -normal-tester [f v]
  (fn [ev]
    (if (multi? ev)
      (some #(f % v) ev)
      (and (some? ev) (f ev v)))))

(defn- -or-tester [values]
  (let [v-set? (partial contains? (set values))]
    (fn [ev]
      (if (multi? ev)
        (some v-set? ev)
        (v-set? ev)))))

(defn- -tester [form]
  (condp = (first form)
    '> (let [v (second form)] (if (number? v) (-normal-tester > v) (-normal-tester (comp pos? compare) v)))
    '< (let [v (second form)] (if (number? v) (-normal-tester < v) (-normal-tester (comp neg? compare) v)))
    '>= (let [v (second form)] (if (number? v) (-normal-tester >= v) (-normal-tester (comp not neg? compare) v)))
    '<= (let [v (second form)] (if (number? v) (-normal-tester <= v) (-normal-tester (comp not pos? compare) v)))
    'like (pattern-comparator (second form) true)
    'ilike (pattern-comparator (str/upper-case (second form)) false)
    'not= (complement (-or-tester (rest form)))
    '= (-or-tester (rest form))
    (-or-tester form)))

(defn- ensure-key [k]
  (if (set? k)
    (map ensure-key k)
    (->> [(namespace k) (name k)]
         (remove nil?)
         (map keyword)
         vec)))

(defn- get-tester-by-type [v]
  (cond (set? v) (-or-tester v)
        (sequential? v) (-tester v)
        (nil? v) nil?
        :else (-normal-tester = v)))

(defn- kv->tester [[k v]]
  (let [tester   (get-tester-by-type v)
        key-path (ensure-key k)]
    (fn [e] (tester (get-in e key-path)))))

(defn spec->tester
  "Creates a predicate based on the key-value pairs in spec."
  [spec]
  (apply every-pred (map kv->tester spec)))
