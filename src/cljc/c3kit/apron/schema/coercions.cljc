(ns c3kit.apron.schema.coercions
  "Standard coercion lexes for the schema lexicon. Each lex is its own
   var for à-la-carte use; default-coercions bundles them and is merged
   into (:coercions c3kit.apron.schema/*lexicon*) at namespace load via
   update-lexicon!. Requiring this namespace is the opt-in for the
   built-in coercion vocabulary."
  (:require
    [c3kit.apron.schema :as s]
    [clojure.string :as str]))

;; ---------- string coercers

(def trim       {:coerce str/trim       :message "must be a string"})
(def upper-case {:coerce str/upper-case :message "must be a string"})
(def lower-case {:coerce str/lower-case :message "must be a string"})
(def capitalize {:coerce str/capitalize :message "must be a string"})

;; ---------- type coercers

(def ->string    {:coerce s/->string    :message "could not coerce to string"})
(def ->int       {:coerce s/->int       :message "could not coerce to int"})
(def ->float     {:coerce s/->float     :message "could not coerce to float"})
(def ->bigdec    {:coerce s/->bigdec    :message "could not coerce to bigdec"})
(def ->boolean   {:coerce s/->boolean   :message "could not coerce to boolean"})
(def ->keyword   {:coerce s/->keyword   :message "could not coerce to keyword"})
(def ->date      {:coerce s/->date      :message "could not coerce to date"})
(def ->sql-date  {:coerce s/->sql-date  :message "could not coerce to sql-date"})
(def ->timestamp {:coerce s/->timestamp :message "could not coerce to timestamp"})
(def ->uri       {:coerce s/->uri       :message "could not coerce to URI"})
(def ->uuid      {:coerce s/->uuid      :message "could not coerce to UUID"})

;; ---------- coercion factories

(defn default [v] {:coerce #(if (nil? %) v %)})

;; ---------- lexicon registration

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

(s/update-lexicon! :coercions merge default-coercions)
