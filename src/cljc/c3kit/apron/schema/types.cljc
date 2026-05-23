(ns c3kit.apron.schema.types
  "Standard type lexes. A type lex has shape

      {:validate    fn        ; required — the type predicate
       :coerce      fn        ; required — the type coercer
       :message     \"...\"     ; optional — fallback message when :validate fails
       :validations [...]     ; optional — type-bundled validations (run after :validate)
       :coercions   [...]}    ; optional — type-bundled coercions (run before :coerce)

   default-types bundles them as a {name → lex} map that
   c3kit.apron.schema merges into the default lexicon at its load.

   This namespace is pure data; it does not require c3kit.apron.schema.

   :one-of, :seq, and :map are structural types — c3kit.apron.schema's
   process-spec-on-value dispatches on them before the type lex's
   :validate / :coerce would run. They're included here so the lex
   names resolve and conform-schema! recognizes them as valid types."
  (:require
    [c3kit.apron.schema.coercers :as coercers]
    [c3kit.apron.schema.validators :as validators]))

(def default-types
  {:any       {:validate (constantly true)
               :coerce   identity}
   :bigdec    {:validate (validators/nil?-or coercers/bigdec?)
               :coerce   coercers/->bigdec
               :message  "must be a bigdec"}
   :boolean   {:validate (validators/nil?-or boolean?)
               :coerce   coercers/->boolean
               :message  "must be a boolean"}
   :date      {:validate (validators/nil?-or #?(:clj  #(instance? java.sql.Date %)
                                                :cljs #(instance? coercers/date %)))
               :coerce   coercers/->sql-date
               :message  "must be a date"}
   :double    {:validate (validators/nil?-or #?(:clj float? :cljs number?))
               :coerce   coercers/->float
               :message  "must be a double"}
   :float     {:validate (validators/nil?-or #?(:clj float? :cljs number?))
               :coerce   coercers/->float
               :message  "must be a float"}
   :fn        {:validate (validators/nil?-or ifn?)
               :coerce   identity
               :message  "must be a function"}
   :ignore    {:validate (constantly true)
               :coerce   identity}
   :instant   {:validate (validators/nil?-or #(instance? coercers/date %))
               :coerce   coercers/->date
               :message  "must be an instant"}
   :int       {:validate (validators/nil?-or integer?)
               :coerce   coercers/->int
               :message  "must be an integer"}
   :keyword   {:validate (validators/nil?-or keyword?)
               :coerce   coercers/->keyword
               :message  "must be a keyword"}
   :kw-ref    {:validate (validators/nil?-or keyword?)
               :coerce   coercers/->keyword
               :message  "must be a keyword"}
   :long      {:validate (validators/nil?-or integer?)
               :coerce   coercers/->int
               :message  "must be an integer"}
   :map       {:validate (validators/nil?-or map?)
               :coerce   coercers/->map
               :message  "must be a map"}
   :ref       {:validate (validators/nil?-or integer?)
               :coerce   coercers/->int
               :message  "must be an integer"}
   :seq       {:validate (validators/nil?-or coercers/multiple?)
               :coerce   identity
               :message  "must be a sequence"}
   :string    {:validate (validators/nil?-or string?)
               :coerce   coercers/->string
               :message  "must be a string"}
   :timestamp {:validate (validators/nil?-or #?(:clj  #(instance? java.sql.Timestamp %)
                                                :cljs #(instance? coercers/date %)))
               :coerce   coercers/->timestamp
               :message  "must be a timestamp"}
   :uri       {:validate (validators/nil?-or validators/uri?)
               :coerce   coercers/->uri
               :message  "must be a URI"}
   :uuid      {:validate (validators/nil?-or uuid?)
               :coerce   coercers/->uuid
               :message  "must be a UUID"}
   ;; structural — dispatched by c3kit.apron.schema/-process-spec-on-value
   :one-of    {:validate (constantly true)
               :coerce   identity}})
