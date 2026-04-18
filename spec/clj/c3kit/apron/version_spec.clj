(ns c3kit.apron.version-spec
  (:require [c3kit.apron.version :as sut]
            [speclj.core :refer [describe it should]]))

(describe "c3kit.apron.version"

  (it "exposes a current version string"
    (should (string? sut/current))
    (should (re-matches #"\d+\.\d+\.\d+" sut/current)))

  )
