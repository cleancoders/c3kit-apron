(ns c3kit.apron.schema.doc-spec
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.doc :as sut]
            [clojure.edn :as edn]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should= should]]))

(describe "schema.doc"

  (context "required?"

    (it "true when :required true"
      (should (sut/required? {:type :string :required true})))

    (it "true when :validate is present?"
      (should (sut/required? {:type :string :validate schema/present?})))

    (it "true when :validations includes present?"
      (should (sut/required? {:type :string :validations [{:validate schema/present?}]})))

    (it "true when :validations includes the bare keyword ref :present?"
      (should (sut/required? {:type :string :validations [:present?]})))

    (it "true when :validations includes the bare keyword ref :required"
      (should (sut/required? {:type :string :validations [:required]})))

    (it "true when :validations includes a map-wrapped keyword ref"
      (should (sut/required? {:type :string :validations [{:validate :present?}]}))
      (should (sut/required? {:type :string :validations [{:validate :required}]})))

    (it "false for an unrelated keyword ref"
      (should= false (sut/required? {:type :string :validations [:string?]})))

    (it "false for an unregistered keyword ref -- never throws"
      (should= false (sut/required? {:type :string :validations [:not-a-real-ref]})))

    (it "false otherwise"
      (should= false (sut/required? {:type :string}))))

  (context "describe"

    (it "a flat schema"
      (should= [{:path "kind" :type :keyword :required false}
                {:path "x" :type :int :required false}
                {:path "y" :type :int :required false}]
               (sut/describe {:kind {:type :keyword}
                              :x    {:type :int}
                              :y    {:type :int}})))

    (it "includes :default, :required, and :description when present"
      (should= [{:path "name" :type :string :required true :description "the pet's name"}
                {:path "age" :type :int :required false :default 0}]
               (sut/describe {:name {:type :string :required true :description "the pet's name"}
                              :age  {:type :int :default 0}})))

    (it "a :required field with a :default is still reported as :required true"
      ;; describe reports the spec's declared :required, independent of
      ;; whether validate/conform would treat a nil value as present.
      (should= [{:path "name" :type :string :required true :default "Bob"}]
               (sut/describe {:name {:type :string :required true :default "Bob"}})))

    (it "accepts a bare schema or a wrapped :map spec identically"
      (let [bare    {:name {:type :string}}
            wrapped {:type :map :schema bare}]
        (should= (sut/describe bare) (sut/describe wrapped))))

    (it "recurses into nested :map fields"
      (should= [{:path "start" :type :map :required false}
                {:path "start.x" :type :int :required false}
                {:path "start.y" :type :int :required false}]
               (sut/describe {:start {:type :map :schema {:x {:type :int} :y {:type :int}}}})))

    (it "recurses into :seq entries via the .value template"
      (should= [{:path "points" :type :seq :required false}
                {:path "points.value" :type :map :required false}
                {:path "points.value.x" :type :int :required false}]
               (sut/describe {:points {:type :seq :spec {:type :map :schema {:x {:type :int}}}}})))

    (it "works with the [type] seq shorthand"
      (should= [{:path "colors" :type :seq :required false}
                {:path "colors.value" :type :string :required false}]
               (sut/describe {:colors {:type [:string]}})))

    (it "reports a :map's dynamic :key-spec / :value-spec at .key / .value"
      (should= [{:path "crew" :type :map :required false}
                {:path "crew.key" :type :keyword :required false}
                {:path "crew.value" :type :map :required false}
                {:path "crew.value.name" :type :string :required false}]
               (sut/describe {:crew {:type       :map
                                     :key-spec   {:type :keyword}
                                     :value-spec {:type :map :schema {:name {:type :string}}}}})))

    (it "does not expand :one-of alternatives -- reports a single entry"
      (should= [{:path "geometry" :type :one-of :required false}]
               (sut/describe {:geometry {:type     :one-of
                                         :specs    [{:type :map :schema {:x {:type :int}}}
                                                    {:type :map :schema {:r {:type :int}}}]}})))

    (it "works on plain data (EDN) schemas -- no functions required"
      (should= [{:path "name" :type :string :required true}]
               (sut/describe (edn/read-string "{:name {:type :string :required true}}"))))

    (it "recognizes required-ness expressed as keyword refs -- the EDN-schema case"
      ;; The whole point of :validations [:present?] / [:required] is that a
      ;; schema loaded from plain EDN (no fns in it) can still say a field is
      ;; required; describe has to see that without ever calling a function.
      (should= [{:path "a" :type :int :required true}
                {:path "b" :type :int :required true}]
               (sut/describe (edn/read-string "{:a {:type :int :validations [:present?]}
                                                :b {:type :int :validations [:required]}}"))))))
