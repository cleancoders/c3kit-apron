(ns c3kit.apron.schema-spec
  #?(:cljs (:require-macros [c3kit.apron.schema :refer [with-lexicon]]))
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.time :as time]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should= should-contain should-not-contain
                                                      should-throw should-be-a should should-not should-be-nil
                                                      with-stubs stub should-not-have-invoked before around]]
    [clojure.string :as str]
    [c3kit.apron.utilc :as utilc]
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.schema :as s])
  #?(:clj (:import (java.net URI)
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

  ;; "coercion" context moved to c3kit.apron.schema.coercers-spec
  ;; "validation" context moved to c3kit.apron.schema.validators-spec

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
      (should-be-nil (schema/conform-value! {:type [:int]} nil))
      (should-throw stdex "[:int] expected" (schema/conform-value! {:type [:int]} "foo")))

    (it "of object"
      (let [spec {:type {:foo {:type :keyword}}}]
        (should= {} (schema/conform-value! spec {}))
        (should= {:foo :bar} (schema/conform-value! spec {:foo :bar :hello "world"}))))

    (it "of multi object"
      (let [spec {:type [{:foo {:type :keyword}}]}]
        (should= [{}] (schema/conform-value! spec [{}]))
        (should= [{:foo :bar}] (schema/conform-value! spec [{:foo :bar :hello "world"}]))
        (should-be-nil (schema/conform-value! spec nil))))

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
        (should-be-nil (:garbage result))
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
        (should-be-nil (schema/message-map result))))

    (it "coercing a string to map"
      (let [path-schema {:path {:type    :map
                                :coerce  utilc/<-edn
                                :message "oops"
                                :schema  {:foo {:type :string :validate str/blank? :coerce #(apply str (rest %))}}}}]
        (let [valid (schema/conform path-schema {:path (pr-str {:foo ""})})]
          (should-be-nil (schema/message-map valid))
          (should= {:foo ""} (:path valid)))
        (let [invalid (schema/conform path-schema {:path (pr-str {:foo "foo"})})]
          (should= "is invalid" (get-in (schema/message-map invalid) [:path :foo])))
        (let [not-a-map (schema/conform path-schema {:path (pr-str "im-not-a-map")})]
          (should= "oops" (get-in (schema/message-map not-a-map) [:path])))
        (let [valid-nested (schema/conform path-schema {:path (pr-str {:foo "f"})})]
          (should-be-nil (schema/message-map valid-nested)))))

    (it "required map field"
      (let [schema {:thing {:type :map :schema {:field {:type :any}} :validations [schema/required]}}]
        (should= "is required" (:thing (schema/conform-message-map schema {})))))

    )



  (context "error messages"

    (it "are nil when there are none"
      (should-be-nil (schema/message-map {})))

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
        (should-contain "pets[1].parent.age can't coerce :foo to int" result)
        (should-contain "pets[1].name must be nice and unique name" result)
        (should-contain "pets[3].parent.age can't coerce :foo to int" result)
        (should-contain "pets[3].name must be nice and unique name" result)
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
        (should-be-nil (schema/-process-spec-on-value :coerce spec nil))
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

  (context "walk-schema"

    (it "leaf emits with nil children"
      (let [seen (atom [])
            emit (fn [spec children] (swap! seen conj [spec children]) [spec children])]
        (schema/walk-schema emit {:type :int})
        (should= [[{:type :int} nil]] @seen)))

    (it "seq recurses into :spec"
      (let [result (schema/walk-schema
                     (fn [spec children]
                       (case (:type spec)
                         :seq (str "seq-of-" (:spec children))
                         (name (:type spec))))
                     {:type :seq :spec {:type :int}})]
        (should= "seq-of-int" result)))

    (it "one-of recurses into :specs"
      (let [result (schema/walk-schema
                     (fn [spec children]
                       (case (:type spec)
                         :one-of (str "one-of[" (clojure.string/join "," (:specs children)) "]")
                         (name (:type spec))))
                     {:type :one-of :specs [{:type :int} {:type :string}]})]
        (should= "one-of[int,string]" result)))

    (it "map recurses into :schema, :key-spec, :value-spec"
      (let [result (schema/walk-schema
                     (fn [spec children]
                       (case (:type spec)
                         :map (select-keys children [:schema :key-spec :value-spec])
                         (name (:type spec))))
                     {:type       :map
                      :schema     {:name {:type :string}}
                      :key-spec   {:type :keyword}
                      :value-spec {:type :int}})]
        (should= {:schema {:name "string"} :key-spec "keyword" :value-spec "int"} result)))

    (it "normalizes shorthand before dispatching"
      (let [result (schema/walk-schema
                     (fn [spec children]
                       (case (:type spec)
                         :seq (str "seq-of-" (:spec children))
                         (name (:type spec))))
                     {:type [:int]})]
        (should= "seq-of-int" result)))

    (it "set shorthand with bare-map member normalizes to one-of with :map"
      (let [seen-types (atom #{})
            emit       (fn [spec _children] (swap! seen-types conj (:type spec)) :ok)]
        (schema/walk-schema emit {:type #{:string {:foo {:type :int}}}})
        (should= #{:one-of :string :map :int} @seen-types)))

    )

  (context "ignore/any"

    (it "ignore"
      (should= :blah (schema/coerce-value! {:type :ignore} :blah)))

    (it "any"
      (should= :blah (schema/coerce-value! {:type :any} :blah)))

    )

  (context "dynamic keys"

    (it "coerces keys and values"
      (let [spec   {:type :map :key-spec {:type :keyword} :value-spec {:type :map :schema {:name {:type :string}}}}
            result (schema/coerce-value! spec {"joe" {:name "Joe"} "bill" {:name "Bill"}})]
        (should= {:joe {:name "Joe"} :bill {:name "Bill"}} result)))

    (it "validates keys and values"
      (let [spec {:type :map :key-spec {:type :keyword} :value-spec {:type :map :schema {:name {:type :string}}}}]
        (should-be-nil (schema/message-map (schema/validate-value! spec {:joe {:name "Joe"}})))
        (let [bad (schema/-process-spec-on-value :validate spec {:joe {:name 42}})]
          (should= {:joe {:name "is invalid"}} (schema/message-map bad)))))

    (it "conforms chains coerce then validate"
      (let [spec   {:type :map :key-spec {:type :keyword} :value-spec {:type :map :schema {:name {:type :string}}}}
            result (schema/conform-value! spec {"joe" {:name "Joe"}})]
        (should= {:joe {:name "Joe"}} result)))

    (it "key coerce error lands at original key"
      (let [bad (schema/-process-spec-on-value :coerce
                                               {:type :map :key-spec {:type :int}}
                                               {"not-an-int" "v"})]
        (should-contain "not-an-int" (schema/message-map bad))))

    (it "value error lands at coerced key"
      (let [spec {:type :map :key-spec {:type :keyword} :value-spec {:type :map :schema {:age {:type :int}}}}
            bad  (schema/-process-spec-on-value :conform spec {"joe" {:age :not-an-int}})]
        (should-contain :joe (schema/message-map bad))))

    (it "known keys win over dynamic"
      (let [crew-member {:name {:type :string}}
            mixed       {:type       :map
                         :schema     {:captain {:type :map :schema crew-member}}
                         :key-spec   {:type :keyword}
                         :value-spec {:type :map :schema crew-member}}
            result      (schema/coerce-value! mixed {:captain {:name "Cap"} :joe {:name "Joe"}})]
        (should= {:captain {:name "Cap"} :joe {:name "Joe"}} result)))

    (it "absent :key-spec/:value-spec drops unknown keys"
      (let [spec   {:type :map :schema {:captain {:type :map :schema {:name {:type :string}}}}}
            result (schema/coerce-value! spec {:captain {:name "Cap"} :joe {:name "Joe"}})]
        (should= {:captain {:name "Cap"}} result)))

    (it "message-seq paths: dot for keyword, bracket for non-keyword"
      (let [crew-spec {:type :map :key-spec {:type :keyword} :value-spec {:type :map :schema {:name {:type :string}}}}
            schema    {:crew crew-spec}
            bad       {:crew {:joe {:name 42}}}
            msgs      (schema/message-seq (schema/validate schema bad))]
        (should-contain "crew.joe.name is invalid" msgs)))

    (it "entity-level :* still runs alongside dynamic keys"
      (let [crew-spec (assoc {:type       :map
                              :key-spec   {:type :keyword}
                              :value-spec {:type :map :schema {:name {:type :string}}}}
                        :* {:size {:validate #(pos? (count %)) :message "no crew"}})
            schema    {:crew crew-spec}]
        (should-be-nil (schema/message-map (schema/validate schema {:crew {:joe {:name "Joe"}}})))))

    (it "seq index path uses bracket"
      (let [schema {:points {:type :seq :spec {:type :int}}}
            msgs   (schema/message-seq (schema/validate schema {:points [1 "bad" 3]}))]
        (should-contain "points[1] is invalid" msgs)))

    )

  (context "spec-schema"

    (it "pets"
      (doseq [spec (vals pet)]
        (let [spec   (schema/normalize-spec spec)
              result (schema/conform schema/spec-schema spec)]
          (should-be-nil (schema/message-map result)))))

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
      (should-be-nil (schema/validate-message-map schema/spec-schema {:type :string :validations []}))
      (should= {:validations {0 "must be schema/validation-schema"}}
               (schema/validate-message-map schema/spec-schema {:type :string :validations [:blah]}))
      (should= {:validations {0 {:validate "must be an ifn or seq of ifn"}}}
               (schema/validate-message-map schema/spec-schema {:type :string :validations [{:validate "blah"}]})))

    (it "coercions"
      (should= {:coercions "[:map] expected"}
               (schema/validate-message-map schema/spec-schema {:type :string :coercions "blah"}))
      (should-be-nil (schema/validate-message-map schema/spec-schema {:type :string :coercions []}))
      (should= {:coercions {0 "must be schema/coercion-schema"}}
               (schema/validate-message-map schema/spec-schema {:type :string :coercions [:blah]}))
      (should= {:coercions {0 {:coerce "must be an ifn or seq of ifn"}}}
               (schema/validate-message-map schema/spec-schema {:type :string :coercions [{:coerce "blah"}]})))

    (it "presentations"
      (should= {:presentations "[:map] expected"}
               (schema/validate-message-map schema/spec-schema {:type :string :presentations "blah"}))
      (should-be-nil (schema/validate-message-map schema/spec-schema {:type :string :presentations []}))
      (should= {:presentations {0 "must be schema/presentation-schema"}}
               (schema/validate-message-map schema/spec-schema {:type :string :presentations [:blah]}))
      (should= {:presentations {0 {:present "must be an ifn or seq of ifn"}}}
               (schema/validate-message-map schema/spec-schema {:type :string :presentations [{:present "blah"}]})))

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

    (it "key-spec"
      (should= {:key-spec "only used with type :map"}
               (schema/validate-message-map schema/spec-schema {:type :string :key-spec {:type :keyword}}))
      (should= {:key-spec "must be schema/spec-schema"}
               (schema/validate-message-map schema/spec-schema {:type :map :key-spec "blah"}))
      (should-be-nil (schema/validate-message-map schema/spec-schema
                                                  {:type :map :key-spec {:type :keyword}})))

    (it "value-spec"
      (should= {:value-spec "only used with type :map"}
               (schema/validate-message-map schema/spec-schema {:type :string :value-spec {:type :int}}))
      (should= {:value-spec "must be schema/spec-schema"}
               (schema/validate-message-map schema/spec-schema {:type :map :value-spec "blah"}))
      (should-be-nil (schema/validate-message-map schema/spec-schema
                                                  {:type :map :value-spec {:type :int}})))

    (it "description"
      (should-be-nil (schema/validate-message-map schema/spec-schema
                                                  {:type :int :description "a count"}))
      (should= {:description "is invalid"}
               (schema/validate-message-map schema/spec-schema {:type :int :description 42})))

    (it "example"
      (should-be-nil (schema/validate-message-map schema/spec-schema
                                                  {:type :int :example 42}))
      (should-be-nil (schema/validate-message-map schema/spec-schema
                                                  {:type :map :example {:foo "bar"}})))

    (it "name"
      (should-be-nil (schema/validate-message-map schema/spec-schema
                                                  {:type :map :name :pet}))
      (should= {:name "is invalid"}
               (schema/validate-message-map schema/spec-schema {:type :map :name "pet"})))

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

(describe "lex lookup"

  (context "lex! on :validations"

    (it "throws when looking up an unregistered lex"
      (should-throw (schema/lex! :validations :nope)))

    (it "looks up by string-form name"
      (schema/with-lexicon {:validations {:my-pos {:validate pos?}}}
        (should= {:validate pos?} (schema/lex! :validations "my-pos"))))

    (it "looks up by symbol-form name"
      (schema/with-lexicon {:validations {:my-pos {:validate pos?}}}
        (should= {:validate pos?} (schema/lex! :validations 'my-pos))))

    (it "preserves namespaces for symbol and string keys"
      (schema/with-lexicon {:validations {:ns/sym {:validate pos?}}}
        (should= {:validate pos?} (schema/lex! :validations :ns/sym))
        (should= {:validate pos?} (schema/lex! :validations "ns/sym"))
        (should= {:validate pos?} (schema/lex! :validations 'ns/sym))))

    (it "applies a registered factory when looked up via vector form"
      (schema/with-lexicon {:validations {:max-len (fn [n] {:validate #(<= (count %) n)})}}
        (let [{:keys [validate]} (schema/lex! :validations [:max-len 3])]
          (should     (validate "ab"))
          (should-not (validate "abcd")))))

    (it "resolves a zero-arg vector form like the bare key"
      (schema/with-lexicon {:validations {:pos-pred {:validate pos?}}}
        (should= {:validate pos?} (schema/lex! :validations [:pos-pred]))))
    )

  (context "lex! on :coercions"

    (it "throws when looking up an unregistered lex"
      (should-throw (schema/lex! :coercions :nope)))

    (it "looks up a coercion lex"
      (schema/with-lexicon {:coercions {:trimify {:coerce str/trim}}}
        (should= str/trim (:coerce (schema/lex! :coercions :trimify)))))
    )

  (context "lex (nil on miss)"

    (it "returns nil for an unregistered name"
      (should= nil (schema/lex :validations :nope)))

    (it "returns the entry when present"
      (schema/with-lexicon {:validations {:my-pos {:validate pos?}}}
        (should= {:validate pos?} (schema/lex :validations :my-pos))))
    )
  )

(describe "lexes in :validations and :coercions"

  (it "resolves a bare lex entry inside :validations"
    (schema/with-lexicon {:validations {:lex/is-pos {:validate pos?}}}
      (let [spec {:type :int :validations [:lex/is-pos] :message "must be positive"}]
        (should= 5 (schema/validate-value! spec 5))
        (should-throw (schema/validate-value! spec -1)))))

  (it "resolves a vector lex entry as a factory call inside :validations"
    (schema/with-lexicon {:validations {:lex/max-len (fn [n] {:validate (fn [s] (<= (count s) n))})}}
      (let [spec {:type :string :validations [[:lex/max-len 3]] :message "too long"}]
        (should= "ab" (schema/validate-value! spec "ab"))
        (should-throw (schema/validate-value! spec "abcd")))))

  (it "uses the lexicon default message when entry omits one"
    (schema/with-lexicon {:validations {:lex/present {:validate schema/present? :message "is required"}}}
      (let [spec {:type :string :validations [:lex/present]}]
        (should-throw stdex "is required" (schema/validate-value! spec nil)))))

  (it "spec :message overrides the lexicon default"
    (schema/with-lexicon {:validations {:lex/present2 {:validate schema/present? :message "is required"}}}
      (let [spec {:type :string :validations [:lex/present2] :message "Name is mandatory"}]
        (should-throw stdex "Name is mandatory" (schema/validate-value! spec nil)))))

  (it "throws when a :validations lex has no :validate key"
    (schema/with-lexicon {:validations {:lex/coerce-only {:coerce str/trim}}}
      (let [spec {:type :string :validations [:lex/coerce-only]}]
        (should-throw stdex "lex :lex/coerce-only has no :validate"
                      (schema/validate-value! spec "foo")))))

  (it "applies a registered lex :coerce inside :coercions"
    (schema/with-lexicon {:coercions {:lex/trim {:coerce str/trim}}}
      (let [spec {:type :string :coercions [:lex/trim]}]
        (should= "foo" (schema/coerce-value! spec "  foo  ")))))

  (it "throws when a :coercions lex has no :coerce key"
    (schema/with-lexicon {:coercions {:lex/validate-only {:validate pos?}}}
      (let [spec {:type :int :coercions [:lex/validate-only]}]
        (should-throw stdex "lex :lex/validate-only has no :coerce"
                      (schema/coerce-value! spec 5)))))

  (it "resolves a lex-valued :validate inside a map entry of :validations"
    (schema/with-lexicon {:validations {:lex/positive {:validate pos? :message "must be pos"}}}
      (let [spec {:type :int :validations [{:validate :lex/positive :message "no good"}]}]
        (should= 1 (schema/validate-value! spec 1))
        (should-throw stdex "no good" (schema/validate-value! spec -1)))))

  (it "resolves a lex-valued :coerce inside a map entry of :coercions"
    (schema/with-lexicon {:coercions {:lex/upper {:coerce str/upper-case}}}
      (let [spec {:type :string :coercions [{:coerce :lex/upper}]}]
        (should= "HI" (schema/coerce-value! spec "hi")))))

  (it "verify-schema-lexes returns true when every lex resolves"
    (schema/with-lexicon {:validations {:verify/str  {:validate string?}}
                          :coercions   {:verify/trim {:coerce str/trim}}}
      (let [schema {:foo {:type        :string
                          :validations [:verify/str]
                          :coercions   [:verify/trim]}}]
        (should= true (schema/verify-schema-lexes schema)))))

  (it "verify-schema-lexes throws when a :validations lex is unregistered"
    (let [schema {:foo {:type :string :validations [:never-registered]}}]
      (should-throw stdex (schema/verify-schema-lexes schema))))

  (it "verify-schema-lexes throws when a :coercions lex entry is missing its :coerce key"
    (schema/with-lexicon {:coercions {:verify/shapeless {:message "I have no :coerce key"}}}
      (let [schema {:foo {:type :int :coercions [:verify/shapeless]}}]
        (should-throw stdex "lex :verify/shapeless has no :coerce"
                      (schema/verify-schema-lexes schema)))))

  (it "missing lex on entity"
    (let [pet    (schema/merge-schemas pet {:species {:validations [:plant?]}
                                            :teeth   {:validations [[:sharper-than? 6]]}
                                            :name    {:coercions   ["fruit!"]}
                                            :length  {:coercions   [[(symbol "grow!") 1.5]]}})
          result (schema/conform-message-map pet valid-pet)]
      (should= "missing lex :plant? in :validations"      (:species result))
      (should= "missing lex :fruit! in :coercions"        (:name    result))
      (should= "missing lex :sharper-than? in :validations" (:teeth  result))
      (should= "missing lex :grow! in :coercions"         (:length  result))))
  )

(describe "entity-scoped lexes"

  (around [it]
    (schema/with-lexicon
      {:validations {:req-when-dog {:validate (fn [entity field-key]
                                                (or (not= "dog" (:species entity))
                                                    (schema/present? (get entity field-key))))
                                    :scope    :entity
                                    :message  "needed for dogs"}}}
      (it)))

  (it "validation fails when sibling triggers requirement and field is absent"
    (let [schema {:species     {:type :string}
                  :tail-length {:type :int :validations [:req-when-dog]}}]
      (should= {:tail-length "needed for dogs"}
               (schema/validate-message-map schema {:species "dog"}))))

  (it "validation passes when sibling condition does not trigger requirement"
    (let [schema {:species     {:type :string}
                  :tail-length {:type :int :validations [:req-when-dog]}}]
      (should-be-nil (schema/validate-message-map schema {:species "cat"}))))

  (it "validation passes when sibling triggers and field is present"
    (let [schema {:species     {:type :string}
                  :tail-length {:type :int :validations [:req-when-dog]}}]
      (should-be-nil (schema/validate-message-map schema {:species "dog" :tail-length 5}))))

  (it "validate-value! on a single field bypasses entity-scoped validations"
    (let [field-spec {:type :int :validations [:req-when-dog]}]
      (should= 0 (schema/validate-value! field-spec 0))
      (should-be-nil (schema/validate-value! field-spec nil))))

  (it "factory ref form binds args before validation runs"
    (schema/with-lexicon
      {:validations {:req-when (fn [other-key expected]
                                 {:validate (fn [entity field-key]
                                              (or (not= expected (get entity other-key))
                                                  (schema/present? (get entity field-key))))
                                  :scope    :entity
                                  :message  (str "is required when " other-key " is " expected)})}}
      (let [schema {:species     {:type :string}
                    :tail-length {:type :int :validations [[:req-when :species "dog"]]}}]
        (should= {:tail-length "is required when :species is dog"}
                 (schema/validate-message-map schema {:species "dog"}))
        (should-be-nil (schema/validate-message-map schema {:species "cat"})))))

  (it "entity-scoped validation rebinds inside a nested map"
    (schema/with-lexicon
      {:validations {:req-when-snake {:validate (fn [entity field-key]
                                                  (or (not= "snake" (:species entity))
                                                      (schema/present? (get entity field-key))))
                                      :scope    :entity
                                      :message  "needed for snakes"}}}
      (let [inner {:species {:type :string}
                   :length  {:type :int :validations [:req-when-snake]}}
            outer {:owner {:type :string}
                   :pet   {:type :map :schema inner}}]
        (should= {:pet {:length "needed for snakes"}}
                 (schema/validate-message-map outer {:owner "Joe" :pet {:species "snake"}}))
        (should-be-nil
          (schema/validate-message-map outer {:owner "Joe" :pet {:species "snake" :length 4}})))))

  (it "value-scoped and entity-scoped validations on the same field both run"
    (let [schema {:species     {:type :string}
                  :tail-length {:type        :int
                                :validations [{:validate (schema/nil?-or pos?) :message "must be positive"}
                                              :req-when-dog]}}]
      (should= {:tail-length "must be positive"}
               (schema/validate-message-map schema {:species "cat" :tail-length -1}))
      (should= {:tail-length "needed for dogs"}
               (schema/validate-message-map schema {:species "dog"}))
      (should-be-nil (schema/validate-message-map schema {:species "dog" :tail-length 5}))))

  (it "entity-scoped coerce derives a value from sibling fields"
    (schema/with-lexicon
      {:coercions {:full-name-from-parts {:coerce (fn [entity _field-key]
                                                    (str (:first-name entity) " " (:last-name entity)))
                                          :scope  :entity}}}
      (let [schema {:first-name {:type :string}
                    :last-name  {:type :string}
                    :full-name  {:type :string :coercions [:full-name-from-parts]}}]
        (should= "Ada Lovelace"
                 (:full-name (schema/coerce schema {:first-name "Ada" :last-name "Lovelace"}))))))

  (it "value-scoped coerce runs before entity-scoped coerce on the same field"
    (schema/with-lexicon
      {:coercions {:double-it {:coerce (fn [entity field-key] (* 2 (get entity field-key)))
                               :scope  :entity}}}
      (let [schema {:n {:type :int :coerce inc :coercions [:double-it]}}]
        (should= 8 (:n (schema/coerce schema {:n 3}))))))

  (it "entity-scoped coerce runs before entity-scoped validate on the same field in conform"
    (schema/with-lexicon
      {:coercions {:default-tail {:coerce (fn [entity field-key]
                                            (or (get entity field-key)
                                                (when (= "dog" (:species entity)) 0)))
                                  :scope  :entity}}}
      (let [schema {:species     {:type :string}
                    :tail-length {:type        :int
                                  :coercions   [:default-tail]
                                  :validations [:req-when-dog]}}]
        (should= 0 (:tail-length (schema/conform schema {:species "dog"})))
        (should-be-nil (schema/conform-message-map schema {:species "dog"})))))

  (it "coerce-value! on a single field bypasses entity-scoped coerce"
    (schema/with-lexicon
      {:coercions {:sibling-coerce {:coerce (fn [_entity _field-key] "would-have-derived")
                                    :scope  :entity}}}
      (should= "raw"
               (schema/coerce-value! {:type :string :coercions [:sibling-coerce]} "raw"))))
  )

(describe "lexicon"

  (context "validations slot"

    (it "validate-value! resolves a name from *lexicon* :validations"
      (schema/with-lexicon {:validations {:positive? {:validate pos? :message "must be positive"}}}
        (should= 5 (schema/validate-value! {:type :any :validations [:positive?]} 5))
        (should-throw stdex "must be positive"
                      (schema/validate-value! {:type :any :validations [:positive?]} -1))))
    )

  (context "coercions slot"

    (it "coerce-value! resolves a name from *lexicon* :coercions"
      (schema/with-lexicon {:coercions {:double-it {:coerce #(* 2 %) :message "could not double"}}}
        (should= 10 (schema/coerce-value! {:type :any :coercions [:double-it]} 5))))
    )

  (context "presentations slot"

    (it "present-value! resolves a name from *lexicon* :presentations"
      (schema/with-lexicon {:presentations {:shout {:present clojure.string/upper-case}}}
        (should= "HELLO" (schema/present-value! {:type :string :presentations [:shout]} "hello"))))

    (it "applies multiple presentations in order"
      (schema/with-lexicon {:presentations {:trim  {:present clojure.string/trim}
                                            :shout {:present clojure.string/upper-case}}}
        (should= "HI"
                 (schema/present-value! {:type :string :presentations [:trim :shout]} "  hi  "))))

    (it "combines inline :present with :presentations"
      (schema/with-lexicon {:presentations {:shout {:present clojure.string/upper-case}}}
        (should= "HELLO!"
                 (schema/present-value! {:type    :string
                                         :present #(str % "!")
                                         :presentations [:shout]} "hello"))))

    (it "applies a registered factory via vector form"
      (schema/with-lexicon {:presentations {:prefix-with (fn [p] {:present #(str p %)})}}
        (should= ">>hi"
                 (schema/present-value! {:type :string :presentations [[:prefix-with ">>"]]} "hi"))))

    (it "resolves a map entry form {:present <name>}"
      (schema/with-lexicon {:presentations {:shout {:present clojure.string/upper-case}}}
        (should= "HELLO"
                 (schema/present-value! {:type :string :presentations [{:present :shout}]} "hello"))))

    (it "present-with scopes a :presentations override"
      (let [lex {:presentations {:pw-shout {:present clojure.string/upper-case}}}
            sch {:n {:type :string :presentations [:pw-shout]}}]
        (should= {:n "HI"} (schema/present-with lex sch {:n "hi"}))))

    (it "update-lexicon! :presentations extends at root"
      (try
        (schema/update-lexicon! :presentations assoc :ulex-yell
                                {:present #(str % "!!!")})
        (should= "hi!!!"
                 (schema/present-value! {:type :string :presentations [:ulex-yell]} "hi"))
        (finally
          (schema/update-lexicon! :presentations dissoc :ulex-yell))))

    (it "throws when a :presentations lex has no :present key"
      (schema/with-lexicon {:presentations {:bad {:message "no :present here"}}}
        (should-throw stdex "lex :bad has no :present"
                      (schema/present-value! {:type :string :presentations [:bad]} "hi"))))
    )

  (context "slot isolation"

    (it "the same name can mean different things in :validations and :coercions"
      (schema/with-lexicon {:validations {:foo {:validate pos? :message "must be positive"}}
                            :coercions   {:foo {:coerce #(* 2 %) :message "could not double"}}}
        (should= 10 (schema/coerce-value! {:type :any :coercions [:foo]} 5))
        (should= 5  (schema/validate-value! {:type :any :validations [:foo]} 5))))
    )

  (context "default-lexicon shape"

    (it "has all four slots present as empty maps"
      (should= #{:types :validations :coercions :presentations}
               (set (keys schema/default-lexicon))))
    )

  (context "update-lexicon!"

    (it "extends the :validations slot at root"
      (try
        (schema/update-lexicon! :validations assoc :ulex-pos
                                {:validate pos? :message "must be positive"})
        (should= 5 (schema/validate-value! {:type :any :validations [:ulex-pos]} 5))
        (finally
          (schema/update-lexicon! :validations dissoc :ulex-pos))))

    (it "extends the :coercions slot at root"
      (try
        (schema/update-lexicon! :coercions assoc :ulex-double
                                {:coerce #(* 2 %) :message "could not double"})
        (should= 10 (schema/coerce-value! {:type :any :coercions [:ulex-double]} 5))
        (finally
          (schema/update-lexicon! :coercions dissoc :ulex-double))))
    )

  (context "with-lexicon"

    (it "scopes :validations overrides for the duration of the body"
      (schema/with-lexicon {:validations {:wl-even? {:validate even? :message "must be even"}}}
        (should= 4 (schema/validate-value! {:type :any :validations [:wl-even?]} 4))
        (should-throw stdex "must be even"
                      (schema/validate-value! {:type :any :validations [:wl-even?]} 3))))

    (it "scopes :coercions overrides for the duration of the body"
      (schema/with-lexicon {:coercions {:wl-inc {:coerce inc :message "could not inc"}}}
        (should= 6 (schema/coerce-value! {:type :any :coercions [:wl-inc]} 5))))

    (it "supports both slots in one form"
      (schema/with-lexicon {:validations {:wl-pos {:validate pos? :message "positive"}}
                            :coercions   {:wl-neg {:coerce -    :message "negate"}}}
        (should= 5 (schema/validate-value! {:type :any :validations [:wl-pos]} 5))
        (should= -5 (schema/coerce-value! {:type :any :coercions [:wl-neg]} 5))))
    )

  (context "*-with API"

    (it "validate-with applies a lexicon for entity validation"
      (let [lex {:validations {:vw-pos {:validate pos? :message "must be positive"}}}
            sch {:n {:type :int :validations [:vw-pos]}}]
        (should= {:n 5} (schema/validate-with lex sch {:n 5}))
        (should-throw stdex (schema/validate-with lex sch {:n -1}))))

    (it "coerce-with applies a lexicon for entity coercion"
      (let [lex {:coercions {:cw-double {:coerce #(* 2 %) :message "could not double"}}}
            sch {:n {:type :int :coercions [:cw-double]}}]
        (should= {:n 10} (schema/coerce-with lex sch {:n 5}))))

    (it "conform-with applies both slots for entity conform"
      (let [lex {:validations {:cfw-pos    {:validate pos? :message "must be positive"}}
                 :coercions   {:cfw-double {:coerce #(* 2 %) :message "could not double"}}}
            sch {:n {:type :int :coercions [:cfw-double] :validations [:cfw-pos]}}]
        (should= {:n 10} (schema/conform-with lex sch {:n 5}))
        (should-throw stdex (schema/conform-with lex sch {:n -3}))))

    (it "present-with scopes a lexicon during presentation"
      (let [lex {:coercions {:pw-noop {:coerce identity :message "noop"}}}
            sch {:n {:type :int :present #(str "n=" %)}}]
        (should= {:n "n=5"} (schema/present-with lex sch {:n 5}))))
    )
  )

