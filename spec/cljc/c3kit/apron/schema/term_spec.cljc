(ns c3kit.apron.schema.term-spec
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.term :as sut]
            [clojure.string :as s]
            [speclj.core #?(:clj :refer :cljs :refer-macros)
             [describe it should should= should-contain should-not-contain context]]))

(def ^:private plain {:color? false :width 80})

(describe "schema.term"

  (context "plain (no color) output"

    (it "renders a leaf type"
      (should= "string" (sut/schema->term {:type :string} plain)))

    (it "renders a map as a header with one line per field"
      (let [out (sut/schema->term
                  {:type :map :schema {:name {:type :string}
                                       :age  {:type :int}}}
                  plain)]
        (should-contain "Schema" out)
        (should-contain "age" out)
        (should-contain "integer" out)
        (should-contain "name" out)
        (should-contain "string" out)))

    (it "sorts fields alphabetically"
      (let [out   (sut/schema->term
                    {:type :map :schema {:zeta {:type :string}
                                         :alpha {:type :string}}}
                    plain)
            alpha (s/index-of out "alpha")
            zeta  (s/index-of out "zeta")]
        (should (< alpha zeta))))

    (it "includes field description on its own line"
      (let [out (sut/schema->term
                  {:type :map :schema {:name {:type :string
                                              :description "User's name."}}}
                  plain)]
        (should-contain "User's name." out)))

    (it "marks required fields"
      (let [out (sut/schema->term
                  {:type :map :schema {:name {:type :string
                                              :validate schema/present?}}}
                  plain)]
        (should-contain "required" out)))

    (it "shows example on its own line"
      (let [out (sut/schema->term
                  {:type :map :schema {:age {:type :int :example 30}}}
                  plain)]
        (should-contain "example: 30" out)))

    (it "shows named ref with an arrow"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/schema->term
                  {:type :map :schema {:pet pet}}
                  plain)]
        (should-contain "object → pet" out)))

    (it "emits a section for each named schema"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/schema->term
                  {:type :map :schema {:pet pet}}
                  plain)]
        (should-contain "Schema" out)
        (should-contain "pet" out)))

    (it "dedups when the same named schema is reached twice"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/schema->term
                  {:type :map :schema {:a pet :b pet}}
                  plain)
            pet-headings (re-seq #"(?m)^pet" out)]
        (should= 1 (count pet-headings))))

    (it "when the root spec is named, omits the generic Schema heading"
      (let [out (sut/schema->term
                  {:type :map :name :user :schema {:name {:type :string}}}
                  plain)]
        (should-contain "user" out)
        (should-not-contain "Schema" out)))

    )

  (context "colored output"

    (it "emits ANSI escape codes when :color? is true"
      (let [out (sut/schema->term
                  {:type :map :schema {:name {:type :string}}}
                  {:color? true :width 80})]
        (should-contain "\033[" out)))

    (it "suppresses ANSI codes when :color? is false"
      (let [out (sut/schema->term
                  {:type :map :schema {:name {:type :string}}}
                  plain)]
        (should-not-contain "\033[" out)))

    )

  (context "section headings"

    (it "underlines the heading with a rule"
      (let [out (sut/schema->term
                  {:type :map :schema {:name {:type :string}}}
                  plain)]
        (should-contain "Schema\n──" out)))

    (it "named section also gets a rule"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            out (sut/schema->term
                  {:type :map :schema {:pet pet}}
                  plain)]
        (should-contain "pet\n──" out)))

    ))
