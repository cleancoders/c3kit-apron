(ns c3kit.apron.schema.coercers-spec
  (:require
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema-spec :refer [pet valid-pet now home a-uuid stdex]]
    [c3kit.apron.schema.coercions :as c]
    [c3kit.apron.utilc :as utilc]
    [clojure.string :as str]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should should= should-be-a should-be-nil
                                                      should-contain should-not should-not-contain should-throw]]))

(describe "schema.coercions (lex bundle)"

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

  (context "message wiring"

    (it "coercer message surfaces through coerce-value!"
      (should-throw stdex "must be a string"
                    (schema/coerce-value! {:type :any :coercions [:trim]} 42)))
    )
  )

(describe "Schema coercion"

  (it "to boolean"
    (should-be-nil (schema/->boolean nil))
    (should= false (schema/->boolean "false"))
    (should= false (schema/->boolean "FALSE"))
    (should= true (schema/->boolean "abc"))
    (should= true (schema/->boolean 1))
    (should= true (schema/->boolean 3.14)))

  (it "to string"
    (should-be-nil (schema/->string nil))
    (should= "abc" (schema/->string "abc"))
    (should= "1" (schema/->string 1))
    (should= "3.14" (schema/->string 3.14)))

  (it "to keyword"
    (should-be-nil (schema/->keyword nil))
    (should= :abc (schema/->keyword "abc"))
    (should= :abc (schema/->keyword ":abc"))
    (should= :abc/xyz (schema/->keyword "abc/xyz"))
    (should= :abc/xyz (schema/->keyword ":abc/xyz"))
    (should= :1 (schema/->keyword 1))
    (should= :3.14 (schema/->keyword 3.14))
    (should= :foo (schema/->keyword :foo)))

  (it "to float"
    (should-be-nil (schema/->float nil))
    (should-be-nil (schema/->float ""))
    (should-be-nil (schema/->float "\t"))
    (should= 1.0 (schema/->float \1))
    (should= 1.0 (schema/->float 1))
    (should= 3.14 (schema/->float 3.14) 0.00001)
    (should= 3.14 (schema/->float "3.14") 0.00001)
    (should= 42.0 (schema/->float "42") 0.00001)
    (should= 3.14 (schema/->float 3.14M) 0.00001)
    (should-throw (schema/->float \a))
    (should-throw stdex (schema/->float "fooey")))

  (it "to int"
    (should-be-nil (schema/->int nil))
    (should-be-nil (schema/->int ""))
    (should-be-nil (schema/->int "\t"))
    (should= 1 (schema/->int \1))
    (should= 1 (schema/->int 1))
    (should= 3 (schema/->int 3.14))
    (should= 3 (schema/->int 3.9))
    (should= 42 (schema/->int "42"))
    (should= 3 (schema/->int "3.14"))
    (should= 3 (schema/->int 3.14M))
    (should-throw (schema/->int \a))
    (should-throw stdex (schema/->int "fooey"))
    (should-throw stdex (schema/->int :foo)))

  (it "to bigdec"
    (should-be-nil (schema/->bigdec nil))
    (should-be-nil (schema/->bigdec ""))
    (should-be-nil (schema/->bigdec "\t"))
    (should= 1M (schema/->bigdec \1))
    (should= 1M (schema/->bigdec 1))
    (should= 3.14M (schema/->bigdec 3.14))
    (should= 3.9M (schema/->bigdec 3.9))
    (should= 42M (schema/->bigdec "42"))
    (should= 3.14M (schema/->bigdec "3.14"))
    (should= 3.14M (schema/->bigdec 3.14M))
    (should-throw (schema/->bigdec \a))
    (should-throw stdex (schema/->bigdec "fooey")))

  (it "to date"
    (should-be-nil (schema/->date nil))
    (should-be-nil (schema/->date " \r\n\t"))
    (should= now (schema/->date now))
    (should= now (schema/->date (.getTime now)))
    (should-be-a #?(:clj java.util.Date :cljs js/Date) (schema/->date now))
    (should-throw stdex (schema/->date "now"))
    (should= now (schema/->date (pr-str now))))

  (it "to sql date"
    (should-be-nil (schema/->sql-date nil))
    (should-be-nil (schema/->sql-date " \r\n\t"))
    (should= #?(:clj (java.sql.Date. (.getTime now)) :cljs now) (schema/->sql-date now))
    (should= #?(:clj (java.sql.Date. (.getTime now)) :cljs now) (schema/->sql-date (.getTime now)))
    (should-be-a #?(:clj java.sql.Date :cljs js/Date) (schema/->sql-date now))
    (should-throw stdex (schema/->sql-date "now"))
    (should= #?(:clj (java.sql.Date. (.getTime now)) :cljs now) (schema/->sql-date (pr-str now))))

  (it "to sql timestamp"
    (should-be-nil (schema/->timestamp nil))
    (should-be-nil (schema/->timestamp " \r\n\t"))
    (should= #?(:bb now :clj (java.sql.Timestamp. (.getTime now)) :cljs now) (schema/->timestamp now))
    (should= #?(:bb now :clj (java.sql.Timestamp. (.getTime now)) :cljs now) (schema/->timestamp (.getTime now)))
    (should-be-a #?(:bb java.util.Date :clj java.sql.Timestamp :cljs js/Date) (schema/->timestamp now))
    #?(:bb  (should-be-a java.util.Date (schema/->timestamp (java.sql.Date. (.getTime now))))
       :clj (should-be-a java.sql.Timestamp (schema/->timestamp (java.sql.Date. (.getTime now)))))
    (should-throw stdex (schema/->timestamp "now"))
    (should= #?(:bb now :clj (java.sql.Timestamp. (.getTime now)) :cljs now) (schema/->timestamp (pr-str now))))

  (it "to uri"
    (should-be-nil (schema/->uri nil))
    (should= home (schema/->uri home))
    (should= home (schema/->uri "http://apron.co"))
    (should-throw stdex (schema/->uri 123)))

  (it "to uuid"
    (should-be-nil (schema/->uuid nil))
    (should= a-uuid (schema/->uuid a-uuid))
    (should= a-uuid (schema/->uuid "1f50be30-1373-40b7-acce-5290b0478fbe"))
    (should= (schema/->uuid "53060bf1-971a-4d18-80fc-92a3112afd6e") (schema/->uuid #uuid "53060bf1-971a-4d18-80fc-92a3112afd6e"))
    (let [uuid2        (ccc/new-uuid)
          transit-uuid (utilc/<-transit (utilc/->transit uuid2))]
      (should= uuid2 (schema/->uuid transit-uuid)))
    (should-throw stdex (schema/->uuid 123)))

  (it "to seq"
    (should= [] (schema/->seq nil))
    (should= ["foo"] (schema/->seq "foo"))
    (should= ["foo"] (schema/->seq ["foo"]))
    (should= ["foo" "bar"] (schema/->seq ["foo" "bar"])))

  (context "from spec"

    (it "with missing type"
      (should-throw stdex "invalid spec: {}" (schema/coerce-value! {} 123)))

    (it "of boolean"
      (should= true (schema/coerce-value! {:type :boolean} 123)))

    (it "of string"
      (should= "123" (schema/coerce-value! {:type :string} 123)))

    (it "of int"
      (should= 123 (schema/coerce-value! {:type :int} "123.4")))

    (it "of ref"
      (should= 123 (schema/coerce-value! {:type :ref} "123.4")))

    (it "of float"
      (should= 123.4 (schema/coerce-value! {:type :float} "123.4") 0.0001)
      (should= 123.4 (schema/coerce-value! {:type :double} "123.4") 0.0001))

    (it "of bigdec"
      (should= 123.4M (schema/coerce-value! {:type :bigdec} "123.4")))

    (it "with custom coercions"
      (let [spec {:type :int :coerce [str/trim reverse #(apply str %)]}]
        (should= 321 (schema/coerce-value! spec " 123\t"))))

    (it "of schema"
      (let [spec  {:type {:name {:type :string :coerce str/trim}}}
            value {:name "  fred "}]
        (should= {:name "fred"} (schema/coerce-value! spec value))))

    (it "of multi schema"
      (let [spec  {:type [{:name {:type :string :coerce str/trim}}]}
            value {:name "  fred "}]
        (should= [{:name "fred"}] (schema/coerce-value! spec [value]))))

    (it "of object with custom coercions"
      (let [spec  {:type   {:name {:type :string}}
                   :coerce (constantly {:name "billy"})}
            value "blah"]
        (should= {:name "billy"} (schema/coerce-value! spec value))))

    (it "of object with nested coercions"
      (let [spec {:type   {:name {:type :string :coerce str/trim}}
                  :coerce (constantly {:name "  billy "})}]
        (should= {:name "billy"} (schema/coerce-value! spec "blah"))))

    (it ", custom coercions happen before type coercion"
      (let [spec {:type :string :coerce #(* % %)}]
        (should= "16" (schema/coerce-value! spec 4))))

    (it "of seq"
      (let [result (schema/coerce-value! {:type [:float]} ["123.4" 321 3.1415])]
        (should= 123.4 (first result) 0.0001)
        (should= 321.0 (second result) 0.0001)
        (should= 3.1415 (last result) 0.0001)))

    (it "of seq from a set"
      (let [result (schema/coerce-value! {:type [:long]} #{"123" 321 3.14})]
        (should-contain 123 result)
        (should-contain 321 result)
        (should-contain 3 result)))

    (it "of seq with missing spec"
      (let [result (schema/coerce-value! {:type :seq} [1 "2"])]
        (should= 1 (first result))
        (should= "2" (last result))))

    (it "of seq with inner coercion"
      (let [result (schema/coerce-value! {:type :seq :spec {:type :float :coerce inc}} [321 3.1415])]
        (should= 322.0 (first result) 0.0001)
        (should= 4.1415 (last result) 0.0001)))

    (it "missing multiple type coercer"
      (should-be-nil (schema/coerce-value! {:type [:blah]} nil))
      (should-throw stdex "[:long] expected" (schema/coerce-value! {:type [:long]} :foo))
      (should-throw stdex "unhandled coercion type: :blah" (schema/coerce-value! {:type [:blah]} ["foo"])))

    (it "of seq with outer coercion happens before inner coercion"
      (let [result (schema/coerce-value! {:type :seq :spec {:type :int} :coerce seq} "123")]
        (should= [1 2 3] result))
      (let [result (schema/coerce-value! {:type :seq :spec {:type :int} :coerce schema/->seq} "123")]
        (should= [123] result))
      (should-throw (schema/coerce-value! {:type :seq :spec {:type :int} :coerce #(map inc %)} :123))) ;; cljs will map over a string

    (it "of entity"
      (let [result (schema/coerce pet {:species  "dog"
                                       :birthday now
                                       :length   "2.3"
                                       :teeth    24.2
                                       :name     "Fluff"
                                       :owner    "12345"
                                       :uuid     a-uuid})]
        (should= false (schema/error? result))
        (should= "dog" (:species result))
        (should= now (:birthday result))
        (should= 2.3 (:length result) 0.001)
        (should= 24 (:teeth result))
        (should= "Fluffy" (:name result))
        (should= 12345 (:owner result))
        (should= a-uuid (:uuid result))))

    (it "of entity, nil values omitted"
      (let [result (schema/coerce pet {:name "Fido"})]
        (should= false (schema/error? result))
        (should= "Fidoy" (:name result))
        (should-not-contain :length result)
        (should-not-contain :species result)
        (should-not-contain :teeth result)
        (should-not-contain :birthday result)
        (should-not-contain :owner result)
        (should-not-contain :uuid result)))

    (it "of entity level coercions"
      (let [schema (assoc pet :* {:stage-name {:type   :string
                                               :coerce #(str (:name %) " the " (:species %))}})
            result (schema/coerce schema valid-pet)]
        (should= "Fluffyy the dog" (:stage-name result))))

    (it "coerces to nil"
      (let [schema (assoc-in pet [:name :coerce] {:coerce (constantly nil)})
            result (schema/coerce schema (dissoc valid-pet :name))]
        (should-not-contain :name result)))

    (it "entity level coerces to nil"
      (let [schema (assoc pet :* {:name {:type :string :coerce (constantly nil)}})
            result (schema/coerce schema valid-pet)]
        (should-not-contain :name result)))

    (it "removes extra fields"
      (let [crufty (assoc valid-pet :garbage "yuk!")
            result (schema/coerce pet crufty)]
        (should-be-nil (:garbage result))
        (should-not-contain :garbage result)))

    (it "accepts a wrapped :map spec as the root schema"
      (let [wrapped {:type :map :name :root :schema pet}
            bare    (schema/coerce pet valid-pet)
            wrapd   (schema/coerce wrapped valid-pet)]
        (should= false (schema/error? wrapd))
        (should= bare wrapd)))

    (it ":coercions coerce/message pairs"
      (let [spec    (merge-with merge pet
                                {:name {:coerce     nil
                                        :coercions  [{:coerce str/trim} {:coerce str/upper-case}]}})
            result1 (schema/coerce spec (assoc valid-pet :name "  fluffy  "))]
        (should= false (schema/error? result1))
        (should= "FLUFFY" (:name result1))))

    (it ":coercions use the entry :message on failure"
      (let [spec   (merge-with merge pet
                               {:name {:coerce    nil
                                       :coercions [{:coerce  (fn [_] (throw (ex-info "boom" {})))
                                                    :message "name coercion failed"}]}})
            result (schema/coerce spec valid-pet)]
        (should= true (schema/error? result))
        (should= "name coercion failed" (:name (schema/message-map result)))))

    (it "coercions stop on first failure"
      (let [spec   (merge-with merge pet
                               {:name {:coerce    nil
                                       :coercions [{:coerce str/upper-case :message "first"}
                                                   {:coerce (fn [_] (throw (ex-info "boom" {})))
                                                    :message "second"}
                                                   {:coerce (fn [_] (throw (ex-info "later" {})))
                                                    :message "third"}]}})
            result (schema/coerce spec valid-pet)]
        (should= true (schema/error? result))
        (should= "second" (:name (schema/message-map result)))))

    (it ":coercions at entity level"
      (let [schema (assoc pet :* {:stage-name {:type      :string
                                               :coercions [{:coerce #(str (:name %) " the " (:species %))}
                                                           {:coerce #(str/upper-case (:stage-name %))}]}})
            result (schema/coerce schema valid-pet)]
        (should= "FLUFFYY THE DOG" (:stage-name result))))

    )

  (context "multi field"

    (it "with nil value"
      (should-be-nil (schema/coerce-value! {:type [:int]} nil)))

    (it "with empty list"
      (should= () (schema/coerce-value! {:type [:int]} ())))

    (it "entity - with an empty seq value"
      (let [result (schema/coerce pet {:colors []})]
        (should= [] (:colors result))))
    )

  (it "nested entity as a seq is coerced into a map"
    (let [result (schema/coerce pet {:parent [[:name "Fido"] [:age "12"]]})]
      (should= {:name "Fido" :age 12} (:parent result))))

  (it "message is used"
    (let [result (schema/coerce pet {:length "foo"})]
      (should= "must be unit in feet" (:length (schema/message-map result)))
      (should= "can't coerce \"foo\" to float" (-> result :length schema/error-exception ex-message))))

  (it "message at entity level is used"
    (let [schema (assoc pet :* {:name {:coerce (fn [_] (throw (ex-info "blah" {}))) :message "boom!"}})
          result (schema/coerce schema valid-pet)]
      (should= "boom!" (:name (schema/message-map result)))
      (should= "blah" (-> result :name schema/error-exception ex-message))))

  (it "error info"
    (let [result (schema/coerce pet {:length "foo"})
          error  (:length result)]
      (should= true (schema/field-error? error))
      (should= "must be unit in feet" (schema/error-message error))
      (should= "can't coerce \"foo\" to float" (-> error schema/error-exception ex-message))
      (should= "foo" (schema/error-value error))
      (should= "float" (schema/error-type error))
      (should= #{:exception :type :value} (set (keys (schema/error-data error))))))
  )
