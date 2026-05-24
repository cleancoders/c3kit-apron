(ns c3kit.apron.schema.types
  "Standard type lexes. A type lex has shape

      {:validations [...]   ; lex names / factory vectors / inline maps — run as type-level validation
       :coercions   [...]}  ; lex names / factory vectors / inline maps / bare fns — run as type-level coercion

   default-types bundles them as a {name → lex} map that
   c3kit.apron.schema merges into the default lexicon at its load.

   Because each entry in :validations and :coercions is itself a lex
   (with its own :message), failures report the specific failure
   instead of a generic 'is invalid'. Wrapping the type predicate in
   [:nil-or? :foo?] preserves the historical 'nil is always allowed
   for typed fields unless explicitly required' behavior; users who
   want strict non-nil checking add :present? to their spec's
   :validations.

   Note: default-types uses bare coercer fns (e.g. coercers/->int)
   rather than the named coercion lexes (e.g. :->int). Both work, but
   bare fns let coerce-ex's specific 'can't coerce VALUE to TYPE'
   message reach the user; a named lex would substitute the lex's
   generic 'could not coerce to int' message instead.

   This namespace is pure data; it does not require c3kit.apron.schema.

   :one-of, :seq, and :map are structural types — c3kit.apron.schema's
   process-spec-on-value dispatches on them before the type lex's
   validations / coercions would run. They're included here so the
   lex names resolve and conform-schema! recognizes them as valid
   types."
  (:require
    [c3kit.apron.schema.coercers :as coercers]))

(def default-types
  {:any       {}
   :ignore    {}
   :bigdec    {:validations [[:nil-or? :bigdec?]]
               :coercions   [coercers/->bigdec]}
   :boolean   {:validations [[:nil-or? :boolean?]]
               :coercions   [coercers/->boolean]}
   :date      {:validations [[:nil-or? {:validate #?(:clj  #(instance? java.sql.Date %)
                                                     :cljs #(instance? coercers/date %))
                                        :message  "must be a date"}]]
               :coercions   [coercers/->sql-date]}
   :double    {:validations [[:nil-or? :float?]]
               :coercions   [coercers/->float]}
   :float     {:validations [[:nil-or? :float?]]
               :coercions   [coercers/->float]}
   :fn        {:validations [[:nil-or? :ifn?]]}
   :instant   {:validations [[:nil-or? {:validate #(instance? coercers/date %)
                                        :message  "must be an instant"}]]
               :coercions   [coercers/->date]}
   :int       {:validations [[:nil-or? :integer?]]
               :coercions   [coercers/->int]}
   :keyword   {:validations [[:nil-or? :keyword?]]
               :coercions   [coercers/->keyword]}
   :kw-ref    {:validations [[:nil-or? :keyword?]]
               :coercions   [coercers/->keyword]}
   :long      {:validations [[:nil-or? :integer?]]
               :coercions   [coercers/->int]}
   :map       {:validations [[:nil-or? :map?]]
               :coercions   [coercers/->map]}
   :ref       {:validations [[:nil-or? :integer?]]
               :coercions   [coercers/->int]}
   :seq       {:validations [[:nil-or? :multiple?]]}
   :string    {:validations [[:nil-or? :string?]]
               :coercions   [coercers/->string]}
   :timestamp {:validations [[:nil-or? {:validate #?(:clj  #(instance? java.sql.Timestamp %)
                                                     :cljs #(instance? coercers/date %))
                                        :message  "must be a timestamp"}]]
               :coercions   [coercers/->timestamp]}
   :uri       {:validations [[:nil-or? :uri?]]
               :coercions   [coercers/->uri]}
   :uuid      {:validations [[:nil-or? :uuid?]]
               :coercions   [coercers/->uuid]}
   ;; structural — dispatched by c3kit.apron.schema/-process-spec-on-value
   :one-of    {}})
