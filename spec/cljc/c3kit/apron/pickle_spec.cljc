(ns c3kit.apron.pickle-spec
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [after after-all around around-all before before before-all
                                                              context describe focus-context focus-describe focus-it it
                                                              pending should should-be should-be-a should-be-nil
                                                              should-be-same should-contain should-end-with should-fail
                                                              should-have-invoked should-invoke should-not should-not
                                                              should-not-be should-not-be-a should-not-be-nil
                                                              should-not-be-same should-not-contain should-not-end-with
                                                              should-not-have-invoked should-not-invoke
                                                              should-not-start-with should-not-throw should-not=
                                                              should-not== should-start-with should-throw should<
                                                              should<= should= should== should> should>= stub tags
                                                              with with-all with-stubs xit redefs-around]]
            [c3kit.apron.time :as time]
            [c3kit.apron.pickle :as sut]))

(deftype Wallace [cheese invention]
  sut/Pickleable
  (pickleable-type [_] :pickle-spec/wallace)
  (pickleable->map [_] {:cheese cheese :invention invention}))

(defmethod sut/map->pickleable :pickle-spec/wallace [_ {:keys [cheese invention]}]
  (Wallace. cheese invention))

(defrecord Gromit [expression paper]
  sut/Pickleable
  (pickleable-type [_] :pickle-spec/gromit)
  (pickleable->map [this] this))

(defmethod sut/map->pickleable :pickle-spec/gromit [_ m] (map->Gromit m))

(describe "Pickle"

  (it "scalars pickle"
    (should= nil (sut/pickle nil))
    (should= 0 (sut/pickle 0))
    (should= 42 (sut/pickle 42))
    (should= 0.0 (sut/pickle 0.0))
    (should= 3.14 (sut/pickle 3.14))
    (should= "foo" (sut/pickle "foo"))
    (should= :bar (sut/pickle :bar))
    (should= 'fizz (sut/pickle 'fizz)))

  (it "scalars unpickle"
    (should= nil (sut/unpickle nil))
    (should= 0 (sut/unpickle 0))
    (should= 42 (sut/unpickle 42))
    (should= 0.0 (sut/unpickle 0.0))
    (should= 3.14 (sut/unpickle 3.14))
    (should= "foo" (sut/unpickle "foo"))
    (should= :bar (sut/unpickle :bar))
    (should= 'fizz (sut/unpickle 'fizz)))

  #_(it "long strings"
      (should= 16 sut/LONG_STRING_LENGTH)
      (let [s       "This ia a long string that might be duplicated in the graph so it gets pickled as a ref."
            id      (sut/-unique-id s)
            pickled (sut/pickle s)]
        (should= {:_refs {id s} :_object {:_t :ref :_v id}} pickled)
        (should= s (sut/unpickle pickled))))

  (it "instant"
    (let [s       (time/utc 2025 02 28 7 25 0)
          id      (sut/-unique-id s)
          pickled (sut/pickle s)]
      (should= {:_refs {id {:_t :inst :_v (pr-str s)}} :_object {:_t :ref :_v id}} pickled)
      (should= s (sut/unpickle pickled))))

  (it "list"
    (let [s       [1 2 3]
          id      (sut/-unique-id s)
          pickled (sut/pickle s)]
      (should= {:_refs {id {:_t :seq :_v [1 2 3]}} :_object {:_t :ref :_v id}} pickled)
      (should= s (sut/unpickle pickled))))

  (it "set"
    (let [s       #{1 2 3}
          id      (sut/-unique-id s)
          pickled (sut/pickle s)]
      (should= :set (get-in pickled [:_refs id :_t]))
      (should= s (set (get-in pickled [:_refs id :_v])))
      ;(should= {:_refs {id {:_t :set :_v [1 2 3]}} :_object {:_t :ref :_v id}} pickled)
      (should= s (sut/unpickle pickled))))

  (it "map"
    (let [m       {:foo "bar"}
          id      (sut/-unique-id m)
          pickled (sut/pickle m)]
      (should= {:_refs {id {:_t :map, :_v {:foo "bar"}}} :_object {:_t :ref :_v id}} pickled)
      (should= m (sut/unpickle pickled))))

  (it "list with a map"
    (let [foobar  {:foo "bar"}
          obj     [foobar]
          pickled (sut/pickle obj)]
      (should= obj (sut/unpickle pickled))
      (should-contain (sut/-unique-id foobar) (:_refs pickled))
      (should-contain (sut/-unique-id obj) (:_refs pickled))))

  (it "map with a list"
    (let [foobar  [:foo "bar"]
          obj     {:list foobar}
          pickled (sut/pickle obj)]
      (should= obj (sut/unpickle pickled))
      (should-contain (sut/-unique-id foobar) (:_refs pickled))
      (should-contain (sut/-unique-id obj) (:_refs pickled))))

  (it "duplicates maps in a list"
    (let [foobar  {:foo "bar"}
          obj     [foobar foobar]
          pickled (sut/pickle obj)]
      (should= obj (sut/unpickle pickled))
      (should-contain (sut/-unique-id foobar) (:_refs pickled))
      (should-contain (sut/-unique-id obj) (:_refs pickled))))

  (it "defrecord"
    (let [gromit  (->Gromit "annoyed" "times")
          id      (sut/-unique-id gromit)
          pickled (sut/pickle gromit)]
      (should= {:_refs {id {:_t :pickle-spec/gromit, :_v (into {} gromit)}} :_object {:_t :ref :_v id}} pickled)
      (should= gromit (sut/unpickle pickled))))

  (it "deftype"
    (let [wallace   (->Wallace "munster" "rocket")
          id        (sut/-unique-id wallace)
          pickled   (sut/pickle wallace)
          unpickled (sut/unpickle pickled)]
      (should= {:_refs   {id {:_t :pickle-spec/wallace :_v {:cheese "munster" :invention "rocket"}}}
                :_object {:_t :ref :_v id}} pickled)
      (should= Wallace (type unpickled))
      (should= (sut/pickleable->map wallace) (sut/pickleable->map unpickled))))

  ;; MDM - cycles are not currently supported.
  ;;  They should only be possible in CLJS or using non-Clojure types.
  ;#?(:cljs
  ;   (focus-it "cyclic dependency - deftype"
  ;     (let [wallace   (->Wallace "munster" "rocket")
  ;           _         (set! (.-invention wallace) wallace)
  ;           id        (sut/unique-id wallace)
  ;           pickled   (sut/pickle wallace)
  ;           unpickled (sut/unpickle pickled)]
  ;       (should= {:_refs   {id {:_refs   {1 {:_t :pickle-spec/wallace
  ;                                              :_v {:cheese "munster" :invention {:_t :ref :_v id}}}}
  ;                                 :_object {:_t :ref :_v id}}}
  ;                   :_object {:_t :ref :_v id}} pickled)
  ;       (should= Wallace (type unpickled))
  ;       (should= (sut/pickleable->map wallace) (sut/pickleable->map unpickled)))))

  )