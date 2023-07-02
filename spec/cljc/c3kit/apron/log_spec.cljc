(ns c3kit.apron.log-spec
  (:require
    [c3kit.apron.log :as log]
    [c3kit.apron.log :as sut]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it xit should= should-contain
                                                      should-not-contain should-throw should-be-a with
                                                      should-not= after]]
    ))

(describe "Log"

  (after (with-out-str (log/off!)))

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

  (it "capture-logs"
    (let [output (with-out-str (sut/capture-logs (log/info "hello")))
          [config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id spying?]  (first @sut/captured-logs)]
      (should= "" output)
      (should= :info level)
      (should= ["hello"] @vargs_)
      (should= {:level :info :message "hello"} (first (sut/parse-captured-logs)))))
  )

