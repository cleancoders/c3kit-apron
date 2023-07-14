(ns c3kit.apron.env-spec
  (:require
    [c3kit.apron.env :as sut]
    [clojure.java.io :as io]
    [speclj.core :refer :all])
  (:import (java.io ByteArrayInputStream)))

(describe "env"

  (with-stubs)

  (context "read .env content"

    (it "missing"
      (let [result (sut/-read-properties "a-file-that-certainly-does-not-exist")]
        (should= {} result)))

    (it "empty"
      (with-redefs [io/reader (stub :io/reader {:return (io/reader (ByteArrayInputStream. (.getBytes "")))})]
        (let [result (sut/-read-properties ".env")]
          (should= {} result))))

    (it "values"
      (let [content "foo=bar\nFIZZ=BANG"]
        (with-redefs [io/reader (stub :io/reader {:return (io/reader (ByteArrayInputStream. (.getBytes content)))})]
          (let [result (sut/-read-properties ".env")]
            (should= {"foo" "bar" "FIZZ" "BANG"} result)))))
    )

  (context "env"

    (before (reset! sut/-overrides {}))
    (after (System/setProperty "PATH" "nil")) ;; PATH is not a typical JVM property, so fear not

    (it "from ENV"
      (should-not= nil (sut/env "PATH")))

    (it "from System properties"
      (System/setProperty "PATH" "sys-prop")
      (should= "sys-prop" (sut/env "PATH")))

    (it "from .env"
      (System/setProperty "PATH" "sys-prop")
      (with-redefs [sut/-locals (delay {"PATH" "local"})]
        (should= "local" (sut/env "PATH"))))

    (it "from override"
      (System/setProperty "PATH" "sys-prop")
      (with-redefs [sut/-locals (delay {"PATH" "local"})]
        (sut/override! "PATH" "override")
        (should= "override" (sut/env "PATH"))))

    (it "multiple keys"
      (sut/override! "FOO" "bar")
      (should= "bar" (sut/env "FOO" "FIZZ"))
      (should= "bar" (sut/env "FIZZ" "FOO")))

    )

  )