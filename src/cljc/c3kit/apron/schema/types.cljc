(ns c3kit.apron.schema.types
  "Standard type lexes. A type lex has shape

      {:validations [...]   ; lex names / factory vectors / inline maps — run as type-level validation
       :coercions   [...]}  ; lex names / factory vectors / inline maps / bare fns — run as type-level coercion

   default-types bundles them as a {name → lex} map that
   c3kit.apron.schema merges into the default lexicon at its load.

   ---- shape choice for default types ----

   Default types' :validations use inline {:validate fn :message msg}
   maps rather than composed [:maybe? :foo?] lex references. The
   composed form reads more elegantly but costs a few lexicon lookups
   per validation pass. For shipped types on the hot path, the inline
   form is the right trade.

   Both shapes follow the same message precedence (entry's :message
   wins, spec :message is a fallback), so the choice between them is
   just about runtime cost vs. composition style.

   :coercions use bare coercer fns so that coerce-ex's specific
   'can't coerce VALUE to TYPE' message reaches the caller. Named
   coercion lexes there would substitute the lex's generic
   'could not coerce to int' message instead.

   User-defined types are free to use either form for either slot.

   ---- nil tolerance ----

   Default types allow nil by wrapping the type predicate in
   (validators/nil?-or ...). Users who want strict non-nil checking
   add :present? to their spec's :validations.

   ---- structural types ----

   :one-of, :seq, and :map are structural — c3kit.apron.schema's
   process-spec-on-value dispatches on them before the type lex's
   validations / coercions would run. They're included here so the
   lex names resolve and conform-schema! recognizes them as valid
   types.

   This namespace is pure data; it does not require c3kit.apron.schema."
  (:require
    [c3kit.apron.schema.coercers :as coercers]
    [c3kit.apron.schema.validators :as validators]))

(def default-types
  {:any       {}
   :ignore    {}
   :bigdec    {:validations [{:validate (validators/nil?-or coercers/bigdec?)
                              :message  "must be a bigdec"}]
               :coercions   [coercers/->bigdec]}
   :boolean   {:validations [{:validate (validators/nil?-or boolean?)
                              :message  "must be a boolean"}]
               :coercions   [coercers/->boolean]}
   :date      {:validations [{:validate (validators/nil?-or
                                          #?(:clj  #(instance? java.sql.Date %)
                                             :cljs #(instance? coercers/date %)))
                              :message  "must be a date"}]
               :coercions   [coercers/->sql-date]}
   :double    {:validations [{:validate (validators/nil?-or
                                          #?(:clj clojure.core/float? :cljs number?))
                              :message  "must be a double"}]
               :coercions   [coercers/->float]}
   :float     {:validations [{:validate (validators/nil?-or
                                          #?(:clj clojure.core/float? :cljs number?))
                              :message  "must be a float"}]
               :coercions   [coercers/->float]}
   :fn        {:validations [{:validate (validators/nil?-or ifn?)
                              :message  "must be a function"}]}
   :instant   {:validations [{:validate (validators/nil?-or #(instance? coercers/date %))
                              :message  "must be an instant"}]
               :coercions   [coercers/->date]}
   :int       {:validations [{:validate (validators/nil?-or integer?)
                              :message  "must be an integer"}]
               :coercions   [coercers/->int]}
   :keyword   {:validations [{:validate (validators/nil?-or keyword?)
                              :message  "must be a keyword"}]
               :coercions   [coercers/->keyword]}
   :kw-ref    {:validations [{:validate (validators/nil?-or keyword?)
                              :message  "must be a keyword"}]
               :coercions   [coercers/->keyword]}
   :long      {:validations [{:validate (validators/nil?-or integer?)
                              :message  "must be an integer"}]
               :coercions   [coercers/->int]}
   :map       {:validations [{:validate (validators/nil?-or map?)
                              :message  "must be a map"}]
               :coercions   [coercers/->map]}
   :ref       {:validations [{:validate (validators/nil?-or integer?)
                              :message  "must be an integer"}]
               :coercions   [coercers/->int]}
   :seq       {:validations [{:validate (validators/nil?-or coercers/multiple?)
                              :message  "must be a sequence"}]}
   :string    {:validations [{:validate (validators/nil?-or string?)
                              :message  "must be a string"}]
               :coercions   [coercers/->string]}
   :timestamp {:validations [{:validate (validators/nil?-or
                                          #?(:clj  #(instance? java.sql.Timestamp %)
                                             :cljs #(instance? coercers/date %)))
                              :message  "must be a timestamp"}]
               :coercions   [coercers/->timestamp]}
   :uri       {:validations [{:validate (validators/nil?-or validators/uri?)
                              :message  "must be a URI"}]
               :coercions   [coercers/->uri]}
   :uuid      {:validations [{:validate (validators/nil?-or uuid?)
                              :message  "must be a UUID"}]
               :coercions   [coercers/->uuid]}
   ;; structural — dispatched by c3kit.apron.schema/-process-spec-on-value
   :one-of    {}})
