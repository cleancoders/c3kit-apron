(ns c3kit.apron.version
  "Runtime access to the apron version string."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def current
  (some-> (io/resource "c3kit/apron/VERSION") slurp str/trim))
