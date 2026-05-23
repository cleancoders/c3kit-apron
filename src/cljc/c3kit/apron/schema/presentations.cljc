(ns c3kit.apron.schema.presentations
  "Standard presentation lexes. Each lex is its own var for à-la-carte
   use; default-presentations bundles them as a {name → lex} map that
   c3kit.apron.schema merges into the default lexicon at its load.
   This namespace is pure data — it does not require c3kit.apron.schema.

   A presentation lex has the shape {:present fn} — no :message, since
   presentations don't fail in the lex-error sense; they just transform.
   If a present-fn throws, that's a bug at the call site; exceptions
   propagate rather than being captured as field errors."
  (:require
    [c3kit.apron.schema.coercers :as coercers]
    [clojure.string :as str]))

;; ---------- string transforms (same fns as coercers, in :present slot)

(def trim       {:present str/trim})
(def upper-case {:present str/upper-case})
(def lower-case {:present str/lower-case})
(def capitalize {:present str/capitalize})

;; ---------- type display

(def ->string {:present coercers/->string})

;; ---------- omit (drop the field from output)

(def omit {:present (constantly nil)})

;; ---------- factories

(defn default
  "Returns a presentation lex that substitutes `v` whenever the value is nil.
   E.g. (default \"—\") shows an em-dash for missing values."
  [v]
  {:present #(if (nil? %) v %)})

;; ---------- lexicon bundle

(def default-presentations
  {:trim       trim
   :upper-case upper-case
   :lower-case lower-case
   :capitalize capitalize
   :->string   ->string
   :omit       omit
   :default    default})
