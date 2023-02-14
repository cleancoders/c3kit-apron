(ns c3kit.apron.corec
  "Common core code.  This file should have minimal dependencies.
  Clients should be able to safely :refer :all from this namespace."
  #?(:clj (:import (java.util UUID)))
  #?(:cljs (:require-macros [c3kit.apron.corec :refer [for-all nand nor xor]]))
  (:require [clojure.string :as str]
            #?(:cljs [goog.string :as gstring])
            #?(:cljs [goog.string.format])))

#?(:clj (defmacro for-all [bindings body]
          `(doall (for ~bindings ~body))))

(def map-all
  "Like for-all, but with map"
  (comp doall map))

(def map-all-indexed
  "Like for-all, but with map-indexed"
  (comp doall map-indexed))

#?(:clj
   (defmacro nand
     "Same as (not (and ...))"
     ([] false)
     ([x & next] `(if-not ~x true (nand ~@next)))))

#?(:clj
   (defmacro nor
     "Same as (not (or ...))"
     ([] true)
     ([x & next] `(if ~x false (nor ~@next)))))

#?(:clj
   (defmacro xor
     "Evaluates expressions one at a time, from left to right.
      If a second form evaluates to logical true, xor returns nil
      and doesn't evaluate any of the other expressions, otherwise
      it returns the value of the first logical true expression.
      If there are no truthy expressions, xor returns nil."
     ([] nil)
     ([x] `(or ~x nil))
     ([x y & next]
      `(let [x# ~x
             y# ~y]
         (if (and x# y#)
           nil
           (xor (or x# y#) ~@next))))))

(defn new-uuid []
  #?(:clj  (UUID/randomUUID)
     :cljs (random-uuid)))

(defn conjv
  "ensures the seq is a vector before conj-ing"
  [col item]
  (conj (vec col) item))

(defn concatv
  "ensures the seq is a vector after concat-ing"
  [& cols]
  (vec (apply concat cols)))

(defn assocv
  "insert elem into vector at index "
  [coll i elem]
  (vec (concat (subvec coll 0 i) [elem] (subvec coll i))))

(defn dissocv
  "removes the item at index i from the vector"
  [coll i]
  (vec (concat (subvec coll 0 i) (subvec coll (inc i)))))

(defn assocv>>
  "assocv with coll as last param"
  [i elem coll]
  (assocv coll i elem))

(defn dissocv>>
  "dissocv with coll as last param"
  [i coll]
  (dissocv coll i))

(defn removev [pred col]
  "core/remove returning a vector"
  (vec (remove pred col)))

(defn removev= [col item]
  "Using =, returns vector without item"
  (removev #(= % item) col))

(defn ffilter
  "Same as (first (filter ...)), but faster!"
  [pred coll]
  (reduce (fn [_ b] (when (pred b) (reduced b))) nil coll))

(defn count-where
  "Counts the number of entities that satisfy a predicate"
  [pred es]
  (reduce #(if (pred %2) (inc %1) %1) 0 es))

(defn count-by
  "Counts the number of entities that
   exactly match some given key-value pairs"
  [es & kvs]
  (let [test (apply hash-map kvs)
        keys (keys test)]
    (count-where #(= test (select-keys % keys)) es)))

(defn sum-by
  "Sums the results of a function applied to the elements of a collection"
  [f coll]
  (transduce (map f) + coll))

(defn map-some
  "Like (filter some? (map f coll)).
   Returns a transducer when no collection is provided."
  ([f] (comp (map f) (filter some?)))
  ([f coll] (sequence (map-some f) coll))
  ([f coll & colls] (apply sequence (map-some f) coll colls)))

(defn some-map
  "Like (map f (filter some? coll)).
   Returns a transducer when no collection is provided."
  ([f] (comp (filter some?) (map f)))
  ([f coll] (sequence (some-map f) coll)))

(defn map-set
  "Like (set (map f coll))"
  [f coll]
  (into #{} (map f) coll))

(defn map-distinct
  "Like (distinct (map f coll))"
  ([f] (comp (map f) (distinct)))
  ([f coll] (sequence (map-distinct f) coll)))

(defn rsort
  "Same as sort, but reversed"
  ([coll] (rsort compare coll))
  ([comp coll] (sort (fn [x y] (comp y x)) coll)))

(defn rsort-by
  "Same as sort-by, but reversed"
  ([keyfn coll] (rsort-by keyfn compare coll))
  ([keyfn comp coll] (sort-by keyfn (fn [x y] (comp y x)) coll)))

(defn- greatest-v
  ([_] nil)
  ([greater? init & vals]
   (reduce #(if (greater? (compare %1 %2)) %1 %2) init vals)))

(def max-v
  "Like max, but works with non-numeric values"
  (partial greatest-v pos?))

(def min-v
  "Like min, but works with non-numeric values"
  (partial greatest-v neg?))

(defn- greatest-m [greater? k comp x y]
  (if (greater? (comp (get x k) (get y k))) x y))

(defn- greatest-by
  ([pos-neg? k coll] (greatest-by pos-neg? k compare coll))
  ([pos-neg? k comp [first & rest]]
   (reduce #(greatest-m pos-neg? k comp %1 %2) first rest)))

(def max-by
  "Like max-key, but works with non-numeric values."
  (partial greatest-by pos?))

(def min-by
  "Like min-key, but works with non-numeric values."
  (partial greatest-by neg?))

(defn ->inspect
  "Insert in threading macro to print the value."
  [v]
  (prn "->inspect: " v)
  v)

(defn index-of
  "Returns the index of e (using =) in the seq. nil if missing."
  [e coll]
  (first (keep-indexed #(if (= e %2) %1) coll)))

(defn ->options
  "Takes keyword argument and converts them to a map.  If the args are prefixed with a map, the rest of the
  args are merged in."
  [options]
  (cond
    (nil? options) {}
    (= [nil] options) {}
    (map? (first options)) (merge (first options) (apply hash-map (rest options)))
    :else (apply hash-map options)))

(defn formats
  "Platform agnostic string format fm"
  [format & args]
  #?(:clj  (apply clojure.core/format format args)
     :cljs (apply gstring/format format args)))

(defn- ->string
  "Creates a string of a given length by repeating the provided value."
  [v length] (apply str (repeat length v)))

(defn pad-left
  "Pads the left side of a string.
   If no character is provided, spaces will be used.
   If the string is greater than or equal to the pad length,
   the unmodified string will be returned."
  ([s length] (pad-left s length " "))
  ([s length v]
   (let [size (count s)]
     (cond->>
       s
       (> length size)
       (str (->string v (- length size)))))))

(defn pad-right
  "Pads the right side of a string.
   If no character is provided, spaces will be used.
   If the string is greater than or equal to the pad length,
   the unmodified string will be returned."
  ([s length] (pad-right s length " "))
  ([s length v]
   (let [size (count s)]
     (cond->
       s
       (> length size)
       (str (->string v (- length size)))))))

(defn pad-left!
  "Same as pad-left, except the string will be trimmed to the desired length."
  ([s length] (pad-left! s length " "))
  ([s length v]
   (let [size (count s)]
     (cond
       (= length size) s
       (> size length) (subs s (- size length))
       :else (str (->string v (- length size)) s)))))

(defn pad-right!
  "Same as pad-right, except the string will be trimmed to the desired length."
  ([s length] (pad-right! s length " "))
  ([s length v]
   (let [size (count s)]
     (cond
       (= length size) s
       (> size length) (subs s 0 length)
       :else (str s (->string v (- length size)))))))

(defn char-code-at
  "Char code at the given index of a string"
  [s i]
  #?(:clj  (int (.charAt s i))
     :cljs (.charCodeAt s i)))

(defn first-char-code
  "Char code at index 0 of a string"
  [s] (char-code-at s 0))

(def not-blank? (complement str/blank?))

(defn remove-nils
  "Return a map where all the keys with nil values are removed"
  [e]
  (reduce (fn [r [k v]] (if (= nil v) r (assoc r k v))) {} e))

(defn ex?
  "Returns true is e is an exception/error for the running platform"
  [e]
  #?(:clj  (instance? Exception e)
     :cljs (instance? js/Error e)))

(defn noop
  "Does nothing"
  [& _])

(defn invoke
  "Insert in threading macro to invoke a function with a given set of arguments."
  [f & args]
  (apply f args))

(defn narity
  "Create an n-arity function from a 0-arity function"
  [f]
  (fn [& _] (f)))
