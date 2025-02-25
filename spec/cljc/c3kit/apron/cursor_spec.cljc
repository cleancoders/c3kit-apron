(ns c3kit.apron.cursor-spec
  (:require
    [c3kit.apron.cursor :refer [cursor]]
    [speclj.core #?(:clj :refer :cljs :refer-macros)
     [context describe it should= should-contain should-not-contain should-throw should]]))

(def base (atom {:a {:b {:c 0}}}))
(def a (cursor base [:a]))
(def b (cursor base [:a :b]))
(def c (cursor base [:a :b :c]))

(describe "cursor"

  (it "not an atom"
    (should-throw (cursor nil [:a]))
    (should-throw (cursor 0 [:a]))
    (should-throw (cursor "" [:a])))

  (it "pulling"
    (should= 0 (deref c))
    (should= 0 @c)
    (should= {:c 0} @b)
    (should= {:b {:c 0}} @a)
    (should= nil (deref (cursor a [:blah])))
    (should= @base (deref (cursor base []))))

  (it "swapping"
    (swap! c inc)
    (should= 1 @c)
    (should= {:c 1} @b)
    (should= {:b {:c 1}} @a)
    (should= {:a {:b {:c 1}}} @base))

  (it "resetting"
    (reset! c 3)
    (should= 3 @c)
    (should= {:c 3} @b)
    (should= {:b {:c 3}} @a)
    (should= {:a {:b {:c 3}}} @base))

  #?(:clj
     (it "swap-vals!"
       (reset! c 0)
       (let [[old new] (swap-vals! c inc)]
         (should= 0 old)
         (should= 1 new)
         (should= 1 @c))))

  #?(:clj
     (it "reset-vals!"
       (reset! c 0)
       (let [[old new] (reset-vals! c 8)]
         (should= 0 old)
         (should= 8 new)
         (should= 8 @c))))

  (it "equality"
    (should= true (= c c))
    (should= false (= c (cursor base [:a :b :c])))
    (should= false (= c b)))

  (it "printing"
    (reset! c 0)
    (should= "#<Cursor: 0 @[:a :b :c]>" (pr-str c)))

  (it "watching"
    (let [change (atom nil)
          a      (atom {:b 0})
          b      (cursor a [:b])]
      (add-watch b :test (fn [k r o n] (reset! change [k r o n])))
      (swap! b inc)
      (should= [:test b 0 1] @change)))

  (it "stop watching"
    (let [change (atom nil)
          a      (atom {:b 0})
          b      (cursor a [:b])]
      (add-watch b :test (fn [k r o n] (reset! change [k r o n])))
      (remove-watch b :test)
      (swap! b inc)
      (should= nil @change)))

  (it "multiple watchers"
    (let [change (atom nil)
          a      (atom {:b 0})
          b      (cursor a [:b])]
      (add-watch b :test1 (fn [k r o n] (swap! change conj [k r o n])))
      (add-watch b :test2 (fn [k r o n] (swap! change conj [k r o n])))
      (swap! b inc)
      (should= 2 (count @change))
      (should-contain [:test1 b 0 1] @change)
      (should-contain [:test2 b 0 1] @change)))
  )
