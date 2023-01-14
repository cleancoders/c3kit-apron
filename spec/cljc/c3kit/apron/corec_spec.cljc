(ns c3kit.apron.corec-spec
  (:require
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.time :as time]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should= should should-not should-be-nil]]))

(defn should-be-lazy [thing]
  (should= #?(:clj  clojure.lang.LazySeq
              :cljs cljs.core/LazySeq) (type thing)))

(describe "Core Common"

  ;(it "encodes and decodes ids"
  ;    (should= 123 (ccc/hash->id (ccc/id->hash 123)))
  ;    (should= 321 (ccc/hash->id (ccc/id->hash 321)))
  ;    (should= 277076930200755 (ccc/hash->id (ccc/id->hash 277076930200755)))
  ;    (should= 277076930200756 (ccc/hash->id (ccc/id->hash 277076930200756))))

  ;(it "hashid hard coded values to make sure they're the same in clj/cljs"
  ;    (should= "BVyGj6x2" (ccc/id->hash 1))
  ;    (should= 1 (ccc/hash->id "BVyGj6x2"))
  ;    (should= "7ayR3Jzb" (ccc/id->hash 42))
  ;    (should= 42 (ccc/hash->id "7ayR3Jzb"))
  ;    ;; MDM - seems to break down at values much higher than below.  This is plenty big for datomic ids as fas as I've seen.
  ;    (should= "jREgpG5GpaB" (ccc/id->hash 999999999999999))
  ;    (should= 999999999999999 (ccc/hash->id "jREgpG5GpaB")))


  (context "nand"
    (it "no arguments" (should= false (ccc/nand)))
    (it "one falsy argument" (should= true (ccc/nand nil)))
    (it "one truthy argument" (should= false (ccc/nand 1)))
    (it "two truthy arguments" (should= false (ccc/nand 1 2)))
    (it "two falsy arguments" (should= true (ccc/nand false nil)))
    (it "three truthy arguments" (should= false (ccc/nand 1 2 3)))
    (it "three falsy arguments" (should= true (ccc/nand false nil (not true))))
    (it "two truthy and one falsy argument" (should= true (ccc/nand 1 2 false)))
    (it "truthy then falsy argument" (should= true (ccc/nand 1 nil)))
    (it "falsy then truthy argument" (should= true (ccc/nand nil 1)))
    (it "lazy evaluation on the first falsy value" (should= true (ccc/nand nil (/ 1 0))))
    (it "evaluates each form exactly once"
      (let [flag (atom false)]
        (should= false (ccc/nand (swap! flag not) true))
        (should= true @flag))))

  (context "nor"
    (it "no arguments" (should= true (ccc/nor)))
    (it "one falsy argument" (should= true (ccc/nor nil)))
    (it "one truthy argument" (should= false (ccc/nor 1)))
    (it "two truthy arguments" (should= false (ccc/nor 1 2)))
    (it "two falsy arguments" (should= true (ccc/nor false nil)))
    (it "three truthy arguments" (should= false (ccc/nor 1 2 3)))
    (it "three falsy arguments" (should= true (ccc/nor false nil (not true))))
    (it "two truthy and one falsy argument" (should= false (ccc/nor 1 2 false)))
    (it "truthy then falsy argument" (should= false (ccc/nor 1 nil)))
    (it "falsy then truthy argument" (should= false (ccc/nor nil 1)))
    (it "lazy evaluation on the first truthy value" (should= false (ccc/nor 1 (/ 1 0))))
    (it "evaluates each form exactly once"
      (let [flag (atom true)]
        (should= true (ccc/nor (swap! flag not) nil))
        (should= false @flag))))

  (context "xor"
    (it "no arguments" (should-be-nil (ccc/xor)))
    (it "one nil argument" (should-be-nil (ccc/xor nil)))
    (it "one false argument" (should-be-nil (ccc/xor false)))
    (it "one truthy argument" (should= 1 (ccc/xor 1)))
    (it "nil then false arguments" (should-be-nil (ccc/xor nil false)))
    (it "false then nil arguments" (should-be-nil (ccc/xor false nil)))
    (it "two truthy arguments" (should-be-nil (ccc/xor true 1)))
    (it "falsy then truthy arguments" (should= 1 (ccc/xor false 1)))
    (it "truthy then falsy arguments" (should= 1 (ccc/xor 1 false)))
    (it "truthy, falsy, then truthy arguments" (should-be-nil (ccc/xor 1 nil 2)))
    (it "lazy evaluation on the second truthy value" (should-be-nil (ccc/xor 1 2 (/ 1 0))))
    (it "four arguments with one truthy value" (should= 4 (ccc/xor nil false nil 4)))
    (it "four arguments with two truthy values" (should= nil (ccc/xor nil false 3 4)))
    (it "evaluates each form exactly once"
      (let [flag (atom 0)]
        (should= 1 (ccc/xor (swap! flag inc) nil nil))
        (should= 1 @flag))))

  (context "->options"

    (it "nil -> {}"
      (should= {} (ccc/->options [nil]))
      )

    )

  (it "new-uuid"
    (should= 10 (->> (repeatedly ccc/new-uuid)
                     (take 10)
                     set
                     count)))

  (it "conjv"
    (let [result (ccc/conjv (list 1 2 3) 4)]
      (should= [1 2 3 4] result)
      (should= true (vector? result))))

  (it "concatv"
    (let [result (ccc/concatv (list 1 2 3) (list 4))]
      (should= [1 2 3 4] result)
      (should= true (vector? result))))

  (it "dissocv"
    (let [result (ccc/dissocv [1 2 3] 1)]
      (should= [1 3] result)
      (should= true (vector? result))))

  (it "assocv"
    (let [result (ccc/assocv [1 2 3] 1 :foo)]
      (should= [1 :foo 2 3] result)
      (should= true (vector? result))))

  (it "removev"
    (let [result (ccc/removev even? (list 1 2 3 4))]
      (should= [1 3] result)
      (should= true (vector? result))))

  (it "removev="
    (let [result (ccc/removev= (list 1 2 3 4) 2)]
      (should= [1 3 4] result)
      (should= true (vector? result))))

  (context "ffilter"
    (for [coll [nil [] [nil]]]
      (it (str "is nil when " (pr-str coll))
        (should-be-nil (ccc/ffilter any? coll))))
    (it "single-element collection"
      (should= 1 (ccc/ffilter any? [1])))
    (it "two-element collection"
      (should= 2 (ccc/ffilter any? [2 1])))
    (it "first item of filtered result"
      (should= :a (ccc/ffilter identity [nil false :a :b])))
    (it "first item of no results"
      (should-be-nil (ccc/ffilter number? [nil false :a :b]))))

  (context "map-some"
    (it "removes nil values from mapping"
      (should= [] (ccc/map-some identity nil))
      (should= [] (ccc/map-some identity []))
      (should= [1] (ccc/map-some identity [1]))
      (should= [1 2 3] (ccc/map-some identity [1 2 3]))
      (should= [false true false] (ccc/map-some even? [1 2 3]))
      (should= [1 3] (ccc/map-some :a [{:a 1} {:b 2} {:a 3 :b 4}]))
      (should= [2 4] (ccc/map-some :b [{:a 1} {:b 2} {:a 3 :b 4}]))
      (should= [] (ccc/map-some :c [{:a 1} {:b 2} {:a 3 :b 4}])))

    (it "is lazy"
      (should-be-lazy (ccc/map-some identity [1 2 3]))
      (should-be-lazy (ccc/map-some identity [1]))
      (let [empty (ccc/map-some identity [])]
        #?(:clj  (should= (class clojure.lang.PersistentList/EMPTY) (type empty))
           :cljs (should-be-lazy empty))))

    (it "accepts multiple collections"
      (should= [[1] [2] [3]] (ccc/map-some vector [1 2 3]))
      (should= [[1 4] [2 5] [3 6]] (ccc/map-some vector [1 2 3] [4 5 6]))
      (should= [[1 4 7] [2 5 8] [3 6 9]] (ccc/map-some vector [1 2 3] [4 5 6] [7 8 9]))
      (letfn [(maybe-even [x y z] (let [sum (+ x y z)] (when (even? sum) sum)))]
        (should= [12 18] (ccc/map-some maybe-even [1 2 3] [4 5 6] [7 8 9]))))

    (it "creates a transducer"
      (should= [1 2 3] (transduce (ccc/map-some identity) conj [1 2 3]))
      (should= [2 4] (eduction (ccc/map-some :a) (map inc) [{:a 1} {:b 2} {:a 3 :b 4}]))
      (should= [2 4] (sequence (comp (ccc/map-some :a) (map inc)) [{:a 1} {:b 2} {:a 3 :b 4}]))
      (should= [] (transduce (ccc/map-some identity) conj []))
      (should= [1 3] (transduce (ccc/map-some identity) conj [1 nil 3]))))

  (context "some-map"
    (it "removes nil values before mapping is applied"
      (should= [] (ccc/some-map identity nil))
      (should= [] (ccc/some-map identity []))
      (should= [1] (ccc/some-map identity [1]))
      (should= [] (ccc/some-map identity [nil]))
      (should= [1 2 3] (ccc/some-map identity [1 2 3]))
      (should= [false true] (ccc/some-map even? [1 nil 2]))
      (should= [4 2 11] (ccc/some-map inc [3 nil 1 10 nil]))
      (should= [nil 2 4] (ccc/some-map :b [{:a 1} {:b 2} {:a 3 :b 4}]))
      (should= [nil nil nil] (ccc/some-map :c [{:a 1} {:b 2} {:a 3 :b 4}])))

    (it "is lazy"
      (should-be-lazy (ccc/some-map identity [1 2 3]))
      (should-be-lazy (ccc/some-map identity [1]))
      (let [empty (ccc/some-map identity [])]
        #?(:clj  (should= (class clojure.lang.PersistentList/EMPTY) (type empty))
           :cljs (should-be-lazy empty)))
      (let [nils (ccc/some-map identity [nil nil nil])]
        #?(:clj  (should= (class clojure.lang.PersistentList/EMPTY) (type nils))
           :cljs (should-be-lazy nils))))

    (it "creates a transducer"
      (should= [1 2 3] (transduce (ccc/some-map identity) conj [1 2 3]))
      (should= [2 4] (eduction (ccc/some-map identity) (map inc) [1 nil 3]))
      (should= [2 4] (sequence (comp (ccc/some-map identity) (map inc)) [1 nil 3]))
      (should= [] (transduce (ccc/some-map identity) conj []))
      (should= [1 3] (transduce (ccc/some-map identity) conj [1 nil 3]))))

  (context "rsort"
    (it "a nil collection"
      (should= [] (ccc/rsort nil)))
    (it "an empty collection"
      (should= [] (ccc/rsort [])))
    (it "a single-element collection"
      (should= [1] (ccc/rsort [1])))
    (it "an already reverse-sorted collection"
      (should= [5 4 3 2 1] (ccc/rsort [5 4 3 2 1])))
    (it "a regular-sorted collection"
      (should= [5 4 3 2 1] (ccc/rsort [1 2 3 4 5])))
    (it "a shuffled collection"
      (should= [5 4 3 2 1] (ccc/rsort [4 5 1 3 2])))
    (it "by custom compare function"
      (should= [[1 5] [2 4] [3 3] [4 2] [5 1]]
               (ccc/rsort
                 (fn [x y] (compare (second x) (second y)))
                 [[5 1] [4 2] [3 3] [2 4] [1 5]]))))

  (context "rsort"
    (it "a nil collection"
      (should= [] (ccc/rsort-by :x nil)))
    (it "an empty collection"
      (should= [] (ccc/rsort-by :x [])))
    (it "a single-element collection"
      (should= [{:x 1}] (ccc/rsort-by :x [{:x 1}])))
    (it "an already reverse-sorted collection"
      (let [coll [{:x 5} {:x 4} {:x 3} {:x 2} {:x 1}]]
        (should= (reverse (sort-by :x coll)) (ccc/rsort-by :x coll))))
    (it "a regular-sorted collection"
      (let [coll [{:x 1} {:x 2} {:x 3} {:x 4} {:x 5}]]
        (should= (reverse (sort-by :x coll)) (ccc/rsort-by :x coll))))
    (it "a shuffled collection"
      (let [coll [{:x 4} {:x 5} {:x 1} {:x 3} {:x 2}]]
        (should= (reverse (sort-by :x coll)) (ccc/rsort-by :x coll))))
    (it "by custom compare function"
      (let [coll       [{:a [5 1]} {:a [4 2]} {:a [3 3]} {:a [2 4]} {:a [1 5]}]
            compare-fn (fn [x y] (compare (second x) (second y)))]
        (should= (reverse (sort-by :a compare-fn coll))
                 (ccc/rsort-by :a compare-fn coll)))))


  (it "max-v"
    (should-be-nil (ccc/max-v))
    (should-be-nil (ccc/max-v nil))
    (should= 0 (ccc/max-v 0))
    (should= 1 (ccc/max-v nil 1))
    (should= 1 (ccc/max-v 1 nil))
    (should= 1 (ccc/max-v 0 1))
    (should= 1 (ccc/max-v 1 0))
    (should= "ABC" (ccc/max-v "ABB" "ABC")))

  (it "min-v"
    (should-be-nil (ccc/min-v))
    (should-be-nil (ccc/min-v nil))
    (should= 0 (ccc/min-v 0))
    (should-be-nil (ccc/min-v nil 1))
    (should-be-nil (ccc/min-v 1 nil))
    (should= 0 (ccc/min-v 0 1))
    (should= 0 (ccc/min-v 1 0))
    (should= "ABB" (ccc/min-v "ABB" "ABC")))

  (context "max-k"
    (it "empty collection" (should-be-nil (ccc/max-by :a [])))
    (it "one item"
      (should= {:a 1} (ccc/max-by :a [{:a 1}]))
      (should= {:a 1} (ccc/max-by :b [{:a 1}])))
    (it "two items"
      (should= {:b 2} (ccc/max-by :b [{:a 1} {:b 2}]))
      (should= {:b 2} (ccc/max-by :b [{:b 1} {:b 2}]))
      (should= {:b 3} (ccc/max-by :b [{:b 3} {:b 2}])))
    (it "three items"
      (should= {:b 5} (ccc/max-by :b [{:a 1} {:b 2} {:b 5}]))
      (should= {:b 3} (ccc/max-by :b [{:b 3} {:b 2} {:b 1}]))
      (should= {:b 1} (ccc/max-by :b [{:a 3} {:a 2} {:b 1}])))
    (it "non-keyword key"
      (should= {"b" 2} (ccc/max-by "b" [{"b" 1} {"b" 2}]))
      (should= [5 4 3] (ccc/max-by 0 [[5 4 3] [3 4 5]]))
      (should= [3 4 5] (ccc/max-by 2 [[5 4 3] [3 4 5]])))
    (it "compares instant"
      (let [now      (time/now)
            after-5  (time/after now (time/minutes 5))
            before-5 (time/before now (time/minutes 5))]
        (should= {:time after-5} (ccc/max-by :time [{:time now} {:time after-5} {:time before-5}]))))
    (it "with comparer"
      (let [comparer #(cond (and (even? %1) (even? %2)) 0
                            (even? %1) 1
                            :else -1)]
        (should= {:a 4} (ccc/max-by :a comparer [{:a 4} {:a 3} {:a 5}])))))

  (context "min-k"
    (it "empty collection"
      (should-be-nil (ccc/min-by :a [])))

    (it "one item"
      (should= {:a 1} (ccc/min-by :a [{:a 1}]))
      (should= {:a 1} (ccc/min-by :b [{:a 1}])))

    (it "two items"
      (should= {:a 1} (ccc/min-by :b [{:a 1} {:b 2}]))
      (should= {:b 1} (ccc/min-by :b [{:b 1} {:b 2}]))
      (should= {:b 2} (ccc/min-by :b [{:b 3} {:b 2}])))

    (it "three items"
      (should= {:a 1} (ccc/min-by :b [{:a 1} {:b 2} {:b 5}]))
      (should= {:b 1} (ccc/min-by :b [{:b 3} {:b 2} {:b 1}]))
      (should= {:a 3} (ccc/min-by :b [{:a 3} {:a 2 :b 2} {:b 1}])))

    (it "non-keyword key"
      (should= {"b" 1} (ccc/min-by "b" [{"b" 1} {"b" 2}]))
      (should= [3 4 5] (ccc/min-by 0 [[5 4 3] [3 4 5]]))
      (should= [5 4 3] (ccc/min-by 2 [[5 4 3] [3 4 5]])))

    (it "compares instant"
      (let [now      (time/now)
            after-5  (time/after now (time/minutes 5))
            before-5 (time/before now (time/minutes 5))]
        (should= {:time before-5} (ccc/min-by :time [{:time now} {:time after-5} {:time before-5}]))))

    (it "with comparer"
      (let [comparer #(cond (odd? %1) -1 (odd? %2) 1 :else 0)
            things   [{:a 2} {:a 3} {:a 6}]]
        (should= {:a 3} (ccc/min-by :a comparer things))
        (should= {:a 6} (ccc/min-by :a (comparator >) things))
        (should= {:a 2} (ccc/min-by :a (comparator <) things)))))

  (it "formats"
    (should= "Number 9" (ccc/formats "Number %s" 9)))

  (it "not-blank?"
    (should (ccc/not-blank? "a"))
    (should (ccc/not-blank? "\r\n\t a "))
    (should-not (ccc/not-blank? "\r"))
    (should-not (ccc/not-blank? "\n"))
    (should-not (ccc/not-blank? "\t"))
    (should-not (ccc/not-blank? " "))
    (should-not (ccc/not-blank? "\r\n\t ")))

  (it "remove-nils"
    (should= {:a 1} (ccc/remove-nils {:a 1 :b nil})))

  (it "ex?"
    (should= false (ccc/ex? "Not an exception"))
    (should= true (ccc/ex? #?(:clj (Exception. "yup") :cljs (js/Error. "yup")))))

  (it "invoke"
    (should= 0 (ccc/invoke (fn [] 0)))
    (should= {:foo :bar} (ccc/invoke identity {:foo :bar}))
    (should= 6 (ccc/invoke + 1 2 3))
    (should= "hello world" (-> {:some-fn str} :some-fn (ccc/invoke "hello" " " "world"))))

  (it "narity"
    (let [f (ccc/narity (fn [] :foo))]
      (should= :foo (f))
      (should= :foo (f 1 2 3))))

  )
