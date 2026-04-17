#!/usr/bin/env bb
;; Writes apron's spec-schema to target/spec-schema.md using the
;; markdown-table renderer (with :name-based ref dedup).
;;
;; Run from the apron project root:   ./dev/spec-schema-md.bb

(require '[babashka.fs :as fs]
         '[c3kit.apron.schema :as schema]
         '[c3kit.apron.schema.markdown :as md])

(fs/create-dirs "target")
(spit "target/spec-schema.md"
      (str "# apron spec-schema\n\n"
           "The shape every apron spec conforms to.\n\n"
           (md/schema->markdown-table {:type :map :schema schema/spec-schema})
           "\n"))
(println "Wrote target/spec-schema.md")
