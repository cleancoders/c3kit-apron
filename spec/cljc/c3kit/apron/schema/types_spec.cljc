(ns c3kit.apron.schema.types-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.types :as t]
    [c3kit.apron.schema.validators :as validators]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [context describe it should should= should-be-nil should-contain should-not-contain should-throw]]))

(def stdex
  #?(:clj  clojure.lang.ExceptionInfo
     :cljs cljs.core/ExceptionInfo))

(describe "schema.types"

  (context "default-types bundle"

    (it "exposes the scalar types"
      (should-contain :string  t/default-types)
      (should-contain :int     t/default-types)
      (should-contain :float   t/default-types)
      (should-contain :double  t/default-types)
      (should-contain :bigdec  t/default-types)
      (should-contain :boolean t/default-types)
      (should-contain :keyword t/default-types)
      (should-contain :uuid    t/default-types))

    (it "exposes the date-ish types"
      (should-contain :date      t/default-types)
      (should-contain :instant   t/default-types)
      (should-contain :timestamp t/default-types))

    (it "exposes the reference types"
      (should-contain :ref    t/default-types)
      (should-contain :kw-ref t/default-types))

    (it "exposes the structural types"
      (should-contain :seq    t/default-types)
      (should-contain :map    t/default-types)
      (should-contain :one-of t/default-types))

    (it "exposes the escape hatches"
      (should-contain :any    t/default-types)
      (should-contain :ignore t/default-types)
      (should-contain :fn     t/default-types))

    (it "non-trivial types declare :validations and/or :coercions"
      (let [non-structural (dissoc t/default-types :any :ignore :one-of :fn :seq)]
        (doseq [[name lex] non-structural]
          (should (seq (:validations lex)))
          (should (seq (:coercions lex))))))
    )

  (context "lexicon integration"

    (it "valid-types reflects the current lexicon"
      (let [vt (schema/valid-types)]
        (should-contain :string vt)
        (should-contain :int vt)
        (should-contain :one-of vt)))

    (it "validate-value! uses the type's :validations"
      (should= "hi" (schema/validate-value! {:type :string} "hi"))
      (should-throw stdex "must be a string"
                    (schema/validate-value! {:type :string} 42))
      (should= nil  (schema/validate-value! {:type :string} nil)))   ; nil allowed by default

    (it "coerce-value! uses the type's :coercions"
      (should= 42  (schema/coerce-value! {:type :int} "42"))
      (should= "x" (schema/coerce-value! {:type :string} "x")))

    (it "validate-value! throws on unknown type"
      (should-throw stdex "unhandled validation type: :nope"
                    (schema/validate-value! {:type :nope} "anything")))

    (it "coerce-value! throws on unknown type"
      (should-throw stdex "unhandled coercion type: :nope"
                    (schema/coerce-value! {:type :nope} "anything")))
    )

  (context "extending with custom types"

    (it "with-lexicon adds a new type for the scope of the body"
      (schema/with-lexicon {:types {:my-doubled {:validate (fn [v] (or (nil? v) (number? v)))
                                                 :coerce   #(* 2 %)}}}
        (should-contain :my-doubled (schema/valid-types))
        (should= 10 (schema/coerce-value! {:type :my-doubled} 5))
        (should= 42 (schema/validate-value! {:type :my-doubled} 42))))

    (it "outside the binding the custom type is gone"
      (schema/with-lexicon {:types {:my-temp {:validate (constantly true) :coerce identity}}}
        (should-contain :my-temp (schema/valid-types)))
      (should-not-contain :my-temp (schema/valid-types)))
    )

  (context "type :message fallback"

    (it ":string fields without :message report 'must be a string'"
      (should-throw stdex "must be a string"
                    (schema/validate-value! {:type :string} 42)))

    (it ":int fields without :message report 'must be an integer'"
      (should-throw stdex "must be an integer"
                    (schema/validate-value! {:type :int} "foo")))

    (it "type :message wins over spec :message (unified precedence)"
      (should-throw stdex "must be an integer"
                    (schema/validate-value! {:type :int :message "tell me a number!"} "foo")))
    )

  (context "type-bundled :validations"

    (it "validations declared on the type run after the type check"
      (schema/with-lexicon {:types {:positive-int {:validate (validators/nil?-or integer?)
                                                   :validations [[:>= 0]]
                                                   :coerce identity
                                                   :message "must be a positive integer"}}}
        (should= 5 (schema/validate-value! {:type :positive-int} 5))
        (should-throw stdex "must be >= 0"
                      (schema/validate-value! {:type :positive-int} -1))
        (should-throw stdex "must be a positive integer"
                      (schema/validate-value! {:type :positive-int} "five"))))

    (it "type :validations don't preempt user :validations"
      (schema/with-lexicon {:types {:my-int {:validate (validators/nil?-or integer?)
                                             :validations [[:>= 0]]
                                             :coerce identity}}}
        (should= 5 (schema/validate-value! {:type :my-int :validations [[:<= 10]]} 5))
        (should-throw stdex (schema/validate-value! {:type :my-int :validations [[:<= 10]]} 11))))
    )

  (context "type-bundled :coercions"

    (it "coercions declared on the type run before the type coerce"
      (schema/with-lexicon {:types {:trimmed-string {:validate (validators/nil?-or string?)
                                                     :coercions [:trim]
                                                     :coerce identity}}}
        (should= "hi" (schema/coerce-value! {:type :trimmed-string} "  hi  "))))

    (it "type :coercions run after user :coercions"
      (schema/with-lexicon {:types {:padded-string {:validate (validators/nil?-or string?)
                                                    :coercions [:upper-case]
                                                    :coerce identity}}}
        (should= "[X]"
                 (schema/coerce-value! {:type       :padded-string
                                        :coercions  [{:coerce #(str "[" % "]")}]} "x"))))
    )
  )
