(ns build
  (:require [clojure.tools.build.api :as b]
            [cemerick.pomegranate.aether :as aether]))

(def lib 'com.cleancoders.c3kit/apron)
(def version "2.0.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (println "cleaning")
  (b/delete {:path "target"}))

(defn pom [_]
  (println "writing pom.xml")
  (b/write-pom {:basis basis
                :class-dir class-dir
                :lib lib
                :version version}))

(defn jar [_]
  (clean nil)
  (pom nil)
  (println "building" jar-file)
  (b/copy-dir {:src-dirs ["src/clj" "src/cljc"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [_]
  (jar nil)
  (aether/deploy {:coordinates [lib version]
                  :jar-file jar-file
                  :repository {"clojars" {:url "https://clojars.org/repo"
                                          :username (System/getenv "CLOJARS_USERNAME")
                                          :password (System/getenv "CLOJARS_PASSWORD")}}
                  :transfer-listener :stdout}))

