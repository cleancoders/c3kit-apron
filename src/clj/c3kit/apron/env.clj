(ns c3kit.apron.env
  (:require [clojure.java.io :as io])
  (:import (java.io FileNotFoundException)))

(defn -read-properties [readable]
  (let [props (java.util.Properties.)]
    (try
      (.load props (io/reader readable))
      (catch FileNotFoundException _))
    props))


(def -overrides (atom {}))
(def -locals (delay (-read-properties ".env")))

(defn override!
  "Override the value of any key that may exist in the environment."
  [key value]
  (swap! -overrides assoc key value))

(defn env
  "Resolves the key to the first matching value with the following priority:
    1) overridden values
    2) '.env' file (Java Properties format)
    3) Java System properties
    4) Environment variables"
  ([key]
  (or (get @-overrides key)
      (get @-locals key)
      (System/getProperty key)
      (System/getenv key)))
  ([key & keys]
   (->> (cons key keys)
        (map env)
        (filter some?)
        first)))