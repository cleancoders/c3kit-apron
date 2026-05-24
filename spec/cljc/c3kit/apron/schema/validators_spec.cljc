(ns c3kit.apron.schema.validators-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema-spec :refer [pet valid-pet invalid-pet a-uuid temperaments stdex]]
    [clojure.string :as str]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should should= should-be-nil
                                                      should-contain should-not should-not-contain
                                                      should-throw]])
  #?(:clj (:import (java.net URI))))

(describe "schema.validators"

  (context "message wiring"

    (it "validator message surfaces through validate-value!"
      (should-throw stdex "must be a string"
                    (schema/validate-value! {:type :any :validations [:string?]} 42)))

    (it "factory message surfaces through validate-value!"
      (should-throw stdex "must be > 5"
                    (schema/validate-value! {:type :any :validations [[:> 5]]} 3)))
    )

  (context "combinator factories"

    (it ":nil-or? passes when value is nil"
      (should= nil (schema/validate-value! {:type :any :validations [[:nil-or? :pos?]]} nil)))

    (it ":nil-or? passes when inner pred passes"
      (should= 5 (schema/validate-value! {:type :any :validations [[:nil-or? :pos?]]} 5)))

    (it ":nil-or? fails when inner pred fails on a non-nil value"
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:nil-or? :pos?]]} -1)))

    (it ":nil-or? composes with a factory invocation"
      (should= nil (schema/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} nil))
      (should= 5  (schema/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} 5))
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:nil-or? [:between 0 10]]]} 11)))

    (it ":nil-or? accepts an inline fn"
      (should= 4 (schema/validate-value! {:type :any :validations [[:nil-or? even?]]} 4))
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:nil-or? even?]]} 3)))

    (it ":not? inverts a ref predicate"
      (should= 0  (schema/validate-value! {:type :any :validations [[:not? :pos?]]} 0))
      (should= -1 (schema/validate-value! {:type :any :validations [[:not? :pos?]]} -1))
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:not? :pos?]]} 1)))

    (it ":and? requires every inner pred to pass"
      (should= 4 (schema/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} 4))
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} -1))
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} 1.5)))

    (it ":or? passes if any inner pred passes"
      (should= 1   (schema/validate-value! {:type :any :validations [[:or? :pos? :zero?]]} 1))
      (should= 0   (schema/validate-value! {:type :any :validations [[:or? :pos? :zero?]]} 0))
      (should-throw stdex (schema/validate-value! {:type :any :validations [[:or? :pos? :zero?]]} -1)))

    (it ":and? composes nested factory invocations"
      (should= 5 (schema/validate-value!
                   {:type :any :validations [[:and? :integer? [:between 0 10]]]} 5))
      (should-throw stdex (schema/validate-value!
                            {:type :any :validations [[:and? :integer? [:between 0 10]]]} 11)))

    (it "combinator messages compose from inner ref messages"
      (should-throw stdex "must be positive"
                    (schema/validate-value! {:type :any :validations [[:nil-or? :pos?]]} -1))
      (should-throw stdex "must be an integer and must be positive"
                    (schema/validate-value! {:type :any :validations [[:and? :integer? :pos?]]} -1.5)))
    )
  )

(describe "Schema validation"

  (it "of presence"
    (should= false (schema/present? nil))
    (should= false (schema/present? ""))
    (should= true (schema/present? 1))
    (should= true (schema/present? "abc")))

  (it "of email?"
    (should (schema/email? "micahmartin@gmail.com"))
    (should (schema/email? "micah@clenacoders.com"))
    (should (schema/email? "vikas.rao@rsa.rohde-schwarz.com"))
    (should-not (schema/email? "micah@clenacoders"))
    (should-not (schema/email? "micah")))

  (it "of enum"
    (let [is-temperament? (schema/is-enum? temperaments)]
      (should (is-temperament? nil))
      (should (is-temperament? :temperament/wild))
      (should (is-temperament? :temperament/domestic))
      (should-not (is-temperament? ":temperament/savage"))
      (should-not (is-temperament? :wild))
      (should-not (is-temperament? :temperament/savage))))

  (context "from spec"

    (it "with missing type"
      (should-throw stdex "invalid spec: {}" (schema/validate-value! {} 123)))

    (it "of booleans"
      (should= true (schema/valid-value? {:type :boolean} true))
      (should= true (schema/valid-value? {:type :boolean} false))
      (should= false (schema/valid-value? {:type :boolean} 123)))

    (it "of strings"
      (should= true (schema/valid-value? {:type :string} "123"))
      (should= false (schema/valid-value? {:type :string} 123)))

    (it "of keywords"
      (should= true (schema/valid-value? {:type :keyword} :abc))
      (should= false (schema/valid-value? {:type :keyword} "abc"))
      (should= false (schema/valid-value? {:type :keyword} 123)))

    (it "of kw-ref"
      (should= true (schema/valid-value? {:type :kw-ref} :abc))
      (should= false (schema/valid-value? {:type :kw-ref} "abc"))
      (should= false (schema/valid-value? {:type :kw-ref} 123)))

    (it "of int"
      (should= true (schema/valid-value? {:type :int} 123))
      (should= false (schema/valid-value? {:type :int} 123.45))
      (should= true (schema/valid-value? {:type :long} 123))
      (should= false (schema/valid-value? {:type :long} 123.45)))

    (it "of ref"
      (should= true (schema/valid-value? {:type :ref} 123))
      (should= false (schema/valid-value? {:type :ref} 123.45)))

    (it "of float"
      (should= true (schema/valid-value? {:type :float} 123.456))
      #?(:clj (should= false (schema/valid-value? {:type :float} 123)))
      #?(:clj (should= false (schema/valid-value? {:type :float} 123M)))
      (should= false (schema/valid-value? {:type :float} "123"))
      (should= true (schema/valid-value? {:type :double} 123.456))
      #?(:clj (should= false (schema/valid-value? {:type :double} 123)))
      #?(:clj (should= false (schema/valid-value? {:type :double} 123M)))
      (should= false (schema/valid-value? {:type :double} "123")))

    (it "of bigdec"
      (should= true (schema/valid-value? {:type :bigdec} 123.456M))
      #?(:clj (should= false (schema/valid-value? {:type :bigdec} 123.456)))
      #?(:clj (should= false (schema/valid-value? {:type :bigdec} 123)))
      (should= false (schema/valid-value? {:type :bigdec} "123")))

    (it "of date/instant"
      (should= true (schema/valid-value? {:type :instant} nil))
      (should= false (schema/valid-value? {:type :instant} "foo"))
      (should= false (schema/valid-value? {:type :instant} 123))
      #?(:clj (should= true (schema/valid-value? {:type :instant} (java.util.Date.))))
      #?(:cljs (should= true (schema/valid-value? {:type :instant} (js/Date.))))
      #?(:cljs (should= false (schema/valid-value? {:type :instant} (js/goog.date.Date.)))))

    (it "of sql-date"
      (should= true (schema/valid-value? {:type :date} nil))
      (should= false (schema/valid-value? {:type :date} "foo"))
      (should= false (schema/valid-value? {:type :date} 123))
      #?(:clj (should= false (schema/valid-value? {:type :date} (java.util.Date.))))
      #?(:clj (should= true (schema/valid-value? {:type :date} (java.sql.Date. (System/currentTimeMillis)))))
      #?(:cljs (should= true (schema/valid-value? {:type :date} (js/Date.))))
      #?(:cljs (should= false (schema/valid-value? {:type :date} (js/goog.date.Date.)))))

    (it "of timestamp"
      (should= true (schema/valid-value? {:type :timestamp} nil))
      (should= false (schema/valid-value? {:type :timestamp} "foo"))
      (should= false (schema/valid-value? {:type :timestamp} 123))
      #?(:clj (should= false (schema/valid-value? {:type :timestamp} (java.util.Date.))))
      #?(:bb  nil
         :clj (should= true (schema/valid-value? {:type :timestamp} (java.sql.Timestamp. (System/currentTimeMillis)))))
      #?(:cljs (should= true (schema/valid-value? {:type :timestamp} (js/Date.))))
      #?(:cljs (should= false (schema/valid-value? {:type :timestamp} (js/goog.date.Date.)))))

    (it "of URI"
      (should= true (schema/valid-value? {:type :uri} nil))
      (should= #?(:clj false :cljs true) (schema/valid-value? {:type :uri} "foo"))
      #?(:clj (should= true (schema/valid-value? {:type :uri} (URI/create "foo"))))
      (should= false (schema/valid-value? {:type :uri} 123)))

    (it "of UUID"
      (should= true (schema/valid-value? {:type :uuid} nil))
      (should= false (schema/valid-value? {:type :uuid} "foo"))
      (should= true (schema/valid-value? {:type :uuid} a-uuid))
      (should= false (schema/valid-value? {:type :uuid} "1234"))
      (should= false (schema/valid-value? {:type :uuid} 123)))

    (it "of custom validation"
      (let [spec {:type :string :validate #(re-matches #"x+" %)}]
        (should= true (schema/valid-value? spec "xxx"))
        (should= false (schema/valid-value? spec "xox"))))

    (it "of multiple custom validations"
      (let [spec {:type :string :validate [#(not (nil? %)) #(<= 5 (count %))]}]
        (should= true (schema/valid-value? spec "abcdef"))
        (should= false (schema/valid-value? spec nil))))

    (it "allows nils, unless specified"
      (should= true (schema/valid-value? {:type :string} nil))
      (should= false (schema/valid-value? {:type :string :validate [schema/present?]} nil))
      (should= true (schema/valid-value? {:type :int} nil))
      (should= false (schema/valid-value? {:type :int :validate [schema/present?]} nil))
      (should= true (schema/valid-value? {:type :ref} nil))
      (should= false (schema/valid-value? {:type :ref :validate [schema/present?]} nil))
      (should= true (schema/valid-value? {:type :float} nil))
      (should= false (schema/valid-value? {:type :float :validate [schema/present?]} nil))
      (should= true (schema/valid-value? {:type :instant} nil))
      (should= false (schema/valid-value? {:type :instant :validate [schema/present?]} nil)))

    (it "of seq with default validations"
      (should= true (schema/valid-value? {:type [:float]} [32.1 3.1415]))
      (should= false (schema/valid-value? {:type [:float]} 3.1415))
      (should= false (schema/valid-value? {:type [:float]} ["3.1415"]))
      (should= true (schema/valid-value? {:type [:float]} nil)))

    (it "of seq with custom validations"
      (should= true (schema/valid-value? {:type [:float] :validate pos?} [32.1 3.1415]))
      (should= false (schema/valid-value? {:type [:float] :validate pos?} [32.1 -3.1415])))

    (it "of object"
      (let [spec {:type {:foo {:type :keyword}}}]
        (should= true (schema/valid-value? spec {:foo :bar}))))

    (it "of seq of objects"
      (let [spec {:type [{:age {:type :int}}]}]
        (should= true (schema/valid-value? spec [{:age 1} {:age 2}]))
        (should= false (schema/valid-value? spec [{:age :foo}]))))

    (it "of object with customs"
      (let [spec {:type {:foo {:type :keyword}} :validate :foo}]
        (should= true (schema/valid-value? spec {:foo :bar}))
        (should= false (schema/valid-value? spec {}))))

    (it "of multiple object with custom validation"
      (let [spec {:type [{:foo {:type :keyword}}] :validate :foo}]
        (should= true (schema/valid-value? spec [{:foo :bar} {:foo :baz}]))
        (should= false (schema/valid-value? spec [{:foo :bar} {}]))
        (should= false (schema/valid-value? spec [{} {:foo :bar}]))
        (should= true (schema/valid-value? spec []))))

    (it "of object with nested validations"
      (let [spec {:type     {:foo   {:type :keyword}
                             :hello {:type :string :validate (partial = "world")}}
                  :validate :foo}]
        (should= false (schema/valid-value? spec {:foo :bar}))
        (should= false (schema/valid-value? spec {:hello "world"}))
        (should= false (schema/valid-value? spec {:foo :bar :hello "worlds"}))
        (should= true (schema/valid-value? spec {:foo :bar :hello "world"}))))

    (it "of seq with outer validation happens after inner validation"
      (let [spec {:type :seq :spec {:type :int :validate pos? :message "neg"} :validate seq :message "empty"}]
        (should= [123] (schema/-process-spec-on-value :validate spec [123]))
        (should= "neg" (->> [-123] (schema/-process-spec-on-value :validate spec) first :message))
        (should= "empty" (->> [] (schema/-process-spec-on-value :validate spec) :message))))

    (it "missing multiple type coercer"
      (should-be-nil (schema/validate-value! {:type [:blah]} nil))
      (should-throw stdex "[:int] expected" (schema/validate-value! {:type [:int]} :foo))
      (should-throw stdex "unhandled validation type: :blah" (schema/validate-value! {:type [:blah]} [:foo])))

    (it "of invalid entity"
      (let [result (schema/validate pet invalid-pet)
            errors (schema/error-map result)]
        (should= true (schema/error? result))
        (should= "must be a pet species" (schema/error-message (:species errors)))
        (should= "must be a date" (schema/error-message (:birthday errors)))
        (should= "must be unit in feet" (schema/error-message (:length errors)))
        (should= "must be between 0 and 999" (schema/error-message (:teeth errors)))
        (should= "must be nice and unique name" (schema/error-message (:name errors)))
        (should= "must be a valid reference format" (schema/error-message (:owner errors)))
        (should= "must be an integer" (schema/error-message (:age (:parent errors))))))

    (it "of valid entity"
      (let [result (schema/validate pet valid-pet)]
        (should= false (schema/error? result))))

    (it "of entity with missing(required) fields"
      (let [result   (schema/validate pet {})
            failures (schema/error-map result)]
        (should= true (schema/error? result))
        (should-contain :owner failures)
        (should-not-contain :birthday failures)))

    (it "of entity level validations"
      (let [spec    (assoc pet :* {:species {:validate #(not (and (= "snake" (:species %))
                                                                  (= "Fluffy" (:name %))))
                                             :message  "Snakes are not fluffy!"}})
            result1 (schema/validate spec valid-pet)
            result2 (schema/validate spec (assoc valid-pet :name "Fluffy" :species "snake"))]
        (should= false (schema/error? result1))
        (should= true (schema/error? result2))
        (should= "Snakes are not fluffy!" (:species (schema/message-map result2)))))

    (it ":validations validations/message pairs"
      (let [spec    (merge-with merge pet
                                {:species {:validate    nil
                                           :validations [{:validate nil? :message "species not nil"}]}
                                 :name    {:validate    nil
                                           :validations [{:validate [schema/present? #(= "blah" %)] :message "bad name"}]}})
            result1 (schema/validate spec (assoc valid-pet :species nil :name "blah"))
            result2 (schema/validate spec (assoc valid-pet :name "Fluffy" :species "snake"))]
        (should= false (schema/error? result1))
        (should= true (schema/error? result2))
        (should= "species not nil" (:species (schema/message-map result2)))
        (should= "bad name" (:name (schema/message-map result2)))))

    (it "validations stop on first failure"
      (let [spec    (merge-with merge pet
                                {:species {:validate #(str/starts-with? % "s")
                                           :message  "not s species"
                                           :validations
                                           [{:validate #(str/ends-with? % "e") :message "not *e species"}
                                            {:validate #(= "snake" %) :message "not snake"}]}})
            result1 (schema/validate spec (assoc valid-pet :species "snake"))
            result2 (schema/validate spec (assoc valid-pet :species "swine"))
            result3 (schema/validate spec (assoc valid-pet :species "snail"))
            result4 (schema/validate spec (assoc valid-pet :species "crab"))]
        (should= false (schema/error? result1))
        (should= true (schema/error? result2))
        (should= "not snake" (:species (schema/message-map result2)))
        (should= true (schema/error? result3))
        (should= "not *e species" (:species (schema/message-map result3)))
        (should= true (schema/error? result4))
        (should= "not s species" (:species (schema/message-map result4)))))

    (it ":validation at entity level"
      (let [spec    (assoc pet :* {:species {:validations [{:validate #(not (and (= "snake" (:species %))
                                                                                 (= "Fluffy" (:name %))))
                                                            :message  "Snakes are not fluffy!"}]}})
            result1 (schema/validate spec valid-pet)
            result2 (schema/validate spec (assoc valid-pet :name "Fluffy" :species "snake"))]
        (should= false (schema/error? result1))
        (should= true (schema/error? result2))
        (should= "Snakes are not fluffy!" (:species (schema/message-map result2)))))

    (it "nested required field"
      (let [child  {:value {:type :string}}
            parent {:child {:type child :validations [{:validate some? :message "is required"}]}}]
        (should= {:child "is required"} (schema/validate-message-map parent {}))))

    (it "removes extra fields"
      (let [crufty (assoc valid-pet :garbage "yuk!")
            result (schema/validate pet crufty)]
        (should-be-nil (:garbage result))
        (should-not-contain :garbage result)))

    )

  (it "error info"
    (let [result (schema/validate pet {:species "frog"})
          error  (:species result)]
      (should= true (schema/field-error? error))
      (should= "must be a pet species" (schema/error-message error))
      (should= "must be a pet species" (-> error schema/error-exception ex-message))
      (should= "frog" (schema/error-value error))
      (should-be-nil (schema/error-type error))
      (should= #{:exception :value} (set (keys (schema/error-data error))))))

  )
