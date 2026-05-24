(ns c3kit.apron.schema.validators
  "Pure validation helpers — predicates, combinator factories, and the
   minimal building blocks used by both c3kit.apron.schema and
   c3kit.apron.schema.validations. Owns no lexicon state; combinators
   call out through *validation-resolver*, which c3kit.apron.schema
   populates at load time, to avoid a require cycle.

   Named with the plural suffix to avoid the CLJS namespace/var clash
   with c3kit.apron.schema/validate (the entity-level public fn)."
  (:refer-clojure :exclude [uri?])
  (:require
    [c3kit.apron.corec :as ccc]
    [clojure.string :as str]))

(def ^:dynamic *validation-resolver*
  "Set by c3kit.apron.schema on load via set-validation-resolver!. A function
   that takes a validation lex name (keyword/symbol/string or factory vector
   [name & args]) and returns the resolved lex map. Combinator factories use
   it to handle name-form arguments without depending on schema's lookup
   machinery."
  nil)

(defn set-validation-resolver!
  "Installs the resolver fn used by combinator factories to look up validation
   lex entries by name. c3kit.apron.schema calls this once at load time."
  [f]
  #?(:clj  (alter-var-root #'*validation-resolver* (constantly f))
     :cljs (set! *validation-resolver* f)))

;; ---------- predicates

(defn present? [v]
  (not (or (nil? v)
           (and (clojure.core/string? v) (str/blank? v)))))

(defn nil?-or [f] (some-fn nil? f))

(def email-pattern #"[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")

(defn email? [value] (boolean (re-matches email-pattern value)))

(defn bigdec? [v] #?(:clj (instance? BigDecimal v) :cljs (number? v)))

(defn uri? [value]
  #?(:clj  (instance? java.net.URI value)
     :cljs (clojure.core/string? value)))

(defn is-enum? [enum]
  (let [enum-name (name (:enum enum))
        enum-set  (ccc/map-set #(keyword enum-name (name %)) (:values enum))]
    (fn [value]
      (or (nil? value)
          (contains? enum-set value)))))

;; ---------- combinator helpers

(defn ->validate-fn
  "Resolves v to a validate fn. v may be an inline fn (returned as-is),
   an inline lex map (:validate extracted), or a lex name (resolved via
   *validation-resolver*)."
  [v]
  (cond
    (fn? v)  v
    (map? v) (:validate v)
    :else    (:validate (*validation-resolver* v))))

(defn- -inner-msg [pred]
  (cond
    (fn? pred)  nil
    (map? pred) (:message pred)
    :else       (try (:message (*validation-resolver* pred))
                     (catch #?(:clj Exception :cljs :default) _ nil))))

;; ---------- combinator factories

(defn nil-or? [pred]
  (let [f   (->validate-fn pred)
        msg (-inner-msg pred)]
    {:validate (some-fn nil? f)
     :message  (or msg "is invalid")}))

(defn not? [pred]
  (let [f   (->validate-fn pred)
        msg (-inner-msg pred)]
    {:validate (complement f)
     :message  (if msg (str "must not: " msg) "is invalid")}))

(defn and? [& preds]
  (let [fs   (mapv ->validate-fn preds)
        msgs (keep -inner-msg preds)]
    {:validate (apply every-pred fs)
     :message  (if (seq msgs) (str/join " and " msgs) "is invalid")}))

(defn or? [& preds]
  (let [fs   (mapv ->validate-fn preds)
        msgs (keep -inner-msg preds)]
    {:validate (apply some-fn fs)
     :message  (if (seq msgs) (str/join " or " msgs) "is invalid")}))
