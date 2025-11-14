(ns c3kit.apron.doc
  (:require [c3kit.apron.schema :as schema]
            [clojure.string :as s]))

(def openapi-types
  {:any     :string
   :bigdec  :string
   :date    :string
   :double  :number
   :float   :number
   :ignore  :string
   :instant :string
   :int     :integer
   :keyword :string
   :long    :number
   :ref     :number
   :uri     :string
   :uuid    :string})

(defn- apron->json-types [type]
  (name (get openapi-types type type)))

(def openapi-formats
  {:date   :date
   :double :double
   :float  :float
   :long   :int64
   :ref    :int64
   :uri    :uri
   :uuid   :uuid})

(defn- required? [{:keys [validate validations]}]
  (boolean (some #{schema/present?} (conj (map :validate validations) validate))))

(defn- schema->parameter [name-field {:keys [type] :as schema}]
  (merge
    {:name   (name name-field)
     :in     "query"
     :schema {:type (apron->json-types type)}}
    (when-let [format (openapi-formats type)]
      {:format (name format)})
    (when (required? schema)
      {:required true})))

; TODO - distinguish between url/query params
(defn ->parameters [schema]
  (reduce-kv
    (fn [coll k v]
      (conj coll (schema->parameter k v)))
    []
    (get-in schema [:params :type])))

(defn- required-fields [schema]
  (keys (filter (fn [[_ v]] (required? v)) schema)))

(defn- maybe-required-fields [{:keys [type] :as _schema}]
  (when-let [required (seq (required-fields type))]
    {:required required}))

(defn apron->openapi-schema [{:keys [type] :as schema}]
  (cond
    (= :one-of type)
    {:oneOf (map apron->openapi-schema (:specs schema))}

    (= :seq type)
    {:type  "array"
     :items (apron->openapi-schema (:spec schema))}

    (sequential? type)
    {:type  "array"
     :items (apron->openapi-schema {:type (first type)})}

    (map? type)
    (merge {:type       "object"
            :properties (reduce-kv
                          (fn [m k type]
                            (assoc m k (apron->openapi-schema type)))
                          {}
                          type)}
           (maybe-required-fields schema))

    :else {:type (apron->json-types type)}))

(defn ->request-body [{:keys [body] :as _schema}]
  {:required (or (required? body) (map? (:type body)))
   :content  {"application/json"
              {:schema (apron->openapi-schema body)}}})

(defn ->openapi [{:keys [schema description]}]
  (cond-> {:description description}
          schema
          (assoc :content
                 {"application/json"
                  {:schema
                   (apron->openapi-schema schema)}})))

(defn ->responses [spec] (update-vals spec ->openapi))

(defn- ->request-keys [{:keys [params body] :as request-schema}]
  (cond-> {}
          params (assoc :parameters (->parameters request-schema))
          body (assoc :requestBody (->request-body request-schema))))

(defn- ->response-keys [response-schema]
  (when response-schema
    {:responses (->responses response-schema)}))

(defn- assoc-route [paths {:keys [path method summary request-schema response-schema] :as _route}]
  (assoc-in paths [path method]
            (merge {:summary summary}
                   (->request-keys request-schema)
                   (->response-keys response-schema))))

(defn routes->paths [routes]
  (reduce assoc-route {} routes))

(defn- integer-keys? [m]
  (every? integer? (keys m)))

(def nil?-or-map? (schema/nil?-or map?))

(defn- schema-map? [m]
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

(defn- maybe-invalid-doc [spec]
  (let [validated (schema/validate doc-schema spec)]
    (when (schema/error? validated)
      (throw (ex-info (s/join "; " (schema/message-seq validated)) spec)))))

(defn ->doc [spec]
  (or (maybe-invalid-doc spec)
      {:openapi "3.0.0"
       :info    {:title   (:title spec)
                 :version (:version spec)}
       :paths   (routes->paths (:routes spec))}))
