(ns c3kit.apron.schema.validations
  "Standard validation lexes for the schema lexicon. Each lex is its own
   var for à-la-carte use; default-validations bundles them and is
   merged into (:validations c3kit.apron.schema/*lexicon*) at namespace
   load via update-lexicon!. Requiring this namespace is the opt-in for
   the built-in validation vocabulary."
  (:refer-clojure :exclude [string? integer? keyword? number? boolean? map?
                            pos? neg? zero? pos-int? neg-int? nat-int? uri?
                            > < >= <= = not=])
  (:require
    [c3kit.apron.schema :as s]
    [clojure.string :as str]))

;; ---------- type predicates

(def string?  {:validate clojure.core/string?  :message "must be a string"})
(def integer? {:validate clojure.core/integer? :message "must be an integer"})
(def keyword? {:validate clojure.core/keyword? :message "must be a keyword"})
(def number?  {:validate clojure.core/number?  :message "must be a number"})
(def boolean? {:validate clojure.core/boolean? :message "must be a boolean"})
(def map?     {:validate clojure.core/map?     :message "must be a map"})

;; ---------- numeric predicates

(def pos?     {:validate clojure.core/pos?     :message "must be positive"})
(def neg?     {:validate clojure.core/neg?     :message "must be negative"})
(def zero?    {:validate clojure.core/zero?    :message "must be zero"})
(def pos-int? {:validate clojure.core/pos-int? :message "must be a positive integer"})
(def neg-int? {:validate clojure.core/neg-int? :message "must be a negative integer"})
(def nat-int? {:validate clojure.core/nat-int? :message "must be a non-negative integer"})

;; ---------- apron predicates

(def present? {:validate s/present? :message "is required"})
(def email?   {:validate s/email?   :message "must be a valid email"})
(def bigdec?  {:validate s/bigdec?  :message "must be a bigdec"})
(def uri?     {:validate s/uri?     :message "must be a URI"})

;; ---------- comparison factories

(defn >    [n] {:validate #(clojure.core/> % n)  :message (str "must be > " n)})
(defn <    [n] {:validate #(clojure.core/< % n)  :message (str "must be < " n)})
(defn >=   [n] {:validate #(clojure.core/>= % n) :message (str "must be >= " n)})
(defn <=   [n] {:validate #(clojure.core/<= % n) :message (str "must be <= " n)})
(defn =    [x] {:validate #(clojure.core/= % x)  :message (str "must = " x)})
(defn not= [x] {:validate #(clojure.core/not= % x) :message (str "must not = " x)})

(defn between [lo hi]
  {:validate #(clojure.core/<= lo % hi)
   :message  (str "must be between " lo " and " hi)})

;; ---------- shape factories

(defn min-length [n]
  {:validate #(clojure.core/<= n (count %))
   :message  (str "must be at least " n " characters")})

(defn max-length [n]
  {:validate #(clojure.core/<= (count %) n)
   :message  (str "must be at most " n " characters")})

(defn length [n]
  {:validate #(clojure.core/= n (count %))
   :message  (str "must be exactly " n " characters")})

(defn matches [re]
  {:validate #(boolean (re-find re %))
   :message  (str "must match " (str re))})

(defn one-of [& xs]
  {:validate (set xs)
   :message  (str "must be one of " (vec xs))})

(defn not-one-of [& xs]
  {:validate (complement (set xs))
   :message  (str "must not be one of " (vec xs))})

;; ---------- combinator factories

(defn- -inner-msg [pred]
  (when-not (fn? pred)
    (try (:message (s/lex! :validations pred))
         (catch #?(:clj Exception :cljs :default) _ nil))))

(defn nil-or? [pred]
  (let [f   (s/->validate-fn pred)
        msg (-inner-msg pred)]
    {:validate (some-fn nil? f)
     :message  (if msg (str "may be nil or " msg) "is invalid")}))

(defn not? [pred]
  (let [f   (s/->validate-fn pred)
        msg (-inner-msg pred)]
    {:validate (complement f)
     :message  (if msg (str "must not: " msg) "is invalid")}))

(defn and? [& preds]
  (let [fs   (mapv s/->validate-fn preds)
        msgs (keep -inner-msg preds)]
    {:validate (apply every-pred fs)
     :message  (if (seq msgs) (str/join " and " msgs) "is invalid")}))

(defn or? [& preds]
  (let [fs   (mapv s/->validate-fn preds)
        msgs (keep -inner-msg preds)]
    {:validate (apply some-fn fs)
     :message  (if (seq msgs) (str/join " or " msgs) "is invalid")}))

;; ---------- lexicon registration

(def default-validations
  {:string?    string?
   :integer?   integer?
   :keyword?   keyword?
   :number?    number?
   :boolean?   boolean?
   :map?       map?
   :pos?       pos?
   :neg?       neg?
   :zero?      zero?
   :pos-int?   pos-int?
   :neg-int?   neg-int?
   :nat-int?   nat-int?
   :present?   present?
   :email?     email?
   :bigdec?    bigdec?
   :uri?       uri?
   :>          >
   :<          <
   :>=         >=
   :<=         <=
   :=          =
   :not=       not=
   :between    between
   :min-length min-length
   :max-length max-length
   :length     length
   :matches    matches
   :one-of     one-of
   :not-one-of not-one-of
   :nil-or?    nil-or?
   :not?       not?
   :and?       and?
   :or?        or?})

(s/update-lexicon! :validations merge default-validations)
