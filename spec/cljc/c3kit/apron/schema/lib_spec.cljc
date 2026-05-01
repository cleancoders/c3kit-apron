(ns c3kit.apron.schema.lib-spec
  (:require
    [c3kit.apron.schema :as s]
    [c3kit.apron.schema.lib :as lib]
    [clojure.string :as str]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [around context describe it should should= should-not should-not-be-nil should-throw]]))

(def stdex
  #?(:clj  clojure.lang.ExceptionInfo
     :cljs cljs.core/ExceptionInfo))

(describe "schema.lib"

  (around [it]
    (binding [s/*ref-registry* (atom {})]
      (lib/install!)
      (it)))

  (context "type predicates"

    (it ":string?"
      (should     ((:validate lib/string?) "hi"))
      (should-not ((:validate lib/string?) 42))
      (should= "must be a string" (:message lib/string?)))

    (it ":integer?"
      (should     ((:validate lib/integer?) 42))
      (should-not ((:validate lib/integer?) "hi"))
      (should-not ((:validate lib/integer?) 3.14))
      (should= "must be an integer" (:message lib/integer?)))

    (it ":keyword?"
      (should     ((:validate lib/keyword?) :foo))
      (should-not ((:validate lib/keyword?) "foo"))
      (should= "must be a keyword" (:message lib/keyword?)))

    (it ":number?"
      (should     ((:validate lib/number?) 42))
      (should     ((:validate lib/number?) 3.14))
      (should-not ((:validate lib/number?) "42"))
      (should= "must be a number" (:message lib/number?)))

    (it ":boolean?"
      (should     ((:validate lib/boolean?) true))
      (should     ((:validate lib/boolean?) false))
      (should-not ((:validate lib/boolean?) nil))
      (should-not ((:validate lib/boolean?) 0))
      (should= "must be a boolean" (:message lib/boolean?)))

    (it ":map?"
      (should     ((:validate lib/map?) {:a 1}))
      (should     ((:validate lib/map?) {}))
      (should-not ((:validate lib/map?) []))
      (should= "must be a map" (:message lib/map?)))
    )

  (context "numeric predicates"

    (it ":pos?"
      (should     ((:validate lib/pos?) 1))
      (should-not ((:validate lib/pos?) 0))
      (should-not ((:validate lib/pos?) -1))
      (should= "must be positive" (:message lib/pos?)))

    (it ":neg?"
      (should     ((:validate lib/neg?) -1))
      (should-not ((:validate lib/neg?) 0))
      (should-not ((:validate lib/neg?) 1))
      (should= "must be negative" (:message lib/neg?)))

    (it ":zero?"
      (should     ((:validate lib/zero?) 0))
      (should-not ((:validate lib/zero?) 1))
      (should= "must be zero" (:message lib/zero?)))

    (it ":pos-int?"
      (should     ((:validate lib/pos-int?) 1))
      (should-not ((:validate lib/pos-int?) 0))
      (should-not ((:validate lib/pos-int?) 1.5))
      (should-not ((:validate lib/pos-int?) -1))
      (should= "must be a positive integer" (:message lib/pos-int?)))

    (it ":neg-int?"
      (should     ((:validate lib/neg-int?) -1))
      (should-not ((:validate lib/neg-int?) 0))
      (should-not ((:validate lib/neg-int?) -1.5))
      (should= "must be a negative integer" (:message lib/neg-int?)))

    (it ":nat-int?"
      (should     ((:validate lib/nat-int?) 0))
      (should     ((:validate lib/nat-int?) 1))
      (should-not ((:validate lib/nat-int?) -1))
      (should-not ((:validate lib/nat-int?) 1.5))
      (should= "must be a non-negative integer" (:message lib/nat-int?)))
    )

  (context "apron predicates"

    (it ":present?"
      (should     ((:validate lib/present?) "x"))
      (should-not ((:validate lib/present?) ""))
      (should-not ((:validate lib/present?) nil))
      (should= "is required" (:message lib/present?)))

    (it ":email?"
      (should     ((:validate lib/email?) "a@b.co"))
      (should-not ((:validate lib/email?) "nope"))
      (should= "must be a valid email" (:message lib/email?)))

    (it ":bigdec?"
      (should ((:validate lib/bigdec?) #?(:clj 1M :cljs 1)))
      (should-not ((:validate lib/bigdec?) "1"))
      (should= "must be a bigdec" (:message lib/bigdec?)))

    (it ":uri?"
      (should     ((:validate lib/uri?) #?(:clj (java.net.URI. "http://a") :cljs "http://a")))
      (should-not ((:validate lib/uri?) #?(:clj 42 :cljs nil)))
      (should= "must be a URI" (:message lib/uri?)))
    )

  (context "comparison factories"

    (it ":>"
      (let [r (lib/> 5)]
        (should     ((:validate r) 6))
        (should-not ((:validate r) 5))
        (should= "must be > 5" (:message r))))

    (it ":<"
      (let [r (lib/< 5)]
        (should     ((:validate r) 4))
        (should-not ((:validate r) 5))
        (should= "must be < 5" (:message r))))

    (it ":>="
      (let [r (lib/>= 5)]
        (should     ((:validate r) 5))
        (should     ((:validate r) 6))
        (should-not ((:validate r) 4))
        (should= "must be >= 5" (:message r))))

    (it ":<="
      (let [r (lib/<= 5)]
        (should     ((:validate r) 5))
        (should     ((:validate r) 4))
        (should-not ((:validate r) 6))
        (should= "must be <= 5" (:message r))))

    (it ":="
      (let [r (lib/= :a)]
        (should     ((:validate r) :a))
        (should-not ((:validate r) :b))
        (should= "must = :a" (:message r))))

    (it ":not="
      (let [r (lib/not= :a)]
        (should     ((:validate r) :b))
        (should-not ((:validate r) :a))
        (should= "must not = :a" (:message r))))

    (it ":between"
      (let [r (lib/between 1 10)]
        (should     ((:validate r) 1))
        (should     ((:validate r) 10))
        (should     ((:validate r) 5))
        (should-not ((:validate r) 0))
        (should-not ((:validate r) 11))
        (should= "must be between 1 and 10" (:message r))))
    )

  (context "shape factories"

    (it ":min-length"
      (let [r (lib/min-length 3)]
        (should     ((:validate r) "abc"))
        (should     ((:validate r) "abcd"))
        (should-not ((:validate r) "ab"))
        (should= "must be at least 3 characters" (:message r))))

    (it ":max-length"
      (let [r (lib/max-length 3)]
        (should     ((:validate r) "ab"))
        (should     ((:validate r) "abc"))
        (should-not ((:validate r) "abcd"))
        (should= "must be at most 3 characters" (:message r))))

    (it ":length"
      (let [r (lib/length 3)]
        (should     ((:validate r) "abc"))
        (should-not ((:validate r) "ab"))
        (should-not ((:validate r) "abcd"))
        (should= "must be exactly 3 characters" (:message r))))

    (it ":matches"
      (let [r (lib/matches #"^foo")]
        (should     ((:validate r) "foobar"))
        (should-not ((:validate r) "barfoo"))
        (should= #?(:clj "must match ^foo" :cljs "must match /^foo/")
                 (:message r))))

    (it ":one-of"
      (let [r (lib/one-of "a" "b" "c")]
        (should     ((:validate r) "a"))
        (should     ((:validate r) "b"))
        (should-not ((:validate r) "z"))
        (should= "must be one of [\"a\" \"b\" \"c\"]" (:message r))))

    (it ":not-one-of"
      (let [r (lib/not-one-of "a" "b")]
        (should     ((:validate r) "z"))
        (should-not ((:validate r) "a"))
        (should= "must not be one of [\"a\" \"b\"]" (:message r))))
    )

  (context "string coercers"

    (it ":trim"
      (should= "foo" ((:coerce lib/trim) "  foo  "))
      (should= "must be a string" (:message lib/trim)))

    (it ":upper-case"
      (should= "FOO" ((:coerce lib/upper-case) "foo"))
      (should= "must be a string" (:message lib/upper-case)))

    (it ":lower-case"
      (should= "foo" ((:coerce lib/lower-case) "FOO"))
      (should= "must be a string" (:message lib/lower-case)))

    (it ":capitalize"
      (should= "Foo" ((:coerce lib/capitalize) "foo"))
      (should= "must be a string" (:message lib/capitalize)))
    )

  (context "type coercers"

    (it ":->string"
      (should= "42" ((:coerce lib/->string) 42))
      (should= "could not coerce to string" (:message lib/->string)))

    (it ":->int"
      (should= 42 ((:coerce lib/->int) "42"))
      (should= 42 ((:coerce lib/->int) 42.9))
      (should= "could not coerce to int" (:message lib/->int)))

    (it ":->float"
      (should= 3.14 ((:coerce lib/->float) "3.14"))
      (should= "could not coerce to float" (:message lib/->float)))

    (it ":->keyword"
      (should= :foo ((:coerce lib/->keyword) "foo"))
      (should= "could not coerce to keyword" (:message lib/->keyword)))

    (it ":->boolean"
      (should= true  ((:coerce lib/->boolean) "true"))
      (should= false ((:coerce lib/->boolean) "false"))
      (should= "could not coerce to boolean" (:message lib/->boolean)))
    )

  (context "coercion factories"

    (it ":default supplies a value when nil"
      (let [r (lib/default 99)]
        (should= 99 ((:coerce r) nil))
        (should= 5  ((:coerce r) 5))
        (should= 0  ((:coerce r) 0))))
    )

  (context "message wiring"

    (it "validator message surfaces through validate-value!"
      (should-throw stdex "must be a string"
                    (s/validate-value! {:type :any :validations [:string?]} 42)))

    (it "factory message surfaces through validate-value!"
      (should-throw stdex "must be > 5"
                    (s/validate-value! {:type :any :validations [[:> 5]]} 3)))

    (it "coercer message surfaces through coerce-value!"
      (should-throw stdex "must be a string"
                    (s/coerce-value! {:type :any :coercions [:trim]} 42)))
    )
  )
