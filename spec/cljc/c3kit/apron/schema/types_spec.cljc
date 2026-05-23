(ns c3kit.apron.schema.types-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.types :as t]
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

    (it "each entry has both :validate and :coerce"
      (doseq [[name lex] t/default-types]
        (should (some? (:validate lex)))
        (should (some? (:coerce lex)))))
    )

  (context "lexicon integration"

    (it "valid-types reflects the current lexicon"
      (let [vt (schema/valid-types)]
        (should-contain :string vt)
        (should-contain :int vt)
        (should-contain :one-of vt)))

    (it "type-validator! reads from the :types slot"
      (should     ((schema/type-validator! :string) "hi"))
      (should     ((schema/type-validator! :string) nil))   ; nil allowed by default
      (should= false (boolean ((schema/type-validator! :string) 42))))

    (it "type-coercer! reads from the :types slot"
      (should= 42  ((schema/type-coercer! :int) "42"))
      (should= "x" ((schema/type-coercer! :string) "x")))

    (it "type-validator! throws on unknown type"
      (should-throw stdex "unhandled validation type: :nope"
                    (schema/type-validator! :nope)))

    (it "type-coercer! throws on unknown type"
      (should-throw stdex "unhandled coercion type: :nope"
                    (schema/type-coercer! :nope)))
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
  )
