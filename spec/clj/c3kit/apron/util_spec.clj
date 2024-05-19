(ns c3kit.apron.util-spec
  (:require
    [c3kit.apron.log :as log]
    [c3kit.apron.util :as sut]
    [speclj.core :refer :all])
  (:import (java.io ByteArrayInputStream)))

(def foo "Foo")

(describe "util"

  (it "resolve-var"
    (should-throw (deref (sut/resolve-var 'foo/bar)))
    (should= "Foo" (deref (sut/resolve-var 'c3kit.apron.util-spec/foo))))

  (it "path->namespace"
    (should= "foo" (sut/path->namespace "foo.clj"))
    (should= "foo" (sut/path->namespace "foo.clj"))
    (should= "hello.world" (sut/path->namespace "hello/world.clj"))
    (should= "hello.cljwhatever" (sut/path->namespace "hello/cljwhatever.clj"))
    (should= "acme.foo.src.clj.hello" (sut/path->namespace "acme/foo/src/clj/hello.clj"))
    (should= "foo.bar.fizz-bang" (sut/path->namespace "foo/bar/fizz_bang"))
    (should= "fizz-bang" (sut/path->namespace "fizz_bang.clj"))
    (should= "foo.bar.fizz-bang" (sut/path->namespace "foo/bar/fizz_bang.clj")))

  (context "var-value"

    (around [it] (log/capture-logs (it)))

    (it "nil"
      (should= nil (sut/var-value nil))
      (should= "" (log/captured-logs-str)))

    (it "missing ns"
      (should= nil (sut/var-value 'foo/bar))
      (should= "Unable to resolve var: foo/bar java.io.FileNotFoundException: Could not locate foo__init.class, foo.clj or foo.cljc on classpath."
               (log/captured-logs-str)))

    (it "missing var"
      (should= nil (sut/var-value 'c3kit.apron.util-spec/bar))
      (should= "Unable to resolve var: c3kit.apron.util-spec/bar java.lang.Exception: No such var c3kit.apron.util-spec/bar"
               (log/captured-logs-str)))

    (it "success"
      (should= "Foo" (sut/var-value 'c3kit.apron.util-spec/foo))
      (should= "" (log/captured-logs-str)))

    )

  (context "config value"

    (it "nil"
      (should= nil (sut/config-value nil)))

    (it "value"
      (should= :foo (sut/config-value :foo)))

    (it "sym"
      (should= "Foo" (sut/config-value 'c3kit.apron.util-spec/foo)))

    )

  (it "md5"
    (should= "8622b9718771d75e07734684d6efa1dd" (sut/md5 "I'm a little teapot")))

  (it "stream->md5"
    (should= "8622b9718771d75e07734684d6efa1dd"
             (sut/stream->md5 (ByteArrayInputStream. (.getBytes "I'm a little teapot" "UTF-8")))))


  (context "resources-in"

    (it "namespace->path"
      (should= "foo/bar/fizz_bang" (sut/namespace->path "foo.bar.fizz-bang")))

    (it "file system"
      (let [result (sut/resources-in "c3kit.apron")]
        ;(prn "result: " result)
        (should-contain "app.clj" result)
        (should-contain "util.clj" result)
        (should-contain "log.cljc" result)))

    (it "jar file"
      (let [result (sut/resources-in "clojure.java")]
        (should-contain "io" result)
        (should-contain "io.clj" result)
        (should-contain "shell.clj" result)))

    (it "missing"
      (should= nil (sut/resources-in "some.missing.package")))

    )
  )
