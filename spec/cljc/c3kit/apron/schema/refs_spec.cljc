(ns c3kit.apron.schema.refs-spec
  (:require
    [c3kit.apron.schema :as s]
    [c3kit.apron.schema.refs :as refs]
    [clojure.string :as str]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [around context describe it should should= should-not should-not-be-nil should-throw]]))

(def stdex
  #?(:clj  clojure.lang.ExceptionInfo
     :cljs cljs.core/ExceptionInfo))

(describe "schema.refs"

  (around [it]
    (binding [s/*ref-registry* (atom {})]
      (refs/install!)
      (it)))

  (context "type predicates"

    (it ":string?"
      (should     ((:validate refs/string?) "hi"))
      (should-not ((:validate refs/string?) 42))
      (should= "must be a string" (:message refs/string?)))

    (it ":integer?"
      (should     ((:validate refs/integer?) 42))
      (should-not ((:validate refs/integer?) "hi"))
      (should-not ((:validate refs/integer?) 3.14))
      (should= "must be an integer" (:message refs/integer?)))

    (it ":keyword?"
      (should     ((:validate refs/keyword?) :foo))
      (should-not ((:validate refs/keyword?) "foo"))
      (should= "must be a keyword" (:message refs/keyword?)))

    (it ":number?"
      (should     ((:validate refs/number?) 42))
      (should     ((:validate refs/number?) 3.14))
      (should-not ((:validate refs/number?) "42"))
      (should= "must be a number" (:message refs/number?)))

    (it ":boolean?"
      (should     ((:validate refs/boolean?) true))
      (should     ((:validate refs/boolean?) false))
      (should-not ((:validate refs/boolean?) nil))
      (should-not ((:validate refs/boolean?) 0))
      (should= "must be a boolean" (:message refs/boolean?)))

    (it ":map?"
      (should     ((:validate refs/map?) {:a 1}))
      (should     ((:validate refs/map?) {}))
      (should-not ((:validate refs/map?) []))
      (should= "must be a map" (:message refs/map?)))
    )

  (context "numeric predicates"

    (it ":pos?"
      (should     ((:validate refs/pos?) 1))
      (should-not ((:validate refs/pos?) 0))
      (should-not ((:validate refs/pos?) -1))
      (should= "must be positive" (:message refs/pos?)))

    (it ":neg?"
      (should     ((:validate refs/neg?) -1))
      (should-not ((:validate refs/neg?) 0))
      (should-not ((:validate refs/neg?) 1))
      (should= "must be negative" (:message refs/neg?)))

    (it ":zero?"
      (should     ((:validate refs/zero?) 0))
      (should-not ((:validate refs/zero?) 1))
      (should= "must be zero" (:message refs/zero?)))

    (it ":pos-int?"
      (should     ((:validate refs/pos-int?) 1))
      (should-not ((:validate refs/pos-int?) 0))
      (should-not ((:validate refs/pos-int?) 1.5))
      (should-not ((:validate refs/pos-int?) -1))
      (should= "must be a positive integer" (:message refs/pos-int?)))

    (it ":neg-int?"
      (should     ((:validate refs/neg-int?) -1))
      (should-not ((:validate refs/neg-int?) 0))
      (should-not ((:validate refs/neg-int?) -1.5))
      (should= "must be a negative integer" (:message refs/neg-int?)))

    (it ":nat-int?"
      (should     ((:validate refs/nat-int?) 0))
      (should     ((:validate refs/nat-int?) 1))
      (should-not ((:validate refs/nat-int?) -1))
      (should-not ((:validate refs/nat-int?) 1.5))
      (should= "must be a non-negative integer" (:message refs/nat-int?)))
    )

  (context "apron predicates"

    (it ":present?"
      (should     ((:validate refs/present?) "x"))
      (should-not ((:validate refs/present?) ""))
      (should-not ((:validate refs/present?) nil))
      (should= "is required" (:message refs/present?)))

    (it ":email?"
      (should     ((:validate refs/email?) "a@b.co"))
      (should-not ((:validate refs/email?) "nope"))
      (should= "must be a valid email" (:message refs/email?)))

    (it ":bigdec?"
      (should ((:validate refs/bigdec?) #?(:clj 1M :cljs 1)))
      (should-not ((:validate refs/bigdec?) "1"))
      (should= "must be a bigdec" (:message refs/bigdec?)))

    (it ":uri?"
      (should     ((:validate refs/uri?) #?(:clj (java.net.URI. "http://a") :cljs "http://a")))
      (should-not ((:validate refs/uri?) #?(:clj 42 :cljs nil)))
      (should= "must be a URI" (:message refs/uri?)))
    )

  (context "comparison factories"

    (it ":>"
      (let [r (refs/> 5)]
        (should     ((:validate r) 6))
        (should-not ((:validate r) 5))
        (should= "must be > 5" (:message r))))

    (it ":<"
      (let [r (refs/< 5)]
        (should     ((:validate r) 4))
        (should-not ((:validate r) 5))
        (should= "must be < 5" (:message r))))

    (it ":>="
      (let [r (refs/>= 5)]
        (should     ((:validate r) 5))
        (should     ((:validate r) 6))
        (should-not ((:validate r) 4))
        (should= "must be >= 5" (:message r))))

    (it ":<="
      (let [r (refs/<= 5)]
        (should     ((:validate r) 5))
        (should     ((:validate r) 4))
        (should-not ((:validate r) 6))
        (should= "must be <= 5" (:message r))))

    (it ":="
      (let [r (refs/= :a)]
        (should     ((:validate r) :a))
        (should-not ((:validate r) :b))
        (should= "must = :a" (:message r))))

    (it ":not="
      (let [r (refs/not= :a)]
        (should     ((:validate r) :b))
        (should-not ((:validate r) :a))
        (should= "must not = :a" (:message r))))

    (it ":between"
      (let [r (refs/between 1 10)]
        (should     ((:validate r) 1))
        (should     ((:validate r) 10))
        (should     ((:validate r) 5))
        (should-not ((:validate r) 0))
        (should-not ((:validate r) 11))
        (should= "must be between 1 and 10" (:message r))))
    )

  (context "shape factories"

    (it ":min-length"
      (let [r (refs/min-length 3)]
        (should     ((:validate r) "abc"))
        (should     ((:validate r) "abcd"))
        (should-not ((:validate r) "ab"))
        (should= "must be at least 3 characters" (:message r))))

    (it ":max-length"
      (let [r (refs/max-length 3)]
        (should     ((:validate r) "ab"))
        (should     ((:validate r) "abc"))
        (should-not ((:validate r) "abcd"))
        (should= "must be at most 3 characters" (:message r))))

    (it ":length"
      (let [r (refs/length 3)]
        (should     ((:validate r) "abc"))
        (should-not ((:validate r) "ab"))
        (should-not ((:validate r) "abcd"))
        (should= "must be exactly 3 characters" (:message r))))

    (it ":matches"
      (let [r (refs/matches #"^foo")]
        (should     ((:validate r) "foobar"))
        (should-not ((:validate r) "barfoo"))
        (should= #?(:clj "must match ^foo" :cljs "must match /^foo/")
                 (:message r))))

    (it ":one-of"
      (let [r (refs/one-of "a" "b" "c")]
        (should     ((:validate r) "a"))
        (should     ((:validate r) "b"))
        (should-not ((:validate r) "z"))
        (should= "must be one of [\"a\" \"b\" \"c\"]" (:message r))))

    (it ":not-one-of"
      (let [r (refs/not-one-of "a" "b")]
        (should     ((:validate r) "z"))
        (should-not ((:validate r) "a"))
        (should= "must not be one of [\"a\" \"b\"]" (:message r))))
    )

  (context "string coercers"

    (it ":trim"
      (should= "foo" ((:coerce refs/trim) "  foo  "))
      (should= "must be a string" (:message refs/trim)))

    (it ":upper-case"
      (should= "FOO" ((:coerce refs/upper-case) "foo"))
      (should= "must be a string" (:message refs/upper-case)))

    (it ":lower-case"
      (should= "foo" ((:coerce refs/lower-case) "FOO"))
      (should= "must be a string" (:message refs/lower-case)))

    (it ":capitalize"
      (should= "Foo" ((:coerce refs/capitalize) "foo"))
      (should= "must be a string" (:message refs/capitalize)))
    )

  (context "type coercers"

    (it ":->string"
      (should= "42" ((:coerce refs/->string) 42))
      (should= "could not coerce to string" (:message refs/->string)))

    (it ":->int"
      (should= 42 ((:coerce refs/->int) "42"))
      (should= 42 ((:coerce refs/->int) 42.9))
      (should= "could not coerce to int" (:message refs/->int)))

    (it ":->float"
      (should= 3.14 ((:coerce refs/->float) "3.14"))
      (should= "could not coerce to float" (:message refs/->float)))

    (it ":->keyword"
      (should= :foo ((:coerce refs/->keyword) "foo"))
      (should= "could not coerce to keyword" (:message refs/->keyword)))

    (it ":->boolean"
      (should= true  ((:coerce refs/->boolean) "true"))
      (should= false ((:coerce refs/->boolean) "false"))
      (should= "could not coerce to boolean" (:message refs/->boolean)))
    )

  (context "coercion factories"

    (it ":default supplies a value when nil"
      (let [r (refs/default 99)]
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
