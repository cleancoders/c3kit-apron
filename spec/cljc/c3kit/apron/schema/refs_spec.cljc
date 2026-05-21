(ns c3kit.apron.schema.refs-spec
  (:require
    [c3kit.apron.schema :as s]
    [c3kit.apron.schema.refs :as refs]
    [clojure.string :as str]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [around context describe it should should= should-not should-not= should-not-be-nil should-throw]]))

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

    (it ":one-of validates via validate-value! (set as predicate)"
      (should= :a (s/validate-value! {:type :any :validations [[:one-of :a :b :c]]} :a))
      (should-throw stdex "must be one of [:a :b :c]"
                    (s/validate-value! {:type :any :validations [[:one-of :a :b :c]]} :z)))

    (it "resolved ref message wins over field-level :message default"
      (should-throw stdex "must be one of [:a :b :c]"
                    (s/validate-value! {:type :any
                                        :message "kind required"
                                        :validations [[:one-of :a :b :c]]} :z))
      (should-throw stdex "must be positive"
                    (s/validate-value! {:type :any
                                        :message "must be valid"
                                        :validations [:pos?]} -1)))

    (it "entry's own :message still wins over resolved ref message"
      (should-throw stdex "custom"
                    (s/validate-value! {:type :any
                                        :validations [{:validate :pos? :message "custom"}]} -1)))

    (it "field-level :message is used when neither entry nor ref provides one"
      (should-throw stdex "field-default"
                    (s/validate-value! {:type :any
                                        :message "field-default"
                                        :validations [{:validate even?}]} 3)))

    (it "vector and map ref forms produce the same error message"
      (let [field-spec {:type :any :message "must be present"
                        :validations [[:one-of :a :b :c]]}
            map-spec   (assoc field-spec :validations [(refs/one-of :a :b :c)])]
        (should-throw stdex "must be one of [:a :b :c]" (s/validate-value! field-spec :z))
        (should-throw stdex "must be one of [:a :b :c]" (s/validate-value! map-spec   :z))))
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

    (it ":nil-or? accepts a validation map directly"
      (let [r (refs/nil-or? refs/pos?)]
        (should     ((:validate r) nil))
        (should     ((:validate r) 1))
        (should-not ((:validate r) -1))
        (should= "may be nil or must be positive" (:message r))))

    (it ":not? accepts a validation map directly"
      (let [r (refs/not? refs/pos?)]
        (should     ((:validate r) 0))
        (should-not ((:validate r) 1))
        (should= "must not: must be positive" (:message r))))

    (it ":and? accepts validation maps directly"
      (let [r (refs/and? refs/integer? refs/pos?)]
        (should     ((:validate r) 4))
        (should-not ((:validate r) -1))
        (should-not ((:validate r) 1.5))
        (should= "must be an integer and must be positive" (:message r))))

    (it ":or? accepts validation maps directly"
      (let [r (refs/or? refs/pos? refs/zero?)]
        (should     ((:validate r) 1))
        (should     ((:validate r) 0))
        (should-not ((:validate r) -1))
        (should= "must be positive or must be zero" (:message r))))

    (it "combinators accept factory invocations as resolved maps"
      (let [r (refs/nil-or? (refs/between 0 10))]
        (should     ((:validate r) nil))
        (should     ((:validate r) 5))
        (should-not ((:validate r) 11))
        (should= "may be nil or must be between 0 and 10" (:message r))))

    (it ":maybe? is the silent nil-or? — passes nil and inner pred"
      (let [r (refs/maybe? refs/pos?)]
        (should     ((:validate r) nil))
        (should     ((:validate r) 1))
        (should-not ((:validate r) -1))))

    (it ":maybe? uses the inner pred's message without a prefix"
      (should= "must be positive" (:message (refs/maybe? refs/pos?)))
      (should= "must be between 0 and 10" (:message (refs/maybe? (refs/between 0 10))))
      (should= "must be positive" (:message (refs/maybe? :pos?))))

    (it ":maybe? falls back to \"is invalid\" when inner has no message"
      (should= "is invalid" (:message (refs/maybe? even?))))

    (it ":maybe? composes through validate-value!"
      (should= nil (s/validate-value! {:type :any :validations [[:maybe? :pos?]]} nil))
      (should= 5   (s/validate-value! {:type :any :validations [[:maybe? :pos?]]} 5))
      (should-throw stdex "must be positive"
                    (s/validate-value! {:type :any :validations [[:maybe? :pos?]]} -1)))
    )

  (context "installation"

    (it "installed? is true after install!"
      (should (refs/installed?)))

    (it "installed? is false on a fresh registry"
      (binding [s/*ref-registry* (atom {})]
        (should-not (refs/installed?))))

    (it "ensure-installed! installs when registry is empty"
      (binding [s/*ref-registry* (atom {})]
        (should-not (refs/installed?))
        (refs/ensure-installed!)
        (should (refs/installed?))
        (should-not-be-nil (get @s/*ref-registry* :string?))))

    (it "ensure-installed! is a no-op when already installed"
      (let [warnings (atom [])]
        (binding [s/*warn-fn* (fn [msg] (swap! warnings conj msg))]
          (refs/ensure-installed!)
          (refs/ensure-installed!))
        (should= [] @warnings)))

    (it "ensure-installed! writes to the currently-bound registry, not the outer one"
      (let [outer-atom s/*ref-registry*]
        (reset! outer-atom {:sentinel ::marker})
        (binding [s/*ref-registry* (atom {})]
          (refs/ensure-installed!)
          (should-not-be-nil (get @s/*ref-registry* :string?)))
        (should= {:sentinel ::marker} @outer-atom)))
    )
  )
