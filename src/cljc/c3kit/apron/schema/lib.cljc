(ns c3kit.apron.schema.lib
  "Standard catalog of validators and coercers. Each ref is its own var
   for à-la-carte use; install! drains the catalog into c3kit.apron.schema's
   *ref-registry*."
  (:refer-clojure :exclude [string? integer? keyword? number? boolean? map?
                            pos? neg? zero? pos-int? neg-int? nat-int?
                            > < >= <= = not=])
  #?(:cljs (:require-macros [c3kit.apron.schema.lib :refer [defref]]))
  (:require
    [c3kit.apron.schema :as s]
    [clojure.string :as str]))

(def -catalog (atom []))

#?(:clj
   (defmacro defref [sym body]
     `(let [v# (with-meta ~body {:key ~(keyword sym)})]
        (def ~sym v#)
        (swap! -catalog conj v#)
        v#)))

;; ---------- type predicates

(defref string?  {:validate clojure.core/string?  :message "must be a string"})
(defref integer? {:validate clojure.core/integer? :message "must be an integer"})
(defref keyword? {:validate clojure.core/keyword? :message "must be a keyword"})
(defref number?  {:validate clojure.core/number?  :message "must be a number"})
(defref boolean? {:validate clojure.core/boolean? :message "must be a boolean"})
(defref map?     {:validate clojure.core/map?     :message "must be a map"})

;; ---------- numeric predicates

(defref pos?     {:validate clojure.core/pos?     :message "must be positive"})
(defref neg?     {:validate clojure.core/neg?     :message "must be negative"})
(defref zero?    {:validate clojure.core/zero?    :message "must be zero"})
(defref pos-int? {:validate clojure.core/pos-int? :message "must be a positive integer"})
(defref neg-int? {:validate clojure.core/neg-int? :message "must be a negative integer"})
(defref nat-int? {:validate clojure.core/nat-int? :message "must be a non-negative integer"})

;; ---------- apron predicates

(defref present? {:validate s/present? :message "is required"})
(defref email?   {:validate s/email?   :message "must be a valid email"})
(defref bigdec?  {:validate s/bigdec?  :message "must be a bigdec"})
(defref uri?     {:validate s/uri?     :message "must be a URI"})

;; ---------- comparison factories

(defref >    (fn [n] {:validate #(clojure.core/> % n)  :message (str "must be > " n)}))
(defref <    (fn [n] {:validate #(clojure.core/< % n)  :message (str "must be < " n)}))
(defref >=   (fn [n] {:validate #(clojure.core/>= % n) :message (str "must be >= " n)}))
(defref <=   (fn [n] {:validate #(clojure.core/<= % n) :message (str "must be <= " n)}))
(defref =    (fn [x] {:validate #(clojure.core/= % x)  :message (str "must = " x)}))
(defref not= (fn [x] {:validate #(clojure.core/not= % x) :message (str "must not = " x)}))

(defref between (fn [lo hi]
                  {:validate #(clojure.core/<= lo % hi)
                   :message  (str "must be between " lo " and " hi)}))

;; ---------- shape factories

(defref min-length (fn [n] {:validate #(clojure.core/<= n (count %))
                            :message  (str "must be at least " n " characters")}))
(defref max-length (fn [n] {:validate #(clojure.core/<= (count %) n)
                            :message  (str "must be at most " n " characters")}))
(defref length     (fn [n] {:validate #(clojure.core/= n (count %))
                            :message  (str "must be exactly " n " characters")}))
(defref matches    (fn [re] {:validate #(boolean (re-find re %))
                             :message  (str "must match " (str re))}))
(defref one-of     (fn [& xs] {:validate (set xs)
                               :message  (str "must be one of " (vec xs))}))
(defref not-one-of (fn [& xs] {:validate (complement (set xs))
                               :message  (str "must not be one of " (vec xs))}))

;; ---------- string coercers

(defref trim       {:coerce str/trim       :message "must be a string"})
(defref upper-case {:coerce str/upper-case :message "must be a string"})
(defref lower-case {:coerce str/lower-case :message "must be a string"})
(defref capitalize {:coerce str/capitalize :message "must be a string"})

;; ---------- type coercers

(defref ->string    {:coerce s/->string    :message "could not coerce to string"})
(defref ->int       {:coerce s/->int       :message "could not coerce to int"})
(defref ->float     {:coerce s/->float     :message "could not coerce to float"})
(defref ->bigdec    {:coerce s/->bigdec    :message "could not coerce to bigdec"})
(defref ->boolean   {:coerce s/->boolean   :message "could not coerce to boolean"})
(defref ->keyword   {:coerce s/->keyword   :message "could not coerce to keyword"})
(defref ->date      {:coerce s/->date      :message "could not coerce to date"})
(defref ->sql-date  {:coerce s/->sql-date  :message "could not coerce to sql-date"})
(defref ->timestamp {:coerce s/->timestamp :message "could not coerce to timestamp"})
(defref ->uri       {:coerce s/->uri       :message "could not coerce to URI"})
(defref ->uuid      {:coerce s/->uuid      :message "could not coerce to UUID"})

;; ---------- coercion factories

(defref default (fn [v] {:coerce #(if (nil? %) v %)}))

(defn install! []
  (run! s/register-ref! @-catalog))
