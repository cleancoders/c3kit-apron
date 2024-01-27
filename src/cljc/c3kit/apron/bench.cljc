(ns c3kit.apron.bench
  #?(:cljs (:require-macros [c3kit.apron.bench :refer [elapsed-time bench]]))
  #?(:cljs (:require [goog.object :as gobj])))

#?(:cljs (def performance (gobj/get js/window "performance")))
(defn millis-now []
  #?(:clj  (/ (System/nanoTime) 1000000.0)
     :cljs (js-invoke performance "now")))

(defn millis-since [millis-start] (- (millis-now) millis-start))

(defmacro elapsed-time
  "Evaluates the body and returns the elapsed time in milliseconds."
  [& body]
  `(let [start# (millis-now)]
     (do ~@body)
     (millis-since start#)))

(defmacro bench
  "Test how long a body will take to execute.
   This will only `(do body)`. If you need to realize
   all results in a sequence, wrap your body in a `doall`.
   Body is executed n times."
  [n & body]
  (when (seq body)
    `(let [n# ~n]
       (when (pos? n#)
         (let [timings# (repeatedly n# #(elapsed-time ~@body))
               total#   (apply + timings#)]
           {:min   (apply min timings#)
            :max   (apply max timings#)
            :total total#
            :avg   (/ total# n#)})))))
