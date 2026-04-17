#!/usr/bin/env bb
;; Prints apron's spec-schema to stdout via c3kit.apron.schema.term.
;; Honors the NO_COLOR env var.
;;
;; Run from the apron project root:   ./dev/spec-schema-term.bb

(require '[c3kit.apron.schema :as schema]
         '[c3kit.apron.schema.term :as term])

(let [color? (not (System/getenv "NO_COLOR"))]
  (println)
  (println (term/schema->term
             {:type :map :schema schema/spec-schema}
             {:color? color?})))
