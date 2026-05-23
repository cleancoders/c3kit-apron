(ns c3kit.apron.schema.validations-spec
  (:require
    [c3kit.apron.schema :as s]
    [c3kit.apron.schema.validations :as v]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [context describe it should should= should-not should-throw]]))

(def stdex
  #?(:clj  clojure.lang.ExceptionInfo
     :cljs cljs.core/ExceptionInfo))

(describe "schema.validations"

  (context "type predicates"

    (it ":string?"
      (should     ((:validate v/string?) "hi"))
      (should-not ((:validate v/string?) 42))
      (should= "must be a string" (:message v/string?)))

    (it ":integer?"
      (should     ((:validate v/integer?) 42))
      (should-not ((:validate v/integer?) "hi"))
      (should-not ((:validate v/integer?) 3.14))
      (should= "must be an integer" (:message v/integer?)))

    (it ":keyword?"
      (should     ((:validate v/keyword?) :foo))
      (should-not ((:validate v/keyword?) "foo"))
      (should= "must be a keyword" (:message v/keyword?)))

    (it ":number?"
      (should     ((:validate v/number?) 42))
      (should     ((:validate v/number?) 3.14))
      (should-not ((:validate v/number?) "42"))
      (should= "must be a number" (:message v/number?)))

    (it ":boolean?"
      (should     ((:validate v/boolean?) true))
      (should     ((:validate v/boolean?) false))
      (should-not ((:validate v/boolean?) nil))
      (should-not ((:validate v/boolean?) 0))
      (should= "must be a boolean" (:message v/boolean?)))

    (it ":map?"
      (should     ((:validate v/map?) {:a 1}))
      (should     ((:validate v/map?) {}))
      (should-not ((:validate v/map?) []))
      (should= "must be a map" (:message v/map?)))
    )

  (context "numeric predicates"

    (it ":pos?"
      (should     ((:validate v/pos?) 1))
      (should-not ((:validate v/pos?) 0))
      (should-not ((:validate v/pos?) -1))
      (should= "must be positive" (:message v/pos?)))

    (it ":neg?"
      (should     ((:validate v/neg?) -1))
      (should-not ((:validate v/neg?) 0))
      (should-not ((:validate v/neg?) 1))
      (should= "must be negative" (:message v/neg?)))

    (it ":zero?"
      (should     ((:validate v/zero?) 0))
      (should-not ((:validate v/zero?) 1))
      (should= "must be zero" (:message v/zero?)))

    (it ":pos-int?"
      (should     ((:validate v/pos-int?) 1))
      (should-not ((:validate v/pos-int?) 0))
      (should-not ((:validate v/pos-int?) 1.5))
      (should-not ((:validate v/pos-int?) -1))
      (should= "must be a positive integer" (:message v/pos-int?)))

    (it ":neg-int?"
      (should     ((:validate v/neg-int?) -1))
      (should-not ((:validate v/neg-int?) 0))
      (should-not ((:validate v/neg-int?) -1.5))
      (should= "must be a negative integer" (:message v/neg-int?)))

    (it ":nat-int?"
      (should     ((:validate v/nat-int?) 0))
      (should     ((:validate v/nat-int?) 1))
      (should-not ((:validate v/nat-int?) -1))
      (should-not ((:validate v/nat-int?) 1.5))
      (should= "must be a non-negative integer" (:message v/nat-int?)))
    )

  (context "apron predicates"

    (it ":present?"
      (should     ((:validate v/present?) "x"))
      (should-not ((:validate v/present?) ""))
      (should-not ((:validate v/present?) nil))
      (should= "is required" (:message v/present?)))

    (it ":email?"
      (should     ((:validate v/email?) "a@b.co"))
      (should-not ((:validate v/email?) "nope"))
      (should= "must be a valid email" (:message v/email?)))

    (it ":bigdec?"
      (should ((:validate v/bigdec?) #?(:clj 1M :cljs 1)))
      (should-not ((:validate v/bigdec?) "1"))
      (should= "must be a bigdec" (:message v/bigdec?)))

    (it ":uri?"
      (should     ((:validate v/uri?) #?(:clj (java.net.URI. "http://a") :cljs "http://a")))
      (should-not ((:validate v/uri?) #?(:clj 42 :cljs nil)))
      (should= "must be a URI" (:message v/uri?)))
    )

  (context "comparison factories"

    (it ":>"
      (let [r (v/> 5)]
        (should     ((:validate r) 6))
        (should-not ((:validate r) 5))
        (should= "must be > 5" (:message r))))

    (it ":<"
      (let [r (v/< 5)]
        (should     ((:validate r) 4))
        (should-not ((:validate r) 5))
        (should= "must be < 5" (:message r))))

    (it ":>="
      (let [r (v/>= 5)]
        (should     ((:validate r) 5))
        (should     ((:validate r) 6))
        (should-not ((:validate r) 4))
        (should= "must be >= 5" (:message r))))

    (it ":<="
      (let [r (v/<= 5)]
        (should     ((:validate r) 5))
        (should     ((:validate r) 4))
        (should-not ((:validate r) 6))
        (should= "must be <= 5" (:message r))))

    (it ":="
      (let [r (v/= :a)]
        (should     ((:validate r) :a))
        (should-not ((:validate r) :b))
        (should= "must = :a" (:message r))))

    (it ":not="
      (let [r (v/not= :a)]
        (should     ((:validate r) :b))
        (should-not ((:validate r) :a))
        (should= "must not = :a" (:message r))))

    (it ":between"
      (let [r (v/between 1 10)]
        (should     ((:validate r) 1))
        (should     ((:validate r) 10))
        (should     ((:validate r) 5))
        (should-not ((:validate r) 0))
        (should-not ((:validate r) 11))
        (should= "must be between 1 and 10" (:message r))))
    )

  (context "shape factories"

    (it ":min-length"
      (let [r (v/min-length 3)]
        (should     ((:validate r) "abc"))
        (should     ((:validate r) "abcd"))
        (should-not ((:validate r) "ab"))
        (should= "must be at least 3 characters" (:message r))))

    (it ":max-length"
      (let [r (v/max-length 3)]
        (should     ((:validate r) "ab"))
        (should     ((:validate r) "abc"))
        (should-not ((:validate r) "abcd"))
        (should= "must be at most 3 characters" (:message r))))

    (it ":length"
      (let [r (v/length 3)]
        (should     ((:validate r) "abc"))
        (should-not ((:validate r) "ab"))
        (should-not ((:validate r) "abcd"))
        (should= "must be exactly 3 characters" (:message r))))

    (it ":matches"
      (let [r (v/matches #"^foo")]
        (should     ((:validate r) "foobar"))
        (should-not ((:validate r) "barfoo"))
        (should= #?(:clj "must match ^foo" :cljs "must match /^foo/")
                 (:message r))))

    (it ":one-of"
      (let [r (v/one-of "a" "b" "c")]
        (should     ((:validate r) "a"))
        (should     ((:validate r) "b"))
        (should-not ((:validate r) "z"))
        (should= "must be one of [\"a\" \"b\" \"c\"]" (:message r))))

    (it ":not-one-of"
      (let [r (v/not-one-of "a" "b")]
        (should     ((:validate r) "z"))
        (should-not ((:validate r) "a"))
        (should= "must not be one of [\"a\" \"b\"]" (:message r))))
    )

  (context "message wiring"

    (it "validator message surfaces through validate-value!"
      (should-throw stdex "must be a string"
                    (s/validate-value! {:type :any :validations [:string?]} 42)))

    (it "factory message surfaces through validate-value!"
      (should-throw stdex "must be > 5"
                    (s/validate-value! {:type :any :validations [[:> 5]]} 3)))
    )

  (context "combinator factories"

    (it ":nil-or? passes when value is nil"
      (should= nil (s/validate-value! {:type :any :validations [[:nil-or? :pos?]]} nil)))

    (it ":nil-or? passes when inner pred passes"
      (should= 5 (s/validate-value! {:type :any :validations [[:nil-or? :pos?]]} 5)))

    (it ":nil-or? fails when inner pred fails on a non-nil value"
      (should-throw stdex (s/validate-value! {:type :any :validations [[:nil-or? :pos?]]} -1)))

    (it ":nil-or? composes with a factory invocation"
      (should= nil (s/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} nil))
      (should= 5  (s/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} 5))
      (should-throw stdex (s/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} 11)))

    (it ":nil-or? accepts an inline fn"
      (should= 4 (s/validate-value! {:type :any :validations [[:nil-or? even?]]} 4))
      (should-throw stdex (s/validate-value! {:type :any :validations [[:nil-or? even?]]} 3)))

    (it ":not? inverts a ref predicate"
      (should= 0  (s/validate-value! {:type :any :validations [[:not? :pos?]]} 0))
      (should= -1 (s/validate-value! {:type :any :validations [[:not? :pos?]]} -1))
      (should-throw stdex (s/validate-value! {:type :any :validations [[:not? :pos?]]} 1)))

    (it ":and? requires every inner pred to pass"
      (should= 4 (s/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} 4))
      (should-throw stdex (s/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} -1))
      (should-throw stdex (s/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} 1.5)))

    (it ":or? passes if any inner pred passes"
      (should= 1   (s/validate-value! {:type :any :validations [[:or? :pos? :zero?]]} 1))
      (should= 0   (s/validate-value! {:type :any :validations [[:or? :pos? :zero?]]} 0))
      (should-throw stdex (s/validate-value! {:type :any :validations [[:or? :pos? :zero?]]} -1)))

    (it ":and? composes nested factory invocations"
      (should= 5 (s/validate-value!
                   {:type :any :validations [[:and? :integer? [:between 0 10]]]} 5))
      (should-throw stdex (s/validate-value!
                            {:type :any :validations [[:and? :integer? [:between 0 10]]]} 11)))

    (it "combinator messages compose from inner ref messages"
      (should-throw stdex "may be nil or must be positive"
                    (s/validate-value! {:type :any :validations [[:nil-or? :pos?]]} -1))
      (should-throw stdex "must be an integer and must be positive"
                    (s/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} -1.5)))
    )
  )
