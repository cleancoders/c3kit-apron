#!/usr/bin/env bb
;; Writes apron's spec-schema to target/spec-schema.json as an OpenAPI 3.0
;; document. Named sub-specs go into components.schemas and are referenced
;; via $ref at use sites. Keys are sorted alphabetically at every level.
;;
;; Run from the apron project root:   ./dev/spec-schema-json.bb

(require '[babashka.fs :as fs]
         '[cheshire.core :as json]
         '[clojure.walk :as walk]
         '[c3kit.apron.schema :as schema]
         '[c3kit.apron.schema.openapi :as oapi])

(defn- deep-sort-keys [v]
  (walk/postwalk
    (fn [x]
      (if (map? x)
        (into (sorted-map-by (fn [a b] (compare (str a) (str b)))) x)
        x))
    v))

(let [{:keys [refs]} (oapi/apron->openapi-schema+refs
                       {:type        :map
                        :name        :spec-schema
                        :schema      schema/spec-schema
                        :description "The shape every apron spec conforms to."})
      doc            {:openapi    "3.0.0"
                      :info       {:title "apron spec-schema" :version "1.0.0"}
                      :components {:schemas refs}}]
  (fs/create-dirs "target")
  (spit "target/spec-schema.json"
        (json/generate-string (deep-sort-keys doc) {:pretty true}))
  (println "Wrote target/spec-schema.json"))
