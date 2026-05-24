(ns c3kit.apron.schema.types
  "Standard type lexes. A type lex has shape

      {:validations [...]   ; lex names / factory vectors / inline maps — run as type-level validation
       :coercions   [...]}  ; lex names / factory vectors / inline maps / bare fns — run as type-level coercion

   default-types bundles them as a {name → lex} map that
   c3kit.apron.schema merges into the default lexicon at its load.

   ---- shape choice for default types ----

   Default types' :validations use the lex form [:maybe? :foo?] rather
   than inline {:validate fn :message msg} maps. The composed form
   costs a few lexicon lookups per validation, but it's the form that
   preserves 'spec :message overrides type message' semantics: in
   -normalize-validation-entry, vector entries go through default-m =
   spec's :message, while inline-map entries treat the map's own
   :message as authoritative. We want the user's spec :message to win
   when they provide one (e.g. {:species {:type :string :message
   \"must be a pet species\"}}) and fall back to the type's message
   otherwise. The lex form gives us both.

   :coercions use bare coercer fns instead, so coerce-ex's specific
   'can't coerce VALUE to TYPE' message reaches the caller. Using
   named coercion lexes there would substitute the lex's generic
   'could not coerce to int' message instead.

   User-defined types are free to use either form for either slot.

   ---- nil tolerance ----

   Default types allow nil by wrapping the type predicate in :maybe?
   (which delegates to (some-fn nil? f) with the inner pred's message
   intact). Users who want strict non-nil checking add :present? to
   their spec's :validations.

   ---- structural types ----

   :one-of, :seq, and :map are structural — c3kit.apron.schema's
   process-spec-on-value dispatches on them before the type lex's
   validations / coercions would run. They're included here so the
   lex names resolve and conform-schema! recognizes them as valid
   types.

   This namespace is pure data; it does not require c3kit.apron.schema."
  (:require
    [c3kit.apron.schema.coercers :as coercers]))

(def default-types
  {:any       {}
   :ignore    {}
   :bigdec    {:validations [[:maybe? :bigdec?]]
               :coercions   [coercers/->bigdec]}
   :boolean   {:validations [[:maybe? :boolean?]]
               :coercions   [coercers/->boolean]}
   :date      {:validations [[:maybe? {:validate #?(:clj  #(instance? java.sql.Date %)
                                                    :cljs #(instance? coercers/date %))
                                       :message  "must be a date"}]]
               :coercions   [coercers/->sql-date]}
   :double    {:validations [[:maybe? :float?]]
               :coercions   [coercers/->float]}
   :float     {:validations [[:maybe? :float?]]
               :coercions   [coercers/->float]}
   :fn        {:validations [[:maybe? :ifn?]]}
   :instant   {:validations [[:maybe? {:validate #(instance? coercers/date %)
                                       :message  "must be an instant"}]]
               :coercions   [coercers/->date]}
   :int       {:validations [[:maybe? :integer?]]
               :coercions   [coercers/->int]}
   :keyword   {:validations [[:maybe? :keyword?]]
               :coercions   [coercers/->keyword]}
   :kw-ref    {:validations [[:maybe? :keyword?]]
               :coercions   [coercers/->keyword]}
   :long      {:validations [[:maybe? :integer?]]
               :coercions   [coercers/->int]}
   :map       {:validations [[:maybe? :map?]]
               :coercions   [coercers/->map]}
   :ref       {:validations [[:maybe? :integer?]]
               :coercions   [coercers/->int]}
   :seq       {:validations [[:maybe? :multiple?]]}
   :string    {:validations [[:maybe? :string?]]
               :coercions   [coercers/->string]}
   :timestamp {:validations [[:maybe? {:validate #?(:clj  #(instance? java.sql.Timestamp %)
                                                    :cljs #(instance? coercers/date %))
                                       :message  "must be a timestamp"}]]
               :coercions   [coercers/->timestamp]}
   :uri       {:validations [[:maybe? :uri?]]
               :coercions   [coercers/->uri]}
   :uuid      {:validations [[:maybe? :uuid?]]
               :coercions   [coercers/->uuid]}
   ;; structural — dispatched by c3kit.apron.schema/-process-spec-on-value
   :one-of    {}})
