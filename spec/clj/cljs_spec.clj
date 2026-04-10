(ns cljs-spec
  (:require [speclj.core :refer :all]))

(defn jvm? []
  (nil? (System/getProperty "babashka.version")))

(when (jvm?)
  (describe "cljs"

    (with-stubs)

    (it "closes page context browser and playwright resources"
      (let [close-browser-resources! (requiring-resolve 'cljs/close-browser-resources!)
            calls                    (atom [])
            closeable                (fn [label]
                                       (reify java.io.Closeable
                                         (close [_] (swap! calls conj label))))]
        (close-browser-resources! {:page       (closeable :page)
                                   :context    (closeable :context)
                                   :browser    (closeable :browser)
                                   :playwright (closeable :playwright)})
        (should= [:page :context :browser :playwright] @calls)))

    (it "returns one-shot status after closing browser resources"
      (let [run-specs                    (requiring-resolve 'cljs/run-specs)
            create-browser-resources-var (requiring-resolve 'cljs/create-browser-resources)
            configure-page-var           (requiring-resolve 'cljs/configure-page!)
            run-specs-once-var           (requiring-resolve 'cljs/run-specs-once)
            close-browser-resources-var  (requiring-resolve 'cljs/close-browser-resources!)
            page                         (Object.)
            resources                    {:page page}]
        (with-redefs-fn {create-browser-resources-var (stub :create-browser-resources {:return resources})
                         configure-page-var           (stub :configure-page!)
                         run-specs-once-var           (stub :run-specs-once {:return 7})
                         close-browser-resources-var  (stub :close-browser-resources!)}
          #(do
             (should= 7 (run-specs))
             (should-have-invoked :configure-page! {:with [page]})
             (should-have-invoked :run-specs-once {:with [page]})
             (should-have-invoked :close-browser-resources! {:with [resources]})))))))
