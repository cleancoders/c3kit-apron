(ns c3kit.apron.schema.coercers
  "Pure coercion helpers and constructors used by both c3kit.apron.schema
   and c3kit.apron.schema.coercions. Owns no lexicon state — coercion
   lexes are bundled in c3kit.apron.schema.coercions and merged into the
   default lexicon by c3kit.apron.schema.

   Named with the plural suffix to avoid the CLJS namespace/var clash
   with c3kit.apron.schema/coerce (the entity-level public fn)."
  #?(:cljs (:require-macros [c3kit.apron.schema.coercers :refer [coerce-ex]]))
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    #?(:cljs [com.cognitect.transit.types])))                 ;; https://github.com/cognitect/transit-cljs/issues/41

(def date #?(:clj java.util.Date :cljs js/Date))

#?(:clj
   (defmacro coerce-ex [value type]
     `(let [value#     ~value
            type#      ~type
            value-str# (pr-str value#)
            value-str# (if (< 50 (count value-str#))
                         (str (subs value-str# 0 45) "...")
                         value-str#)]
        (ex-info (str "can't coerce " value-str# " to " type#) {:value value# :type type#}))))

#?(:cljs
   (defn parse! [f v]
     (let [result (f v)]
       (if (js/isNaN result)
         (throw (js/Error "parsed NaN"))
         result))))

(defn bigdec? [v] #?(:clj (instance? BigDecimal v) :cljs (number? v)))

(defn ->boolean [value]
  (cond (nil? value) nil
        (boolean? value) value
        (string? value) (not= "false" (str/lower-case value))
        :else (boolean value)))

(defn ->string [value] (some-> value str))
(defn str-or-nil [v] (->string v))

(defn ->keyword [value]
  (cond
    (nil? value) nil
    (keyword? value) value
    :else (let [s (str value)]
            (if (str/starts-with? s ":")
              (keyword (subs s 1))
              (keyword s)))))

(defn ->float [v]
  (cond
    (nil? v) nil
    (string? v) (when-not (str/blank? v)
                  (try
                    #?(:clj (Double/parseDouble v) :cljs (parse! js/parseFloat v))
                    (catch #?(:clj Exception :cljs :default) _
                      (throw (coerce-ex v "float")))))
    #?@(:clj [(char? v) (-> v str ->float)])
    #?@(:cljs [(js/isNaN v) nil])
    (integer? v) (double v)
    (#?(:clj float? :cljs number?) v) v
    (bigdec? v) #?(:clj (.doubleValue v) :cljs v)
    :else (throw (coerce-ex v "float"))))

(defn ->int [v]
  (cond
    (nil? v) nil
    (string? v) (when-not (str/blank? v)
                  (try
                    #?(:clj  (long (Double/parseDouble v))
                       :cljs (parse! js/parseInt v))
                    (catch #?(:clj Exception :cljs :default) _
                      (throw (coerce-ex v "int")))))
    (keyword? v) (throw (coerce-ex v "int"))
    #?@(:clj [(char? v) (-> v str ->int)])
    #?@(:cljs [(js/isNaN v) nil])
    (integer? v) v
    (#?(:clj float? :cljs number?) v) (long v)
    (bigdec? v) #?(:clj (.intValue v) :cljs v)
    :else (throw (coerce-ex v "int"))))

(defn ->bigdec [v]
  (cond
    (nil? v) nil
    (string? v) (when-not (str/blank? v)
                  (try
                    #?(:clj  (bigdec v)
                       :cljs (parse! js/parseFloat v))
                    (catch #?(:clj Exception :cljs :default) _
                      (throw (coerce-ex v "bigdec")))))
    #?@(:clj [(char? v) (-> v str ->bigdec)])
    #?@(:cljs [(js/isNaN v) nil])
    (integer? v) #?(:clj (bigdec v) :cljs (double v))
    (#?(:clj float? :cljs number?) v) #?(:clj (bigdec v) :cljs v)
    #?(:clj (bigdec? v)) #?(:clj v)
    :else (throw (coerce-ex v "bigdec"))))

(defn ->date [v]
  (cond
    (nil? v) nil
    (instance? #?(:clj java.util.Date :cljs js/Date) v) v
    (integer? v) (doto (new #?(:clj java.util.Date :cljs js/Date)) (.setTime v))
    #?(:cljs (instance? goog.date.Date v)) #?(:cljs (js/Date. (.getTime v)))
    (string? v) (cond
                  (str/blank? v) nil
                  (str/starts-with? v "#inst") (edn/read-string v)
                  :else (throw (coerce-ex v "date")))
    :else (throw (coerce-ex v "date"))))

(defn ->sql-date [v]
  (cond
    (nil? v) nil
    (instance? #?(:clj java.sql.Date :cljs js/Date) v) v
    #?(:clj (instance? java.util.Date v)) #?(:clj (java.sql.Date. (.getTime v)))
    (integer? v) #?(:clj (java.sql.Date. v) :cljs (doto (new js/Date) (.setTime v)))
    #?(:cljs (instance? goog.date.Date v)) #?(:cljs (js/Date. (.getTime v)))
    (string? v) (cond
                  (str/blank? v) nil
                  (str/starts-with? v "#inst") #?(:clj (java.sql.Date. (.getTime (edn/read-string v))) :cljs (edn/read-string v))
                  :else (throw (coerce-ex v "sql-date")))
    :else (throw (coerce-ex v "sql-date"))))

(defn ->timestamp [v]
  (cond
    (nil? v) nil
    (instance? #?(:bb java.util.Date :clj java.sql.Timestamp :cljs js/Date) v) v
    #?@(:bb  []
        :clj [(instance? java.util.Date v) (java.sql.Timestamp. (.getTime v))])
    (integer? v) #?(:bb   (java.util.Date. v)
                    :clj  (java.sql.Timestamp. v)
                    :cljs (doto (new js/Date) (.setTime v)))
    #?@(:cljs [(instance? goog.date.Date v) (js/Date. (.getTime v))])
    (string? v) (cond
                  (str/blank? v) nil
                  (str/starts-with? v "#inst")
                  (let [date (edn/read-string v)]
                    #?(:bb   date
                       :clj  (java.sql.Timestamp. (.getTime date))
                       :cljs date))
                  :else (throw (coerce-ex v "timestamp")))
    :else (throw (coerce-ex v "timestamp"))))

(defn ->uri [v]
  (cond
    (nil? v) nil
    #?@(:clj [(instance? java.net.URI v) v])
    (string? v) #?(:clj (java.net.URI/create v) :cljs v)
    :else (throw (coerce-ex v "uri"))))

(defn ->map [m]
  (cond (nil? m) m
        (map? m) m
        (sequential? m) (into {} m)
        :else (throw (coerce-ex m "map"))))

;; MDM : https://github.com/cognitect/transit-cljs/issues/41
#?(:cljs (extend-type com.cognitect.transit.types/UUID IUUID))

(defn ->uuid [v]
  (cond
    (nil? v) nil
    (uuid? v) v
    (string? v) #?(:clj (java.util.UUID/fromString v) :cljs (uuid v))
    :else (throw (coerce-ex v "uuid"))))

(defn multiple? [thing]
  (or (sequential? thing)
      (set? thing)))

(defn ->vec [v]
  (cond
    (nil? v) []
    (multiple? v) (vec v)
    :else [v]))

(defn ->seq [v]
  (cond
    (nil? v) []
    (multiple? v) v
    :else (list v)))
