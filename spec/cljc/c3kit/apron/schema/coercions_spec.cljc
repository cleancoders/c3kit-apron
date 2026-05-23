(ns c3kit.apron.schema.coercions-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema-spec :refer [stdex]]
    [c3kit.apron.schema.coercions :as c]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [context describe it should= should-not should-throw]]))

(describe "schema.coercions"

  (context "string coercers"

    (it ":trim"
      (should= "foo" ((:coerce c/trim) "  foo  "))
      (should= "must be a string" (:message c/trim)))

    (it ":upper-case"
      (should= "FOO" ((:coerce c/upper-case) "foo"))
      (should= "must be a string" (:message c/upper-case)))

    (it ":lower-case"
      (should= "foo" ((:coerce c/lower-case) "FOO"))
      (should= "must be a string" (:message c/lower-case)))

    (it ":capitalize"
      (should= "Foo" ((:coerce c/capitalize) "foo"))
      (should= "must be a string" (:message c/capitalize)))
    )

  (context "type coercers"

    (it ":->string"
      (should= "42" ((:coerce c/->string) 42))
      (should= "could not coerce to string" (:message c/->string)))

    (it ":->int"
      (should= 42 ((:coerce c/->int) "42"))
      (should= 42 ((:coerce c/->int) 42.9))
      (should= "could not coerce to int" (:message c/->int)))

    (it ":->float"
      (should= 3.14 ((:coerce c/->float) "3.14"))
      (should= "could not coerce to float" (:message c/->float)))

    (it ":->keyword"
      (should= :foo ((:coerce c/->keyword) "foo"))
      (should= "could not coerce to keyword" (:message c/->keyword)))

    (it ":->boolean"
      (should= true  ((:coerce c/->boolean) "true"))
      (should= false ((:coerce c/->boolean) "false"))
      (should= "could not coerce to boolean" (:message c/->boolean)))
    )

  (context "coercion factories"

    (it ":default supplies a value when nil"
      (let [r (c/default 99)]
        (should= 99 ((:coerce r) nil))
        (should= 5  ((:coerce r) 5))
        (should= 0  ((:coerce r) 0))))
    )

  (context "default-coercions bundle"

    (it "exposes string coercers by name"
      (should= c/trim (:trim c/default-coercions))
      (should= c/upper-case (:upper-case c/default-coercions)))

    (it "exposes type coercers by name"
      (should= c/->string (:->string c/default-coercions))
      (should= c/->int (:->int c/default-coercions))
      (should= c/->uuid (:->uuid c/default-coercions)))

    (it "exposes the :default factory"
      (should= c/default (:default c/default-coercions)))
    )
  )
