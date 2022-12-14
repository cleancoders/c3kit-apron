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
