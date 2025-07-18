(ns c3kit.apron.log-spec
  #?(:cljs (:require-macros [c3kit.apron.log-spec :refer [test-log-arity]]))
  (:require
    [c3kit.apron.log :as sut]
    [taoensso.timbre :as timbre]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [around describe it should= should-be-nil after
                                                      should-start-with should-end-with]]))

(defmacro test-log-arity [arity]
  `(let [args# (range ~arity)]
     (should-be-nil (apply timbre/-log! args#))
     (should= args# (last @sut/captured-logs))))

(describe "Log"
  (after (with-out-str (sut/off!)))

  (around [it] (sut/capture-logs (it)))

  (it "level"
    (with-out-str
      (sut/debug!)
      (should= :debug (sut/level))
      (sut/off!)
      (should= :report (sut/level))
      (sut/fatal!)
      (should= :fatal (sut/level))
      (sut/all!)
      (should= :trace (sut/level))))

  (it "-log! arity overrides"
    (sut/capture-logs
      (test-log-arity 9)
      (test-log-arity 10)
      (test-log-arity 11)
      (test-log-arity 12)))

  (it "capture-logs"
    (let [output (with-out-str (sut/capture-logs (sut/info "hello")))
          [config level ?ns-str ?file ?line ?column msg-type ?err vargs_ ?base-data callsite-id spying? :as log] (first @sut/captured-logs)]
      (should= "" output)
      (should= :info level)
      (should= ["hello"] @vargs_)
      (should= {:level :info :message "hello"} (first (sut/parse-captured-logs)))))

  (it "time"
    (sut/capture-logs
      (let [output (with-out-str (sut/time (println "foo")))]
        (should= "foo\n" output)))
    ;(prn "(log/captured-logs-str): " (log/captured-logs-str))
    (should-start-with "Elapsed time:" (sut/captured-logs-str))
    (should-end-with " msecs" (sut/captured-logs-str)))

  )
