(ns c3kit.apron.pickle
  (:require [c3kit.apron.corec :as ccc]))

(def LONG_STRING_LENGTH 16)

(defprotocol Custom
  (custom-type [_])
  (custom->map [_]))

#?(:cljs (def js-uid-counter (atom 0)))

(defn unique-id [o]
  #?(:clj (System/identityHashCode o)
     :cljs (if-let [id (ccc/oget o "_pickle_id")]
             id
             (let [id (swap! js-uid-counter inc)]
               (ccc/oset o "_pickle_id" id)
               id))))

(defn- ref-value [id] {:_t :ref :_v id})

(declare ref!)
(declare do-pickle)

(defn- pickled-map-value [refs m]
  (into {} (map (fn [[k v]] [(do-pickle refs k) (do-pickle refs v)]) m)))

(defn pickle-map [refs o]
  (let [v (pickled-map-value refs o)]
    {:_t :map :_v v}))

(defn pickle-seq [refs o]
  {:_t :seq :_v (mapv (partial do-pickle refs) o)})

(defn pickle-custom [refs o]
  {:_t (custom-type o) :_v (pickled-map-value refs (custom->map o))})

(defn- ref! [refs o]
  (let [id (unique-id o)]
    (when-not (contains? @refs id)
      (let [pickled (cond
                      (record? o) (pickle-custom refs o)
                      (satisfies? Custom o) (pickle-custom refs o)
                      (sequential? o) (pickle-seq refs o)
                      (map? o) (pickle-map refs o)
                      :else o)]
        (swap! refs assoc id pickled)))
    (ref-value id)))

(defn- do-pickle [refs obj]
  (cond (sequential? obj) (ref! refs obj)
        (map? obj) (ref! refs obj)
        (record? obj) (ref! refs obj)
        (satisfies? Custom obj) (ref! refs obj)
        :else obj))

(defn pickle [obj]
  (let [refs   (atom {})
        result (do-pickle refs obj)]
    (if (empty? @refs)
      result
      {:_refs @refs :_object result})))

(declare do-unpickle)

(defn unpickle-ref [refs id]
  (let [pickled (get refs id)
        unpickled (do-unpickle refs pickled)]
    ;; TODO - MDM: cache?
    unpickled))

(defn unpickle-map [refs o]
  (into {} (map (fn [[k v]] [(do-unpickle refs k) (do-unpickle refs v)]) o)))

(defn unpickle-seq [refs o]
  (mapv (partial do-unpickle refs) o))

(defmulti map->custom (fn [_t _m] _t))

(defn unpickle-custom [refs _t _v]
  (let [m (unpickle-map refs _v)]
    (map->custom _t m)))

(defn- do-unpickle [refs obj]
  (if (map? obj)
    (let [_t (:_t obj)
          _v (:_v obj)]
      (case _t
        nil (throw (ex-info (str "unpickle: missing type" obj) {:obj obj}))
        :ref (unpickle-ref refs _v)
        :map (unpickle-map refs _v)
        :seq (unpickle-seq refs _v)
        (unpickle-custom refs _t _v)))
    obj))

(defn unpickle [pickled-obj]
  (if (and (map? pickled-obj) (contains? pickled-obj :_refs))
    (do-unpickle (:_refs pickled-obj) (:_object pickled-obj))
    pickled-obj))