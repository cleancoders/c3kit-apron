(ns c3kit.apron.bench-spec
  (:require [c3kit.apron.time :as time]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [should describe context should-not-be it should= should-have-invoked should-be should-not-have-invoked should-be-nil with-stubs stub]]
            [c3kit.apron.bench :as sut]))

(def body-fn (stub :body))

(defn wait-for [time]
  (let [later (time/from-now time)]
    (while (time/before? (time/now) later))))

(describe "Benchmarks"
  (with-stubs)

  (context "bench"
    (it "0 iterations"
      (should-be-nil (sut/bench 0 (body-fn)))
      (should-not-have-invoked :body))

    (it "-1 iteration"
      (should-be-nil (sut/bench -1 (body-fn)))
      (should-not-have-invoked :body))

    (it "1 iteration"
      (should (sut/bench 1 (body-fn)))
      (should-have-invoked :body {:times 1}))

    (it "calculates min, max, average and total"
      (let [{:keys [min max total avg]}
            (sut/bench 2 (wait-for (time/milliseconds 10)))]
        (should= 10 min 1)
        (should= 10 max 1)
        (should= 10 avg 1)
        (should= 20 total 1)))

    (it "missing body"
      (should-be-nil (sut/bench 1000))
      (should-not-have-invoked :body))

    (it "many items in body"
      (should (sut/bench 1
                (body-fn)
                (+ 1 2)
                (body-fn)))
      (should-have-invoked :body {:times 2}))

    (it "executes body 10 times"
      (let [{:keys [min max avg total]} (sut/bench 10 (body-fn))]
        (should-be double? min)
        (should-be double? max)
        (should-be double? avg)
        (should-be double? total)
        (should-not-be neg? min)
        (should-not-be neg? max)
        (should-not-be neg? avg)
        (should-not-be neg? total))
      (should-have-invoked :body {:times 10}))

    (it "increasing timings"
      (let [timings (atom (range))]
        (with-redefs [sut/millis-since (fn [_]
                                         (let [v (first @timings)]
                                           (swap! timings rest)
                                           v))]
          (let [{:keys [min max total avg]} (sut/bench 5 (body-fn))]
            (should= 0 min)
            (should= 4 max)
            (should= 2 avg)
            (should= 10 total)))))
    )
  )
