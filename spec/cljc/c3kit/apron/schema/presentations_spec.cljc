(ns c3kit.apron.schema.presentations-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.presentations :as p]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should= should-be-nil]]))

(describe "schema.presentations"

  (context "string transforms"

    (it ":trim"
      (should= "foo" ((:present p/trim) "  foo  ")))

    (it ":upper-case"
      (should= "FOO" ((:present p/upper-case) "foo")))

    (it ":lower-case"
      (should= "foo" ((:present p/lower-case) "FOO")))

    (it ":capitalize"
      (should= "Foo" ((:present p/capitalize) "foo")))
    )

  (context "type display"

    (it ":->string"
      (should= "42" ((:present p/->string) 42))
      (should= ":foo" ((:present p/->string) :foo))
      (should-be-nil ((:present p/->string) nil)))
    )

  (context ":omit"

    (it "returns nil for any input"
      (should-be-nil ((:present p/omit) "anything"))
      (should-be-nil ((:present p/omit) 123))
      (should-be-nil ((:present p/omit) nil)))
    )

  (context ":default factory"

    (it "substitutes the value when input is nil"
      (let [r (p/default "—")]
        (should= "—" ((:present r) nil))
        (should= "abc" ((:present r) "abc"))
        (should= 0 ((:present r) 0))))
    )

  (context "default-presentations bundle"

    (it "exposes string transforms by name"
      (should= p/trim (:trim p/default-presentations))
      (should= p/upper-case (:upper-case p/default-presentations))
      (should= p/lower-case (:lower-case p/default-presentations))
      (should= p/capitalize (:capitalize p/default-presentations)))

    (it "exposes type display by name"
      (should= p/->string (:->string p/default-presentations)))

    (it "exposes :omit and :default"
      (should= p/omit (:omit p/default-presentations))
      (should= p/default (:default p/default-presentations)))
    )

  (context "lexicon integration"

    (it "default lexes are reachable through present-value!"
      (should= "HI" (schema/present-value! {:type :string :presentations [:upper-case]} "hi"))
      (should= "hi" (schema/present-value! {:type :string :presentations [:trim]} "  hi  "))
      (should= "42" (schema/present-value! {:type :any   :presentations [:->string]} 42))
      (should-be-nil (schema/present-value! {:type :any  :presentations [:omit]} "anything")))

    (it "factory form invokes :default with arg"
      (should= "—" (schema/present-value! {:type :any :presentations [[:default "—"]]} nil))
      (should= "x" (schema/present-value! {:type :any :presentations [[:default "—"]]} "x")))

    (it "presentations compose in order"
      (should= "HI" (schema/present-value! {:type :string :presentations [:trim :upper-case]} "  hi  ")))
    )
  )
