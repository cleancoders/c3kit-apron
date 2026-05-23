(ns c3kit.apron.schema.validations-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema-spec :refer [stdex]]
    [c3kit.apron.schema.validations :as v]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [context describe it should should= should-not should-throw]]))

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

    (it ":required (alias for :present?)"
      (should= v/present? v/required))

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

  (context "default-validations bundle"

    (it "exposes the type predicates"
      (should= v/string? (:string? v/default-validations))
      (should= v/integer? (:integer? v/default-validations))
      (should= v/present? (:present? v/default-validations))
      (should= v/required (:required v/default-validations)))

    (it "exposes the factories"
      (should= v/> (:> v/default-validations))
      (should= v/between (:between v/default-validations)))

    (it "exposes the combinators by name"
      (should-not (nil? (:nil-or? v/default-validations)))
      (should-not (nil? (:and? v/default-validations)))
      (should-not (nil? (:or? v/default-validations)))
      (should-not (nil? (:not? v/default-validations))))
    )
  )
