(ns c3kit.apron.log-spec
  (:require
    [c3kit.apron.log :as sut]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [describe it should= after]]))

(describe "Log"

  (after (with-out-str (sut/off!)))

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
    (let [output (with-out-str (sut/capture-logs (sut/info "hello")))
          [config level ?ns-str ?file ?line ?column msg-type ?err vargs_ ?base-data callsite-id spying? :as log] (first @sut/captured-logs)]
      (should= "" output)
      (should= :info level)
      (should= ["hello"] @vargs_)
      (should= {:level :info :message "hello"} (first (sut/parse-captured-logs)))))
  )
