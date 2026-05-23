(ns c3kit.apron.schema.types
  "Standard type lexes. A type lex has shape {:validate fn :coerce fn} —
   the predicate that decides whether a value belongs to the type, and
   the coercer that pulls compatible input into the type. default-types
   bundles them as a {name → lex} map that c3kit.apron.schema merges
   into the default lexicon at its load.

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
               :coerce   coercers/->bigdec}
   :boolean   {:validate (validators/nil?-or boolean?)
               :coerce   coercers/->boolean}
   :date      {:validate (validators/nil?-or #?(:clj  #(instance? java.sql.Date %)
                                                :cljs #(instance? coercers/date %)))
               :coerce   coercers/->sql-date}
   :double    {:validate (validators/nil?-or #?(:clj float? :cljs number?))
               :coerce   coercers/->float}
   :float     {:validate (validators/nil?-or #?(:clj float? :cljs number?))
               :coerce   coercers/->float}
   :fn        {:validate (validators/nil?-or ifn?)
               :coerce   identity}
   :ignore    {:validate (constantly true)
               :coerce   identity}
   :instant   {:validate (validators/nil?-or #(instance? coercers/date %))
               :coerce   coercers/->date}
   :int       {:validate (validators/nil?-or integer?)
               :coerce   coercers/->int}
   :keyword   {:validate (validators/nil?-or keyword?)
               :coerce   coercers/->keyword}
   :kw-ref    {:validate (validators/nil?-or keyword?)
               :coerce   coercers/->keyword}
   :long      {:validate (validators/nil?-or integer?)
               :coerce   coercers/->int}
   :map       {:validate (validators/nil?-or map?)
               :coerce   coercers/->map}
   :ref       {:validate (validators/nil?-or integer?)
               :coerce   coercers/->int}
   :seq       {:validate (validators/nil?-or coercers/multiple?)
               :coerce   identity}
   :string    {:validate (validators/nil?-or string?)
               :coerce   coercers/->string}
   :timestamp {:validate (validators/nil?-or #?(:clj  #(instance? java.sql.Timestamp %)
                                                :cljs #(instance? coercers/date %)))
               :coerce   coercers/->timestamp}
   :uri       {:validate (validators/nil?-or validators/uri?)
               :coerce   coercers/->uri}
   :uuid      {:validate (validators/nil?-or uuid?)
               :coerce   coercers/->uuid}
   ;; structural — dispatched by c3kit.apron.schema/-process-spec-on-value
   :one-of    {:validate (constantly true)
               :coerce   identity}})
