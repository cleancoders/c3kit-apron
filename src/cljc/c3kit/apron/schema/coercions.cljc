(ns c3kit.apron.schema.coercions
  "Standard coercion lexes. Each lex is its own var for à-la-carte use;
   default-coercions bundles them as a {name → lex} map that
   c3kit.apron.schema merges into the default lexicon at its load.
   This namespace is pure data — it does not require c3kit.apron.schema."
  (:require
    [c3kit.apron.schema.coercers :as coercers]
    [clojure.string :as str]))

;; ---------- string coercers

(def trim       {:coerce str/trim       :message "must be a string"})
(def upper-case {:coerce str/upper-case :message "must be a string"})
(def lower-case {:coerce str/lower-case :message "must be a string"})
(def capitalize {:coerce str/capitalize :message "must be a string"})

;; ---------- type coercers

(def ->string    {:coerce coercers/->string    :message "could not coerce to string"})
(def ->int       {:coerce coercers/->int       :message "could not coerce to int"})
(def ->float     {:coerce coercers/->float     :message "could not coerce to float"})
(def ->bigdec    {:coerce coercers/->bigdec    :message "could not coerce to bigdec"})
(def ->boolean   {:coerce coercers/->boolean   :message "could not coerce to boolean"})
(def ->keyword   {:coerce coercers/->keyword   :message "could not coerce to keyword"})
(def ->date      {:coerce coercers/->date      :message "could not coerce to date"})
(def ->sql-date  {:coerce coercers/->sql-date  :message "could not coerce to sql-date"})
(def ->timestamp {:coerce coercers/->timestamp :message "could not coerce to timestamp"})
(def ->uri       {:coerce coercers/->uri       :message "could not coerce to URI"})
(def ->uuid      {:coerce coercers/->uuid      :message "could not coerce to UUID"})

;; ---------- coercion factories

(defn default [v] {:coerce #(if (nil? %) v %)})

;; ---------- lexicon bundle

(def default-coercions
  {:trim        trim
   :upper-case  upper-case
   :lower-case  lower-case
   :capitalize  capitalize
   :->string    ->string
   :->int       ->int
   :->float     ->float
   :->bigdec    ->bigdec
   :->boolean   ->boolean
   :->keyword   ->keyword
   :->date      ->date
   :->sql-date  ->sql-date
   :->timestamp ->timestamp
   :->uri       ->uri
   :->uuid      ->uuid
   :default     default})
