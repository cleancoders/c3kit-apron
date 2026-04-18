(ns c3kit.apron.schema.path-spec
  (:require [c3kit.apron.schema.path :as sut]
            [speclj.core #?(:clj :refer :cljs :refer-macros)
             [describe it should= should-throw context]]))

(describe "schema.path"

  (context "parse"

    (it "parses a single keyword segment"
      (should= [[:key :a]] (sut/parse "a")))

    (it "parses dot-separated keyword segments"
      (should= [[:key :a] [:key :b] [:key :c]] (sut/parse "a.b.c")))

    (it "parses a bracketed integer as an index"
      (should= [[:key :points] [:index 0]] (sut/parse "points[0]")))

    (it "parses [*] as a wildcard"
      (should= [[:key :crew] [:wildcard]] (sut/parse "crew[*]")))

    (it "parses .* as a wildcard"
      (should= [[:key :crew] [:wildcard]] (sut/parse "crew.*")))

    (it "parses [\"string\"] as a string key"
      (should= [[:key :crew] [:str "bill"]] (sut/parse "crew[\"bill\"]")))

    (it "parses [:kw] as a keyword literal"
      (should= [[:key :crew] [:key :joe]] (sut/parse "crew[:joe]")))

    )

  (context "schema-at"

    (it "returns the field spec for a simple keyword path"
      (let [schema {:name {:type :string}}]
        (should= {:type :string} (sut/schema-at schema "name"))))

    (it "descends into nested :schema under :map"
      (let [schema {:user {:type :map :schema {:name {:type :string}}}}]
        (should= {:type :string} (sut/schema-at schema "user.name"))))

    (it "resolves wildcard on :map to :value-spec"
      (let [schema {:crew {:type :map :value-spec {:type :map :schema {:name {:type :string}}}}}]
        (should= {:type :map :schema {:name {:type :string}}}
                 (sut/schema-at schema "crew.*"))))

    (it "resolves wildcard on :seq to :spec"
      (let [schema {:points {:type :seq :spec {:type :int}}}]
        (should= {:type :int} (sut/schema-at schema "points[*]"))))

    (it "[N] on :seq behaves like wildcard"
      (let [schema {:points {:type :seq :spec {:type :int}}}]
        (should= {:type :int} (sut/schema-at schema "points[0]"))))

    (it "keyword bracket descends like dot"
      (let [schema {:user {:type :map :schema {:name {:type :string}}}}]
        (should= {:type :string} (sut/schema-at schema "user[:name]"))))

    (it "returns nil for an unknown path"
      (should= nil (sut/schema-at {:a {:type :int}} "zz")))

    )

  (context "data-at"

    (it "returns the value at a simple keyword path"
      (should= "Joe" (sut/data-at {:name "Joe"} "name")))

    (it "descends into nested maps"
      (should= "Joe" (sut/data-at {:user {:name "Joe"}} "user.name")))

    (it "indexes into seqs with [N]"
      (should= 20 (sut/data-at {:points [10 20 30]} "points[1]")))

    (it "reads string keys with [\"str\"]"
      (should= {:name "Bill"} (sut/data-at {:crew {"bill" {:name "Bill"}}} "crew[\"bill\"]")))

    (it "reads keyword keys with [:kw]"
      (should= "Joe" (sut/data-at {:crew {:joe "Joe"}} "crew[:joe]")))

    (it "throws on wildcard (data has concrete values, not templates)"
      (should-throw (sut/data-at {:crew {:joe "Joe"}} "crew.*")))

    (it "returns nil for an unknown path"
      (should= nil (sut/data-at {:a 1} "zz")))

    ))
