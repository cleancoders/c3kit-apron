(ns c3kit.apron.schema.doc
  "Shared infrastructure for doc-format renderers (OpenAPI, markdown, ...).
   Describes the expected shape of a route/doc spec and provides helpers that
   are format-agnostic."
  (:require [c3kit.apron.schema :as schema]
            [clojure.string :as s]))

(defn required? [{:keys [validate validations]}]
  (boolean (some #{schema/present?} (conj (map :validate validations) validate))))

(defn required-fields [schema]
  (keys (filter (fn [[_ v]] (required? v)) schema)))

(defn integer-keys? [m]
  (every? integer? (keys m)))

(def nil?-or-map? (schema/nil?-or map?))

(defn schema-map? [m]
  (every? (comp nil?-or-map? :schema) (vals m)))

(def route-schema
  {:path            {:type :string :validate schema/present? :message "is a required string"}
   :method          {:type :keyword :validate schema/present? :message "is a required keyword"}
   :request-schema  {:type    {:params {:type :map :message "must be map"}
                               :body   {:type :map :message "must be map"}}
                     :message "must be map"}
   :response-schema {:type        :any                      ; should be :map, but break breaks validations. maybe apron bug?
                     :validations [{:validate nil?-or-map? :message "must be map"}
                                   {:validate integer-keys? :message "keys must be response codes (integers)"}
                                   {:validate schema-map? :message ":schema must be map"}]}})

(def doc-schema
  {:title   {:type :string :validate schema/present? :message "is required"}
   :version {:type :string :validate schema/present? :message "is required"}
   :routes  {:type :seq :spec {:type route-schema}}})

(defn maybe-invalid-doc [spec]
  (let [validated (schema/validate doc-schema spec)]
    (when (schema/error? validated)
      (throw (ex-info (s/join "; " (schema/message-seq validated)) spec)))))
