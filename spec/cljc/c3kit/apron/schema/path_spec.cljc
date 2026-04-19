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

  (context "unparse"

    (it "emits a single keyword segment"
      (should= "a" (sut/unparse [[:key :a]])))

    (it "joins keyword segments with dots"
      (should= "a.b.c" (sut/unparse [[:key :a] [:key :b] [:key :c]])))

    (it "emits indices with brackets"
      (should= "points[0]" (sut/unparse [[:key :points] [:index 0]])))

    (it "emits string keys with quoted brackets"
      (should= "crew[\"bill\"]" (sut/unparse [[:key :crew] [:str "bill"]])))

    (it "emits wildcards with [*]"
      (should= "crew[*]" (sut/unparse [[:key :crew] [:wildcard]])))

    (it "escapes keywords with special chars using bracket form"
      (should= "a[:foo.bar]" (sut/unparse [[:key :a] [:key :foo.bar]])))

    (it "round-trips through parse for common segments"
      (let [path "a.b[0].c[\"x\"].d[*]"]
        (should= path (sut/unparse (sut/parse path)))))

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

    (it "throws on wildcard"
      (should-throw (sut/data-at {:crew {:joe "Joe"}} "crew.*")))

    (it "returns nil for an unknown path"
      (should= nil (sut/data-at {:a 1} "zz")))

    (context "lenient (one-way: keyword → string or integer key)"

      (it "strict default: keyword path does not match string key"
        (should= nil (sut/data-at {:crew {"joe" "Joe"}} "crew.joe")))

      (it "lenient: keyword path finds string key"
        (should= "Joe" (sut/data-at {:crew {"joe" "Joe"}} "crew.joe" {:lenient? true})))

      (it "lenient: keyword path finds integer key when name parses as int"
        (should= "zero" (sut/data-at {(keyword "0") "zero"} "[:0]" {:lenient? true}))
        (should= "zero" (sut/data-at {0 "zero"} "[:0]" {:lenient? true})))

      (it "lenient is not bidirectional: string bracket does NOT find keyword key"
        (should= nil (sut/data-at {:crew {:joe "Joe"}} "crew[\"joe\"]" {:lenient? true})))

      )

    ))
