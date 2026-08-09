(ns c3kit.apron.log-spec
  (:require
    [c3kit.apron.log :as sut]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [around describe it should= should-be-nil after
                                                      should-contain should-not-contain
                                                      should-start-with should-end-with]]))

(describe "Log"
  (after (with-out-str (sut/off!)))

  (around [it] (sut/capture-logs (it)))

  (it "level controls"
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
          parsed (sut/parse-captured-logs)]
      (should= "" output)
      (should= [{:level :info :message "hello"}] parsed)
      (should= "hello" (sut/captured-logs-str))
      (should= :info (:level (first @sut/captured-logs)))
      (should= "hello" (:message (first @sut/captured-logs)))))

  (it "time"
    (sut/capture-logs
      (let [output (with-out-str (sut/time (println "foo")))]
        (should= "foo\n" output)))
    (should-start-with "Elapsed time:" (sut/captured-logs-str))
    (should-end-with " msecs" (sut/captured-logs-str)))

  (it "level filtering"
    (sut/warn!)
    (reset! sut/captured-logs [])
    (sut/info "nope")
    (sut/warn "yes-warn")
    (sut/error "yes-error")
    (should= [{:level :warn :message "yes-warn"}
              {:level :error :message "yes-error"}]
             (sut/parse-captured-logs)))

  (it "with-level is temporary"
    (sut/info!)
    (should= :info (sut/level))
    (sut/with-level :error
      (should= :error (sut/level))
      (reset! sut/captured-logs [])
      (sut/info "hidden")
      (sut/error "shown")
      (should= [{:level :error :message "shown"}] (sut/parse-captured-logs)))
    (should= :info (sut/level)))

  (it "set-level! reports on change only"
    (sut/info!)
    (reset! sut/captured-logs [])
    (sut/warn!)
    (should= 1 (count (sut/parse-captured-logs)))
    (should-contain "Setting log level: :warn" (sut/captured-logs-str))
    (reset! sut/captured-logs [])
    (sut/warn!)
    (should= [] (sut/parse-captured-logs)))

  (it "off! allows report, blocks info"
    (sut/off!)
    (reset! sut/captured-logs [])
    (sut/info "blocked")
    (sut/report "ok")
    (should= [{:level :report :message "ok"}] (sut/parse-captured-logs)))

  (it "all! allows trace"
    (sut/all!)
    (reset! sut/captured-logs [])
    (sut/trace "tiny")
    (should= [{:level :trace :message "tiny"}] (sut/parse-captured-logs)))

  (it "multi-arg message"
    (reset! sut/captured-logs [])
    (sut/info "a" :b 1)
    (should= "a :b 1" (:message (first (sut/parse-captured-logs)))))

  (it "capture-logs resets buffer each time"
    (sut/capture-logs (sut/info "first"))
    (should= ["first"] (map :message (sut/parse-captured-logs)))
    (sut/capture-logs (sut/info "second"))
    (should= ["second"] (map :message (sut/parse-captured-logs)))
    (should-not-contain "first" (sut/captured-logs-str)))

  (it "suppressed levels do not evaluate args"
    (sut/error!)
    (reset! sut/captured-logs [])
    (let [called? (atom false)
          result  (sut/info (do (reset! called? true) "x"))]
      (should= false @called?)
      (should-be-nil result)
      (should= [] (sut/parse-captured-logs)))
    (let [called? (atom false)]
      (sut/debug (do (reset! called? true) "y"))
      (sut/trace (do (reset! called? true) "z"))
      (should= false @called?)))

  (it "enabled levels evaluate args"
    (sut/info!)
    (reset! sut/captured-logs [])
    (let [called? (atom false)]
      (sut/info (do (reset! called? true) "x"))
      (should= true @called?)
      (should= [{:level :info :message "x"}] (sut/parse-captured-logs))))

  (it "capture-logs under with-level does not pollute global min-level"
    ;; Reproduce: with-level uses override; capture-logs must restore the atom, not effective level.
    (sut/info!)
    (should= :info (sut/level))
    (sut/with-level :error
      (should= :error (sut/level))
      (sut/capture-logs
        (sut/trace "captured"))
      ;; still inside with-level — override remains :error
      (should= :error (sut/level)))
    ;; after both forms exit, global must still be :info (not stuck at :trace or :error)
    (should= :info (sut/level))))
