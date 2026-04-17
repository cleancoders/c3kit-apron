(ns c3kit.apron.schema.openapi
  (:require [c3kit.apron.schema.doc :as doc]))

(def openapi-types
  {:any       :string
   :bigdec    :string
   :date      :string
   :double    :number
   :float     :number
   :ignore    :string
   :instant   :string
   :int       :integer
   :keyword   :string
   :kw-ref    :string
   :long      :number
   :map       :object
   :ref       :number
   :timestamp :string
   :uri       :string
   :uuid      :string})

(defn- apron->json-types [type]
  (name (get openapi-types type type)))

(def openapi-formats
  {:date      :date
   :double    :double
   :float     :float
   :instant   :date-time
   :long      :int64
   :ref       :int64
   :timestamp :date-time
   :uri       :uri
   :uuid      :uuid})

(defn- schema->parameter [name-field {:keys [type] :as schema}]
  (merge
    {:name   (name name-field)
     :in     "query"
     :schema {:type (apron->json-types type)}}
    (when-let [format (openapi-formats type)]
      {:format (name format)})
    (when (doc/required? schema)
      {:required true})))

; TODO - distinguish between url/query params
(defn ->parameters [schema]
  (reduce-kv
    (fn [coll k v]
      (conj coll (schema->parameter k v)))
    []
    (get-in schema [:params :type])))

(defn- maybe-required-fields [{:keys [type] :as _schema}]
  (when-let [required (seq (doc/required-fields type))]
    {:required required}))

(defn apron->openapi-schema [{:keys [type] :as schema}]
  (cond
    (= :one-of type)
    {:oneOf (mapv apron->openapi-schema (:specs schema))}

    (set? type)
    {:oneOf (mapv #(apron->openapi-schema (if (keyword? %) {:type %} {:type %})) type)}

    (= :seq type)
    {:type  "array"
     :items (apron->openapi-schema (:spec schema))}

    (sequential? type)
    {:type  "array"
     :items (apron->openapi-schema {:type (first type)})}

    (= :map type)
    (let [nested-schema (:schema schema)
          value-spec    (:value-spec schema)]
      (cond-> {:type "object"}
        nested-schema
        (assoc :properties
               (reduce-kv (fn [m k v] (assoc m k (apron->openapi-schema v)))
                          {}
                          nested-schema))

        (seq (doc/required-fields nested-schema))
        (assoc :required (doc/required-fields nested-schema))

        value-spec
        (assoc :additionalProperties (apron->openapi-schema value-spec))))

    (map? type)
    (merge {:type       "object"
            :properties (reduce-kv
                          (fn [m k v] (assoc m k (apron->openapi-schema v)))
                          {}
                          type)}
           (maybe-required-fields schema))

    :else
    (cond-> {:type (apron->json-types type)}
      (openapi-formats type) (assoc :format (name (openapi-formats type))))))

(defn ->request-body [{:keys [body] :as _schema}]
  {:required (or (doc/required? body) (map? (:type body)))
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

(defn ->doc [spec]
  (or (doc/maybe-invalid-doc spec)
      {:openapi "3.0.0"
       :info    {:title   (:title spec)
                 :version (:version spec)}
       :paths   (routes->paths (:routes spec))}))
