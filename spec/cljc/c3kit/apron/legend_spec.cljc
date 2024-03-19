(ns c3kit.apron.legend-spec
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.legend :as sut]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it xit should= should-contain
                                                      should-not-contain should-throw should-not-be-nil with]]))


(def foo
  {:kind  (schema/kind :foo)
   :id    schema/id
   :name  {:type :string}
   :value {:type :int}})

(def bar
  {:kind   (schema/kind :bar)
   :shape  {:type :string}
   :colors {:type [:keyword]}})

(def legend (sut/build [foo bar]))

(describe "Legend"

  (it "schemas are normalized"
    (should= {:type :seq :spec {:type :keyword}} (get-in legend [:bar :colors])))

  (it "init!"
    (sut/init! legend)
    (should= #{:foo :bar} (set (keys sut/index)))
    (should= foo (:foo sut/index))
    (should= (schema/normalize-schema bar) (:bar sut/index)))

  (it "presents an entity contained"
    (let [bob {:kind :foo :name "Bob"}
          presentation (sut/present! bob)]
      (should= (schema/present! foo bob) presentation)))

  (it "complains when the entity kind is not in the legend"
    (should-throw (sut/present! {:kind :unknown})))

  (it "coerces an entity contained"
    (let [bob {:kind :foo :name "Bob"}
          coercion (sut/coerce! bob)]
      (should= (schema/coerce! foo bob) coercion)))

  (it "conforms an entity contained"
    (let [bob {:kind :foo :name "Bob"}
          conformation (sut/conform! bob)]
      (should= (schema/conform! foo bob) conformation)))

  )
