(ns c3kit.apron.refresh-spec
  (:require
    [c3kit.apron.log :as log]
    [c3kit.apron.refresh :as sut]
    [clojure.string :as str]
    [clojure.tools.namespace.reload :as reload]
    [clojure.tools.namespace.track :as track]
    [speclj.core :refer :all])
  (:import (java.io File)))

(describe "c3kit.apron.refresh"

  (context "ns-to-filenames"

    (it "simple namespace returns default .clj and .cljc paths"
      (should= ["c3kit/apron/refresh.clj" "c3kit/apron/refresh.cljc"]
               (sut/ns-to-filenames 'c3kit.apron.refresh)))

    (it "dashes in namespace segments become underscores in paths"
      (should= ["foo_bar/baz_qux.clj" "foo_bar/baz_qux.cljc"]
               (sut/ns-to-filenames 'foo-bar.baz-qux)))

    (it "single-segment namespace"
      (should= ["foo.clj" "foo.cljc"]
               (sut/ns-to-filenames 'foo)))

    (it "custom extensions override the default list"
      (should= ["foo/bar.cljs"]
               (sut/ns-to-filenames 'foo.bar [".cljs"])))

    (it "preserves extension order"
      (should= ["foo.clj" "foo.cljs"]
               (sut/ns-to-filenames 'foo [".clj" ".cljs"]))))

  (context "ns-to-file"

    (it "returns a File that exists for a namespace on the classpath"
      (let [^File f (sut/ns-to-file 'c3kit.apron.refresh)]
        (should-not-be-nil f)
        (should (.exists f))
        ;; accept either extension — today it is refresh.clj,
        ;; after the bb move it will be refresh.cljc
        (should (or (str/ends-with? (.getName f) "refresh.clj")
                    (str/ends-with? (.getName f) "refresh.cljc")))))

    (it "returns nil for a namespace that has no source file"
      (should-be-nil (sut/ns-to-file 'no.such.namespace.exists)))

    (it "returns nil when searching with extensions that do not match"
      (should-be-nil (sut/ns-to-file 'c3kit.apron.refresh [".nope"]))))

  (context "scan"

    (it "iterates all-ns without throwing on either JVM or bb"
      ;; Regression: an earlier version used (map #(.name %)) which fails
      ;; under bb because sci.lang.Namespace disallows .name interop.
      ;; ns-name is the portable clojure.core equivalent that works on both
      ;; clojure.lang.Namespace and sci.lang.Namespace.
      (should-not-throw (sut/scan {})))

    (it "populates ::track/files with matching-prefix namespace source files"
      ;; c3kit.apron.refresh itself is loaded and matches the default
      ;; "c3kit" prefix, so scan must pick it up.
      (let [tracker (sut/scan {})
            files   (:clojure.tools.namespace.dir/files tracker)
            refresh-file (sut/ns-to-file 'c3kit.apron.refresh)]
        (should-not-be-nil refresh-file)
        (should-contain refresh-file files)))

    (it "is a no-op when the prefix matches no loaded namespaces"
      (let [original-prefix @sut/prefix]
        (try
          (reset! sut/prefix "no.such.prefix.matches.anything")
          (let [tracker {:clojure.tools.namespace.dir/files #{}}]
            (should= tracker (sut/scan tracker)))
          (finally
            (reset! sut/prefix original-prefix))))))

  (context "reload"

    (it "is a no-op when ::track/load is empty"
      (let [tracker {::track/load  []
                     ::track/files #{}}]
        (should= tracker (sut/reload tracker))))

    (it "is a no-op when ::track/load is absent"
      (let [tracker {::track/files #{}}]
        (should= tracker (sut/reload tracker)))))

  (context "print-error"

    (around [it] (log/capture-logs (it)))

    (it "returns the tracker unchanged when no error is present"
      (let [tracker {::track/files #{}}]
        (should= tracker (sut/print-error tracker))
        (should= "" (log/captured-logs-str))))

    (it "logs the error and returns the tracker when ::reload/error is present"
      (let [err     (Exception. "boom")
            tracker {::reload/error err}]
        (should= tracker (sut/print-error tracker))
        (should-contain "boom" (log/captured-logs-str)))))

  (context "bb-track-reload (bb-compatible reload backend)"

    (it "load-files each ns in ::track/load and clears them from the list"
      (let [^File temp-file (File/createTempFile "c3kit_refresh_ok" ".clj")]
        (try
          (spit temp-file "(ns c3kit-refresh-test.bb-sample) (def loaded-value 42)")
          (with-redefs [sut/ns-to-file (fn [ns]
                                         (when (= ns 'c3kit-refresh-test.bb-sample)
                                           temp-file))]
            (let [tracker {::track/load ['c3kit-refresh-test.bb-sample]}
                  result  (#'sut/bb-track-reload tracker)]
              (should (map? result))
              (should= [] (::track/load result))
              (should= 42 @(ns-resolve 'c3kit-refresh-test.bb-sample 'loaded-value))))
          (finally
            (.delete temp-file)))))

    (it "passes through namespaces where ns-to-file returns nil"
      (with-redefs [sut/ns-to-file (constantly nil)]
        (let [tracker {::track/load ['some.phantom.ns]}
              result  (#'sut/bb-track-reload tracker)]
          ;; nil file means skip; tracker state unchanged for that entry
          (should= tracker result))))

    (it "records error state and short-circuits when load-file throws"
      (let [^File temp-file (File/createTempFile "c3kit_refresh_bad" ".clj")]
        (try
          (spit temp-file "(ns c3kit-refresh-test.bad-sample) (/ 1 0)")
          (with-redefs [sut/ns-to-file (fn [ns]
                                         (when (= ns 'c3kit-refresh-test.bad-sample)
                                           temp-file))]
            (let [tracker {::track/load ['c3kit-refresh-test.bad-sample
                                          'c3kit-refresh-test.unreached]}
                  result  (#'sut/bb-track-reload tracker)]
              ;; reduce unwraps Reduced, so the result is a plain map
              (should (map? result))
              (should-not-be-nil (::reload/error result))
              (should= 'c3kit-refresh-test.bad-sample (::reload/error-ns result))
              ;; the second ns was never reached (short-circuit), so ::track/load still has it
              (should-contain 'c3kit-refresh-test.unreached (::track/load result))))
          (finally
            (.delete temp-file))))))

  )
