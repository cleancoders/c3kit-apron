(ns c3kit.apron.schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.time :as time]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [focus-it context describe it should= should-contain should-not-contain should-throw should-be-a
                                                      should should-not should-be-nil with-stubs stub should-not-have-invoked]]
    [clojure.string :as str]
    [c3kit.apron.utilc :as utilc]
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.schema :as s])
  #?(:clj
     (:import (java.net URI)
              (java.util UUID))))

(def stdex
  #?(:clj  clojure.lang.ExceptionInfo
     :cljs cljs.core/ExceptionInfo))

(def pet
  {:kind        (schema/kind :pet)
   :id          schema/id
   :species     {:type     :string
                 :validate [#{"dog" "cat" "snake"}]
                 :message  "must be a pet species"}
   :birthday    {:type    :instant
                 :message "must be a date"}
   :length      {:type    :float
                 :message "must be unit in feet"}
   :teeth       {:type     :int
                 :validate [#(and (<= 0 %) (<= % 999))]
                 :message  "must be between 0 and 999"}
   :name        {:type     :string
                 :db       [:unique-value]
                 :coerce   #(str % "y")
                 :validate #(> (count %) 2)
                 :message  "must be nice and unique name"}
   :owner       {:type     :ref
                 :validate [schema/present?]
                 :message  "must be a valid reference format"}
   :colors      {:type [:string] :message "must be a string"}
   ;:ears        {:type     :seq
   ;              :spec     {:type :keyword :validate #{:pointy :floppy} :message "bad ear type"}
   ;              :validate (schema/nil-or? #(= 2 (count %))) :message "must have 2 types"}
   :uuid        {:type :uuid
                 :db   [:unique-identity]}
   :parent      {:type {:name {:type :string}
                        :age  {:type :int}}}
   :temperament {:type :kw-ref}})

(def temperaments
  {:enum   :temperament
   :values [:wild :domestic]})

(def owner
  {:kind (schema/kind :owner)
   :name {:type :string}
   :pet  {:type pet}})

(def household
  {:kind (schema/kind :household)
   :size {:type :long}
   :pets {:type [pet]}})

(def now (new #?(:clj java.util.Date :cljs js/Date)))
(def home #?(:clj (URI/create "http://apron.co") :cljs "http://apron.co"))
(def a-uuid #?(:clj (UUID/fromString "1f50be30-1373-40b7-acce-5290b0478fbe") :cljs (uuid "1f50be30-1373-40b7-acce-5290b0478fbe")))

(def valid-pet {:species  "dog"
                :birthday now
                :length   2.5
                :teeth    24
                :name     "Fluffy"
                :owner    12345
                :color    ["brown" "white"]
                :uuid     a-uuid})
(def invalid-pet {:species  321
                  :birthday "yesterday"
                  :length   "foo"
                  :teeth    1000
                  :name     ""
                  :owner    nil
                  :parent   {:age :foo}})

(describe "Schema"

  (context "coercion"

    (it "to boolean"
      (should= nil (schema/->boolean nil))
      (should= false (schema/->boolean "false"))
      (should= false (schema/->boolean "FALSE"))
      (should= true (schema/->boolean "abc"))
      (should= true (schema/->boolean 1))
      (should= true (schema/->boolean 3.14)))

    (it "to string"
      (should= nil (schema/->string nil))
      (should= "abc" (schema/->string "abc"))
      (should= "1" (schema/->string 1))
      (should= "3.14" (schema/->string 3.14)))

    (it "to keyword"
      (should= nil (schema/->keyword nil))
      (should= :abc (schema/->keyword "abc"))
      (should= :abc (schema/->keyword ":abc"))
      (should= :abc/xyz (schema/->keyword "abc/xyz"))
      (should= :abc/xyz (schema/->keyword ":abc/xyz"))
      (should= :1 (schema/->keyword 1))
      (should= :3.14 (schema/->keyword 3.14))
      (should= :foo (schema/->keyword :foo)))

    (it "to float"
      (should= nil (schema/->float nil))
      (should= nil (schema/->float ""))
      (should= nil (schema/->float "\t"))
      (should= 1.0 (schema/->float \1))
      (should= 1.0 (schema/->float 1))
      (should= 3.14 (schema/->float 3.14) 0.00001)
      (should= 3.14 (schema/->float "3.14") 0.00001)
      (should= 42.0 (schema/->float "42") 0.00001)
      (should= 3.14 (schema/->float 3.14M) 0.00001)
      (should-throw (schema/->float \a))
      (should-throw stdex (schema/->float "fooey")))

    (it "to int"
      (should= nil (schema/->int nil))
      (should= nil (schema/->int ""))
      (should= nil (schema/->int "\t"))
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
      (should= nil (schema/->bigdec nil))
      (should= nil (schema/->bigdec ""))
      (should= nil (schema/->bigdec "\t"))
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
      (should= nil (schema/->date nil))
      (should= nil (schema/->date " \r\n\t"))
      (should= now (schema/->date now))
      (should= now (schema/->date (.getTime now)))
      (should-be-a #?(:clj java.util.Date :cljs js/Date) (schema/->date now))
      (should-throw stdex (schema/->date "now"))
      (should= now (schema/->date (pr-str now))))

    (it "to sql date"
      (should= nil (schema/->sql-date nil))
      (should= nil (schema/->sql-date " \r\n\t"))
      (should= #?(:clj (java.sql.Date. (.getTime now)) :cljs now) (schema/->sql-date now))
      (should= #?(:clj (java.sql.Date. (.getTime now)) :cljs now) (schema/->sql-date (.getTime now)))
      (should-be-a #?(:clj java.sql.Date :cljs js/Date) (schema/->sql-date now))
      (should-throw stdex (schema/->sql-date "now"))
      (should= #?(:clj (java.sql.Date. (.getTime now)) :cljs now) (schema/->sql-date (pr-str now))))

    (it "to sql timestamp"
      (should= nil (schema/->timestamp nil))
      (should= nil (schema/->timestamp " \r\n\t"))
      (should= #?(:clj (java.sql.Timestamp. (.getTime now)) :cljs now) (schema/->timestamp now))
      (should= #?(:clj (java.sql.Timestamp. (.getTime now)) :cljs now) (schema/->timestamp (.getTime now)))
      (should-be-a #?(:clj java.sql.Timestamp :cljs js/Date) (schema/->timestamp now))
      #?(:clj (should-be-a java.sql.Timestamp (schema/->timestamp (java.sql.Date. (.getTime now)))))
      (should-throw stdex (schema/->timestamp "now"))
      (should= #?(:clj (java.sql.Timestamp. (.getTime now)) :cljs now) (schema/->timestamp (pr-str now))))

    (it "to uri"
      (should= nil (schema/->uri nil))
      (should= home (schema/->uri home))
      (should= home (schema/->uri "http://apron.co"))
      (should-throw stdex (schema/->uri 123)))

    (it "to uuid"
      (should= nil (schema/->uuid nil))
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
        (should= nil (schema/coerce-value! {:type [:blah]} nil))
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
          (should= nil (:garbage result))
          (should-not-contain :garbage result)))

      )

    (context "multi field"

      (it "with nil value"
        (should= nil (schema/coerce-value! {:type [:int]} nil)))

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

  (context "validation"

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
        #?(:clj (should= true (schema/valid-value? {:type :timestamp} (java.sql.Timestamp. (System/currentTimeMillis)))))
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
        (should= nil (schema/validate-value! {:type [:blah]} nil))
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
          (should= "is invalid" (schema/error-message (:age (:parent errors))))))

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
                                             :validations [{:validate [s/present? #(= "blah" %)] :message "bad name"}]}})
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
          (should= nil (:garbage result))
          (should-not-contain :garbage result)))

      )

    (it "error info"
      (let [result (schema/validate pet {:species "frog"})
            error  (:species result)]
        (should= true (schema/field-error? error))
        (should= "must be a pet species" (schema/error-message error))
        (should= "must be a pet species" (-> error schema/error-exception ex-message))
        (should= "frog" (schema/error-value error))
        (should= nil (schema/error-type error))
        (should= #{:exception :value} (set (keys (schema/error-data error))))))

    )

  (context "conforming"

    (it "with failed coercion"
      (should-throw stdex "can't coerce \"foo\" to int" (schema/conform-value! {:type :int} "foo"))
      (should-throw stdex "oh no!" (schema/conform-value! {:type :int :message "oh no!"} "foo")))

    (it "with failed validation"
      (should-throw stdex "oh no!"
                    (schema/conform-value! {:type :int :validate even? :message "oh no!"} "123")))

    (it "of int the must be present"
      (should-throw stdex "is invalid"
                    (schema/conform-value! {:type :int :validate [schema/present?]} ""))
      (should-throw stdex "is invalid"
                    (schema/conform-value! {:type :long :validate schema/present?} "")))

    (it "success"
      (should= 123 (schema/conform-value! {:type :int :message "oh no!"} "123")))

    (it "of sequentials"
      (should= [123 321 3] (schema/conform-value! {:type [:int]} ["123.4" 321 3.1415])))

    (it "of sequentials - empty"
      (should= [] (schema/conform-value! {:type [:int]} []))
      (should= nil (schema/conform-value! {:type [:int]} nil))
      (should-throw stdex "[:int] expected" (schema/conform-value! {:type [:int]} "foo")))

    (it "of object"
      (let [spec {:type {:foo {:type :keyword}}}]
        (should= {} (schema/conform-value! spec {}))
        (should= {:foo :bar} (schema/conform-value! spec {:foo :bar :hello "world"}))))

    (it "of multi object"
      (let [spec {:type [{:foo {:type :keyword}}]}]
        (should= [{}] (schema/conform-value! spec [{}]))
        (should= [{:foo :bar}] (schema/conform-value! spec [{:foo :bar :hello "world"}]))
        (should= nil (schema/conform-value! spec nil))))

    (it "a valid entity"
      (let [result (schema/conform pet {:species  "dog"
                                        :birthday now
                                        :length   "2.3"
                                        :teeth    24.2
                                        :name     "Fluff"
                                        :owner    "12345"})]
        (should= false (schema/error? result))
        (should= "dog" (:species result))
        (should= now (:birthday result))
        (should= 2.3 (:length result) 0.001)
        (should= 24 (:teeth result))
        (should= "Fluffy" (:name result))
        (should= 12345 (:owner result))))

    (it "entity - with an empty seq value"
      (let [result (schema/conform pet {:species  "dog"
                                        :birthday now
                                        :length   "2.3"
                                        :teeth    24.2
                                        :name     "Fluff"
                                        :owner    "12345"
                                        :colors   []})]
        (should= false (schema/error? result))
        (should= [] (:colors result))))

    (it "of entity level operations"
      (let [spec    (assoc pet :* {:species {:type     :ignore
                                             :coerce   (constantly "snake")
                                             :validate #(not (and (= "snake" (:species %))
                                                                  (= "Fluffyy" (:name %))))
                                             :message  "Snakes are not fluffy!"}})
            result1 (schema/conform spec (assoc valid-pet :name "Slimey"))
            result2 (schema/conform spec valid-pet)]
        (should= false (schema/error? result1))
        (should= "snake" (:species result1))
        (should= true (schema/error? result2))
        (should= "Snakes are not fluffy!" (:species (schema/message-map result2)))))

    (it "of entity level operations on nil values"
      (let [spec   (assoc pet
                     :* {:length {:validate #(or (nil? (:length %))
                                                 (pos? (:length %)))
                                  :message  "must be a positive number"}})
            result (schema/conform spec (dissoc valid-pet :length))]
        (should= false (schema/error? result))
        (should-not-contain :length result)))

    (it "a invalid entity"
      (let [result (schema/conform pet invalid-pet)]
        (should= true (schema/error? result))
        (should= "must be a pet species" (schema/error-message (:species result)))
        (should= "must be a date" (schema/error-message (:birthday result)))
        (should= "must be unit in feet" (schema/error-message (:length result)))
        (should= "must be between 0 and 999" (schema/error-message (:teeth result)))
        (should= "must be nice and unique name" (schema/error-message (:name result)))
        (should= "must be a valid reference format" (schema/error-message (:owner result)))
        (should= "can't coerce :foo to int" (schema/error-message (:age (:parent result))))))

    (it "removes extra fields"
      (let [crufty (assoc valid-pet :garbage "yuk!")
            result (schema/conform pet crufty)]
        (should= nil (:garbage result))
        (should-not-contain :garbage result)))

    (it ":validations errors"
      (let [spec    (merge-with merge pet
                                {:species {:validate    nil
                                           :validations [{:validate nil? :message "species not nil"}]}
                                 :name    {:validate    nil
                                           :coerce      nil
                                           :validations [{:validate [s/present? #(= "blah" %)] :message "bad name"}]}})
            result1 (schema/conform spec (assoc valid-pet :species nil :name "blah"))
            result2 (schema/conform spec (assoc valid-pet :name "Fluffy" :species "snake"))]
        (should= false (schema/error? result1))
        (should= true (schema/error? result2))
        (should= "species not nil" (:species (schema/message-map result2)))
        (should= "bad name" (:name (schema/message-map result2)))))

    (it "with invalid schema"
      (should-throw stdex "invalid spec: {:type \"hi\"}" (schema/conform {:foo {:type "hi"}} {:foo "bar"})))

    (it "coercing a string to seq"
      (let [path-schema {:path {:type :seq :coerce utilc/<-edn :spec {:type :map :schema {:foo {:type :string}}}}}
            entity      {:path (pr-str [{:foo "foo"}])}
            result      (schema/conform path-schema entity)]
        (should= nil (schema/message-map result))))

    (it "coercing a string to map"
      (let [path-schema {:path {:type    :map
                                :coerce  utilc/<-edn
                                :message "oops"
                                :schema  {:foo {:type :string :validate str/blank? :coerce #(apply str (rest %))}}}}]
        (let [valid (schema/conform path-schema {:path (pr-str {:foo ""})})]
          (should= nil (schema/message-map valid))
          (should= {:foo ""} (:path valid)))
        (let [invalid (schema/conform path-schema {:path (pr-str {:foo "foo"})})]
          (should= "is invalid" (get-in (schema/message-map invalid) [:path :foo])))
        (let [not-a-map (schema/conform path-schema {:path (pr-str "im-not-a-map")})]
          (should= "oops" (get-in (schema/message-map not-a-map) [:path])))
        (let [valid-nested (schema/conform path-schema {:path (pr-str {:foo "f"})})]
          (should= nil (schema/message-map valid-nested)))))

    (it "required map field"
      (let [schema {:thing {:type :map :schema {:field {:type :any}} :validations [schema/required]}}]
        (should= "is required" (:thing (schema/conform-message-map schema {})))))

    )



  (context "error messages"

    (it "are nil when there are none"
      (should= nil (schema/message-map {})))

    (it "are only given for failed results"
      (should= {:name "must be nice and unique name"}
               (-> {:name (schema/-process-error :validate {:message "must be nice and unique name"})}
                   (schema/message-map))))

    (it "with missing message"
      (should= {:foo "blah"}
               (-> {:foo (schema/-process-error :validate {:exception (ex-info "blah" {})})}
                   (schema/message-map))))

    (it "does not validate nil values against schema types"
      (let [jerry {:name "Jerry" :pet nil}]
        (should-be-nil (schema/message-map (schema/coerce owner jerry)))
        (should-be-nil (schema/message-map (schema/validate owner jerry)))
        (should-be-nil (schema/message-map (schema/conform owner jerry)))))

    (it "validates false values against schema types"
      (let [jerry {:name "Jerry" :pet false}]
        (should= {:pet "can't coerce false to map"} (schema/message-map (schema/coerce owner jerry)))
        (should= {:pet "is invalid"} (schema/message-map (schema/validate owner jerry)))
        (should= {:pet "can't coerce false to map"} (schema/message-map (schema/conform owner jerry)))))

    (it "does not require collection on seq of schema types"
      (let [house {:size 10 :pets nil}]
        (should-be-nil (schema/message-map (schema/coerce household house)))
        (should-be-nil (schema/message-map (schema/validate household house)))
        (should-be-nil (schema/message-map (schema/conform household house)))))

    (it "for single, top-level error"
      (let [invalid-pet (assoc valid-pet :name "")]
        (should-be-nil (schema/message-map (schema/coerce pet invalid-pet)))
        (should= {:name "must be nice and unique name"} (schema/message-map (schema/validate pet invalid-pet)))
        (should= {:name "must be nice and unique name"} (schema/message-map (schema/conform pet invalid-pet)))))

    (it "for multiple, top-level errors"
      (let [invalid-pet (assoc valid-pet :name 123 :species :cat)]
        (should= {:name "must be nice and unique name", :species "must be a pet species"}
                 (schema/message-map (schema/validate pet invalid-pet)))))

    (it "specifies idx when inside sequential structure"
      (let [invalid-pet {:species  "dog"
                         :birthday now
                         :length   2.5
                         :teeth    24
                         :name     "Fluffy"
                         :owner    12345
                         :colors   ["brown" "white" 123 "red" 456]
                         :uuid     a-uuid}]
        (should= {:colors {2 "must be a string"
                           4 "must be a string"}}
                 (schema/message-map (schema/validate pet invalid-pet)))
        (should-be-nil (schema/message-map (schema/validate pet valid-pet)))))

    (it "specifies individual errors within nested entities"
      (let [invalid-owner {:pet invalid-pet}
            valid-owner   {:pet valid-pet}]
        (should= {:pet {:parent   {:age "is invalid"}
                        :name     "must be nice and unique name"
                        :species  "must be a pet species"
                        :birthday "must be a date"
                        :teeth    "must be between 0 and 999"
                        :length   "must be unit in feet"
                        :owner    "must be a valid reference format"}}
                 (schema/message-map (schema/validate owner invalid-owner)))
        (should-be-nil (schema/message-map (schema/validate owner valid-owner)))))

    (it "specifies idx for invalid nested entity inside sequential structure"
      (let [invalid-household {:pets [valid-pet invalid-pet valid-pet invalid-pet]}
            error             {:parent   {:age "is invalid"}
                               :name     "must be nice and unique name"
                               :species  "must be a pet species"
                               :birthday "must be a date"
                               :teeth    "must be between 0 and 999"
                               :length   "must be unit in feet"
                               :owner    "must be a valid reference format"}]

        (should= {:pets {1 error 3 error}} (schema/message-map (schema/validate household invalid-household)))))

    (it "message-seq flat"
      (let [result (schema/message-seq (schema/conform pet (dissoc invalid-pet :parent)))]
        (should-contain "name must be nice and unique name" result)
        (should-contain "species must be a pet species" result)
        (should-contain "birthday must be a date" result)
        (should-contain "teeth must be between 0 and 999" result)
        (should-contain "length must be unit in feet" result)
        (should-contain "owner must be a valid reference format" result)))

    (it "message-seq nested"
      (let [invalid-household {:pets [valid-pet invalid-pet valid-pet invalid-pet]}
            result            (schema/message-seq (schema/conform household invalid-household))]
        (should-contain "pets.1.parent.age can't coerce :foo to int" result)
        (should-contain "pets.1.name must be nice and unique name" result)
        (should-contain "pets.3.parent.age can't coerce :foo to int" result)
        (should-contain "pets.3.name must be nice and unique name" result)
        ))
    )

  (context "presentation"

    (it "of int"
      (should= 123 (schema/present-value! {:type :int} 123))
      (should= 123 (schema/present-value! {:type :long} 123)))

    (it "of float"
      (should= 12.34 (schema/present-value! {:type :float} 12.34))
      (should= 12.34 (schema/present-value! {:type :double} 12.34)))

    (it "of string"
      (should= "foo" (schema/present-value! {:type :string} "foo")))

    (it "of date"
      (should= now (schema/present-value! {:type :instant} now)))

    (it "applies custom presenter"
      (should= 124 (schema/present-value! {:type :long :present inc} 123)))

    (it "ommited"
      (should-be-nil (schema/present-value! {:type :long :present schema/omit} 123)))

    (it "applies multiple custom presenters"
      (should= 62 (schema/present-value! {:type :long :present [inc #(/ % 2)]} 123)))

    (it "of sequentials"
      (should= [123 456] (schema/present-value! {:type [:int]} [123 456])))

    (it "of sequentials - empty"
      (should= [] (schema/present-value! {:type [:int]} [])))

    (it "of sequentials - nil"
      (should-be-nil (schema/present-value! {:type [:int]} nil)))

    (it "of sequentials with customs"
      (should= ["123" "456"] (schema/present-value! {:type [:int] :present str} [123 456]))
      (should= ["2" "3" "4" "5"] (schema/present-value! {:type [:float] :present [inc str]} [1 2 3 4])))

    (it "of sequentials when omitted"
      (should= [] (schema/present-value! {:type [:int] :present schema/omit} [123 456])))

    (it "of object"
      (let [spec  {:type {:age {:type :int :present str}}}
            value {:age 10}]
        (should= {:age "10"} (schema/present-value! spec value))))

    (it "of sequential object"
      (let [spec  {:type [{:age {:type :int :present str}}]}
            value [{:age 10}]]
        (should= [{:age "10"}] (schema/present-value! spec value))))

    (it "of object with customs"
      (let [spec  {:type    {:age {:type :int}}
                   :present pr-str}
            value {:age 10}]
        (should= "{:age 10}" (schema/present-value! spec value))))

    (it "of object with presentable attributes"
      (let [spec  {:type    {:age {:type :int :present inc}}
                   :present pr-str}
            value {:age 10}]
        (should= "{:age 11}" (schema/present-value! spec value))))

    (context "of entity"

      (it "doesn't present omitted (nil) results"
        (let [schema (assoc-in pet [:owner :present] schema/omit)
              result (schema/present schema (assoc valid-pet :owner "George"))]
          (should-not-contain :id result)
          (should-not-contain :owner result))
        )

      (it "with entity level presentation"
        (let [result (schema/present (assoc pet :* {:stage-name {:present #(str (:name %) " the " (:species %))}}) valid-pet)]
          (should= "Fluffy the dog" (:stage-name result))))

      (it "with error on entity level presentation"
        (let [result (schema/present (assoc pet :* {:stage-name {:present #(throw (ex-info "blah" {:x %}))}}) valid-pet)]
          (should= true (schema/error? result))
          (should-contain :stage-name (schema/error-map result))))

      (it "with error on entity level presentation!"
        (should-throw stdex
                      (schema/present!
                        (assoc pet :* {:stage-name {:present #(throw (ex-info "blah" {:x %}))}}) valid-pet)))
      )
    )

  (context "kind"

    (it "is enforced on validate!"
      (let [result (schema/validate pet (assoc valid-pet :kind :beast))
            kind   (:kind result)]
        (should= true (schema/field-error? kind))
        (should= true (schema/error? result))
        (should= ["kind mismatch; must be :pet"] (schema/message-seq result))))

    (it "can be left out"
      (should= false (schema/error? (schema/validate pet (dissoc valid-pet :kind)))))

    (it "will be added if missing by conform"
      (let [result (schema/conform pet (dissoc valid-pet :kind))]
        (should= false (schema/error? result))
        (should= :pet (:kind result))))

    )

  (context "entity level"

    (it "must be maps"
      (should= "can't coerce \"foo\" to map" (schema/error-message (schema/coerce pet "foo")))
      (should= "can't coerce \"foo\" to map" (schema/error-message (schema/validate pet "foo")))
      (should= "can't coerce \"foo\" to map" (schema/error-message (schema/conform pet "foo")))
      (should= "can't coerce \"foo\" to map" (schema/error-message (schema/present pet "foo"))))

    (it "errors don't get added until after"
      (let [schema {:foo {:type :string}
                    :bar {:type :string}
                    :*   {:foo {:validate seq}
                          :bar {:validate seq}}}]
        (should= {:foo "is invalid" :bar "is invalid"} (schema/validate-message-map schema {})))
      )

    )

  (context "merge schemas"

    (it "simple"
      (let [pet-a  {:kind    (schema/kind :pet)
                    :id      schema/id
                    :name    {:type :string}
                    :species {:type :string :validate :valid-species :message "invalid species"}
                    :*       {:name {:validate :valid-entity-name}}}
            pet-b  {:name    {:validate :valid-name :message "invalid name"}
                    :species {:coerce :coerce-species}
                    :color   {:type :string}
                    :*       {:species {:validate :valid-entity-species}}}
            result (schema/merge-schemas pet-a pet-b)]
        (should= schema/id (:id result))
        (should= {:type        :string :message "invalid name"
                  :validations [{:validate :valid-name, :message "invalid name"}]}
                 (:name result))
        (should= {:type        :string :message "invalid species"
                  :validations [{:validate :valid-species, :message "invalid species"}]
                  :coerce      :coerce-species} (:species result))
        (should= {:type :string} (:color result))
        (should= {:name    {:validate :valid-entity-name}
                  :species {:validate :valid-entity-species}}
                 (:* result))))

    (it "with validations"
      (let [pet-a  {:kind    (schema/kind :pet)
                    :id      schema/id
                    :name    {:type :string}
                    :species {:type :string :validations [{:validate :valid-species :message "invalid species"}]}
                    :*       {:name {:validations [{:validate :valid-entity-name}]}}}
            pet-b  {:name    {:validations [{:validate :valid-name :message "invalid name"}]}
                    :species {:validations [{:validate :valid-species2 :message "invalid2 species"}]}
                    :*       {:species {:validations [{:validate :valid-entity-species}]}
                              :name    {:validations [{:validate :valid-entity-name2}]}}}
            result (schema/merge-schemas pet-a pet-b)]
        (should= schema/id (:id result))
        (should= {:type :string :validations [{:validate :valid-name :message "invalid name"}]} (:name result))
        (should= {:type        :string
                  :validations [{:validate :valid-species :message "invalid species"}
                                {:validate :valid-species2 :message "invalid2 species"}]}
                 (:species result))
        (should= {:species {:validations [{:validate :valid-entity-species}]}
                  :name    {:validations [{:validate :valid-entity-name}
                                          {:validate :valid-entity-name2}]}} (:* result))))

    (it "conflicting validate"
      (let [pet-a  {:kind    (schema/kind :pet)
                    :id      schema/id
                    :species {:type :string :validate :valid-species :message "invalid species"}
                    :*       {:species {:validate :valid-entity-species :message "invalid entity species"}}}
            pet-b  {:species {:type :string :validate :valid-species2 :message "invalid species2"}
                    :*       {:species {:validate :valid-entity-species2 :message "invalid entity species2"}}}
            result (schema/merge-schemas pet-a pet-b)]
        (should= schema/id (:id result))
        (should= {:type        :string
                  :validations [{:validate :valid-species :message "invalid species"}
                                {:validate :valid-species2 :message "invalid species2"}]
                  :message     "invalid species2"} (:species result))

        (should= {:species {:message     "invalid entity species2"
                            :validations [{:validate :valid-entity-species :message "invalid entity species"}
                                          {:validate :valid-entity-species2 :message "invalid entity species2"}]}}
                 (:* result))))

    )

  (context "one-of"

    (it "no specs"
      (let [spec          {:type :one-of}
            coerce-result (schema/-process-spec-on-value :coerce spec 1)]
        (should= true (schema/error? coerce-result))
        (should= "one-of: empty specs" (:message coerce-result))))

    (it "no choice"
      (let [spec          {:type :one-of :specs []}
            coerce-result (schema/-process-spec-on-value :coerce spec 1)]
        (should= true (schema/error? coerce-result))
        (should= "one-of: empty specs" (:message coerce-result))))

    (it "one choice - coerce"
      (let [spec {:type :one-of :specs [{:type :int}]}]
        (should= nil (schema/-process-spec-on-value :coerce spec nil))
        (should= 1 (schema/-process-spec-on-value :coerce spec 1))
        (should= 2 (schema/-process-spec-on-value :coerce spec "2"))
        (should= "one-of: no matching spec" (:message (schema/-process-spec-on-value :coerce spec "blah")))))

    (it "one choice - validate"
      (let [spec {:type :one-of :specs [{:type :int :validate pos?}]}]
        (should= 1 (schema/-process-spec-on-value :validate spec 1))
        (should= 2 (schema/-process-spec-on-value :validate spec 2))
        (should= "one-of: no matching spec" (:message (schema/-process-spec-on-value :validate spec -3)))))

    (it "multiple choices"
      (let [spec {:type :one-of :specs [{:type :int :validate even?}
                                        {:type :int :validate pos?}
                                        {:type :string :validate #{"foo" "bar"}}]}]
        (should= 1 (schema/-process-spec-on-value :conform spec 1))
        (should= -2 (schema/-process-spec-on-value :conform spec -2))
        (should= 3 (schema/-process-spec-on-value :conform spec "3"))
        (should= "one-of: no matching spec" (:message (schema/-process-spec-on-value :conform spec -5)))
        (should= "one-of: no matching spec" (:message (schema/-process-spec-on-value :conform spec "blah")))))

    ;(focus-it "process-spec-schema"
    ;  (let [result (schema/-process-spec-on-value :conform schema/process-spec-schema nil)]
    ;    (prn "result: " result)
    ;    (prn "(meta result): " (meta result))
    ;    (should= 1 result))
    ;  )
    )

  (context "shorthands"

    (it "none"
      (should= {:type :int} (schema/normalize-spec {:type :int}))
      (should= {:type :string :message "foo"} (schema/normalize-spec {:type :string :message "foo"})))

    (it "invalid"
      (should-throw (schema/normalize-spec nil))
      (should-throw (schema/normalize-spec 1))
      (should-throw (schema/normalize-spec {:type nil}))
      (should-throw (schema/normalize-spec {:type 1}))
      (should-throw (schema/normalize-spec {:type (time/now)})))

    (it "keyword"
      (should= {:type :string} (schema/normalize-spec :string))
      (should= {:type :long} (schema/normalize-spec :long))
      (should= {:type :ignore} (schema/normalize-spec :ignore))
      (should= {:type :instant} (schema/normalize-spec :instant))
      (should= {:type :blah} (schema/normalize-spec :blah)))

    (context "seq"

      (it "errors"
        (should-throw (schema/normalize-spec {:type [:int :int]}))
        (should-throw (schema/normalize-spec {:type [:int :string]}))
        (should-throw (schema/normalize-spec {:type []})))

      (it "with type"
        (let [result (schema/normalize-spec {:type [:int]})]
          (should= {:type :seq :spec {:type :int}} result))
        (let [result (schema/normalize-spec {:type [:int] :validate even? :foo "bar"})]
          (should= {:type :seq :spec {:type :int :validate even?} :foo "bar"} result)))

      (it "with spec"
        (let [result (schema/normalize-spec {:type [{:type :int}] :message "foo"})]
          (should= {:type :seq :spec {:type :int} :message "foo"} result))
        (let [result (schema/normalize-spec {:type [{:type :int}] :message "foo" :foo "bar"})]
          (should= {:type :seq :spec {:type :int} :message "foo" :foo "bar"} result)))

      (it "with schema"
        (let [result (schema/normalize-spec {:type [{:foo "bar"}]})]
          (should= {:type :seq :spec {:type :map :schema {:foo "bar"}}} result))
        (let [result (schema/normalize-spec {:type [{:foo "bar"}] :validate :foo})]
          (should= {:type :seq :spec {:type :map :schema {:foo "bar"} :validate :foo}} result))
        (let [result (schema/normalize-spec {:type [{:foo "bar"}] :foo "bar"})]
          (should= {:type :seq :spec {:type :map :schema {:foo "bar"}} :foo "bar"} result)))
      )

    (it "map"
      (let [result (schema/normalize-spec {:type {:foo {:type :string}}})]
        (should= {:type :map :schema {:foo {:type :string}}} result))
      (let [result (schema/normalize-spec {:type {:foo {:type :string}} :validate map?})]
        (should= {:type :map :schema {:foo {:type :string}} :validate map?} result)))

    (it "set"
      (let [result (schema/normalize-spec {:type #{:int :string}})]
        (should= {:type :one-of :specs [{:type :int} {:type :string}]} result)))

    (context "normalize-schema"

      (with-stubs)

      (it "pet"
        (let [result (schema/normalize-schema pet)]
          (should= (dissoc pet :colors :parent) (dissoc result :colors :parent))
          (should= {:type :seq :spec {:type :string :message "must be a string"}} (:colors result))
          (should= {:type :map, :schema {:name {:type :string}, :age {:type :int}}} (:parent result))))

      (it "with entity level spec"
        (let [pet    (assoc pet :* {:name {:validate :name}})
              result (schema/normalize-schema pet)]
          (should= {:name {:validate :name}} (:* result))))

      (it "meta-data, skipping normalization"
        (let [result (schema/normalize-schema pet)]
          (should= {:c3kit.apron.schema/normalized? true} (meta result))
          (with-redefs [update-vals (stub :update-vals)]
            (should= result (schema/normalize-schema result))
            (should-not-have-invoked :update-vals))))
      )
    )

  (context "ignore/any"

    (it "ignore"
      (should= :blah (schema/coerce-value! {:type :ignore} :blah)))

    (it "any"
      (should= :blah (schema/coerce-value! {:type :any} :blah)))

    )

  (context "spec-schema"

    (it "pets"
      (doseq [[field spec] pet]
        (let [spec   (schema/normalize-spec spec)
              result (schema/conform schema/spec-schema spec)]
          (should= nil (schema/message-map result)))))

    (it "type"
      (should= {:type "is required"} (schema/validate-message-map schema/spec-schema {:type nil}))
      (should= {:type "must be one of schema/valid-types"} (schema/validate-message-map schema/spec-schema {:type :blah})))

    (it "validate"
      (should= {:validate "must be an ifn or seq of ifn"}
               (schema/validate-message-map schema/spec-schema {:type :string :validate "blah"})))

    (it "coerce"
      (should= {:coerce "must be an ifn or seq of ifn"}
               (schema/validate-message-map schema/spec-schema {:type :string :coerce "blah"})))

    (it "present"
      (should= {:present "must be an ifn or seq of ifn"}
               (schema/validate-message-map schema/spec-schema {:type :string :present "blah"})))

    (it "message"
      (should= ":blah" (:message (schema/coerce schema/spec-schema {:type :string :message :blah}))))

    (it "validations"
      (should= {:validations "[:map] expected"}
               (schema/validate-message-map schema/spec-schema {:type :string :validations "blah"}))
      (should= nil (schema/validate-message-map schema/spec-schema {:type :string :validations []}))
      (should= {:validations {0 "must be schema/validation-schema"}}
               (schema/validate-message-map schema/spec-schema {:type :string :validations [:blah]}))
      (should= {:validations {0 {:validate "must be an ifn or seq of ifn"}}}
               (schema/validate-message-map schema/spec-schema {:type :string :validations [{:validate "blah"}]})))

    (it "spec"
      (should= {:spec "only used with type :seq"}
               (schema/validate-message-map schema/spec-schema {:type :string :spec {:type :string}}))
      (should= {:spec "must be schema/spec-schema"}
               (schema/validate-message-map schema/spec-schema {:type :seq :spec "blah"})))

    (it "specs"
      (should= {:specs "only used with type :one-of"}
               (schema/validate-message-map schema/spec-schema {:type :string :specs [{:type :string}]}))
      (should= {:specs "[:map] expected"}
               (schema/validate-message-map schema/spec-schema {:type :one-of :specs "blah"})))

    (it "schema"
      (should= {:schema "only used with type :map"}
               (schema/validate-message-map schema/spec-schema {:type :string :schema {:foo {:type :string}}}))
      (should= {:schema "must be a map"}
               (schema/validate-message-map schema/spec-schema {:type :map :schema "blah"})))

    (context "conform-schema"

      (it "pets with entity-level"
        (let [new-pet (assoc pet :* {:species {:validate #(not (and (= "snake" (:species %)) (= "Fluffy" (:name %))))
                                               :message  "Snakes are not fluffy!"}})
              schema  (schema/conform-schema! new-pet)]
          (should-contain :species (:* schema))))

      (it "specs are normalized"
        (let [schema (schema/conform-schema! pet)]
          (should= :seq (:type (:colors schema)))))

      (it "extra spec attributes are not removed"
        (let [schema (schema/conform-schema! pet)]
          (should= :pet (-> schema :kind :value))))

      )
    )
  )

