(ns c3kit.apron.doc-spec
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.doc :as sut]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [focus-it describe it should= should-throw context should-contain should]]))

(def exception #?(:clj clojure.lang.ExceptionInfo :cljs js/Error))

(describe "Automated docs generation"

  (it "requires title and version"
    (should-throw exception "title is required; version is required"
      (sut/->doc {})))

  (it "minimal doc with title and version"
    (should= {:openapi "3.0.0"
              :info    {:title   "Silmarillion"
                        :version "1.0.0"}
              :paths   {}}
      (sut/->doc {:title "Silmarillion" :version "1.0.0"})))

  (context "doc routes"
    (context "throws"
      (it "non-seq routes"
        (should-throw exception
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes {}})))

      (it "route with no path"
        (should-throw exception "routes.0.path is a required string"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:method :get}]})))

      (it "route with no method"
        (should-throw exception "routes.0.method is a required keyword"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"}]})))

      (it "route with non-map request-schema"
        (should-throw exception "routes.0.request-schema must be map"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"
                                :method :get
                                :request-schema []}]})))

      (it "route with non-map request-schema params"
        (should-throw exception "routes.0.request-schema.params must be map"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"
                                :method :get
                                :request-schema {:params []}}]})))

      (it "route with non-map request-schema body"
        (should-throw exception "routes.0.request-schema.body must be map"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"
                                :method :get
                                :request-schema {:body []}}]})))

      (it "route with non-map response-schema"
        (should-throw exception "routes.0.response-schema must be map"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"
                                :method :get
                                :response-schema []}]})))

      (it "route with non-integer response-schema key"
        (should-throw exception "routes.0.response-schema keys must be response codes (integers)"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"
                                :method :get
                                :response-schema {"200" {:schema {:type :int}
                                                         :description "Hello!"}}}]})))

      (it "route with non-map response-schema :schema value"
        (should-throw exception "routes.0.response-schema :schema must be map"
          (sut/->doc {:title "Silmarillion"
                      :version "1.0.0"
                      :routes [{:path "/my-resource"
                                :method :get
                                :response-schema {200 {:schema []}}}]}))))

    (it "minimal valid route"
      (let [routes [{:path "/my-resource" :summary "Fetch my resource" :method :get}]]
        (should= {:openapi "3.0.0"
                  :info    {:title   "I Love Schemas"
                            :version "1.2.3"}
                  :paths   (sut/routes->paths routes)}
          (sut/->doc {:title "I Love Schemas" :version "1.2.3" :routes routes}))))

    (it "route with request-schema"
      (let [routes [{:path           "/my-resource"
                     :summary        "Fetch my resource"
                     :method         :get
                     :request-schema {:body {:type :int}}}]]
        (should= {:openapi "3.0.0"
                  :info    {:title   "Silmarillion"
                            :version "1.0.0"}
                  :paths   (sut/routes->paths routes)}
          (sut/->doc {:title "Silmarillion" :version "1.0.0" :routes routes}))))

    (it "route with response-schema"
      (let [routes [{:path            "/my-resource"
                     :summary         "Fetch my resource"
                     :method          :get
                     :response-schema {200 {:schema {:type {:req-1 {:type :int}}} :description "Hello!"}}}]]
        (should= {:openapi "3.0.0"
                  :info    {:title   "Silmarillion"
                            :version "1.0.0"}
                  :paths   (sut/routes->paths routes)}
          (sut/->doc {:title "Silmarillion" :version "1.0.0" :routes routes})))))

  (context "path components"

    (context "routes->paths"

      (it "converts a route to a path"
        (should= {"/my-resource" {:get {:summary "Fetch my resource"}}}
          (sut/routes->paths [{:path    "/my-resource"
                               :summary "Fetch my resource"
                               :method  :get}]))
        (should= {"/another-resource" {:post {:summary "Post another resource"}}}
          (sut/routes->paths [{:path    "/another-resource"
                               :summary "Post another resource"
                               :method  :post}])))

      (context "convert multiple routes"

        (it "no shared paths"
          (should= {"/my-resource" {:get {:summary "Fetch my resource"}}
                    "/another-resource" {:post {:summary "Post another resource"}}}
            (sut/routes->paths [{:path    "/my-resource"
                                 :summary "Fetch my resource"
                                 :method  :get}
                                {:path    "/another-resource"
                                 :summary "Post another resource"
                                 :method  :post}])))

        (it "some shared paths"
          (should= {"/my-resource" {:get {:summary "Fetch my resource"}
                                    :post {:summary "Post my resource"}}}
            (sut/routes->paths [{:path    "/my-resource"
                                 :summary "Fetch my resource"
                                 :method  :get}
                                {:path    "/my-resource"
                                 :summary "Post my resource"
                                 :method  :post}]))))

      (it "includes request-schema parameters"
        (let [request-schema {:params {:type {:req-1 {:type :int}}}}]
          (should= {"/my-resource" {:get {:summary "Fetch my resource"
                                          :parameters (sut/->parameters request-schema)}}}
            (sut/routes->paths
              [{:path           "/my-resource"
                :summary        "Fetch my resource"
                :method         :get
                :request-schema request-schema}]))))

      (it "includes request-schema body"
        (let [request-schema {:body {:type {:req-1 {:type :int}}}}]
          (should= {"/my-resource" {:get {:summary "Fetch my resource"
                                          :requestBody (sut/->request-body request-schema)}}}
            (sut/routes->paths
              [{:path           "/my-resource"
                :summary        "Fetch my resource"
                :method         :get
                :request-schema request-schema}]))))

      (it "includes response-schema body"
        (let [response-schema {200 {:schema {:type {:req-1 {:type :int}}} :description "Hello!"}} #_{200 {:req-1 {:type :int}}}]
          (should= {"/my-resource" {:get {:summary "Fetch my resource"
                                          :responses (sut/->responses response-schema)}}}
            (sut/routes->paths
              [{:path            "/my-resource"
                :summary         "Fetch my resource"
                :method          :get
                :response-schema response-schema}])))))

    (context "query parameters"

      (it "basic type specifications"
        (let [spec       {:params
                          {:type
                           {:any-field       {:type :any}
                            :bigdec-field    {:type :bigdec}
                            :boolean-field   {:type :boolean}
                            :date-field      {:type :date}
                            :double-field    {:type :double}
                            :float-field     {:type :float}
                            :ignore-field    {:type :ignore}
                            :instant-field   {:type :instant}
                            :int-field       {:type :int}
                            :keyword-field   {:type :keyword}
                            :kw-ref-field    {:type :kw}
                            :long-field      {:type :long}
                            :one-of-field    {:type :one}
                            :map-field       {:type :map}
                            :ref-field       {:type :ref}
                            :seq-field       {:type :seq}
                            :string-field    {:type :string}
                            :timestamp-field {:type :timestamp}
                            :uri-field       {:type :uri}
                            :uuid-field      {:type :uuid}
                            }}}
              parameters (sut/->parameters spec)]
          (letfn [(should-map-param-type
                    ([field-name type]
                     (should-map-param-type field-name type nil))
                    ([field-name type opts]
                     (should-contain
                       (merge {:name   field-name
                               :in     "query"
                               :schema {:type type}}
                              opts)
                       parameters)))]
            (should-map-param-type "any-field" "string")
            (should-map-param-type "bigdec-field" "string")
            (should-map-param-type "boolean-field" "boolean")
            (should-map-param-type "date-field" "string" {:format "date"})
            (should-map-param-type "double-field" "number" {:format "double"})
            (should-map-param-type "float-field" "number" {:format "float"})
            (should-map-param-type "ignore-field" "string")
            (should-map-param-type "instant-field" "string")
            (should-map-param-type "int-field" "integer")
            (should-map-param-type "keyword-field" "string")
            ;(should-map-param-type "kw-ref-field" "string") ; TODO
            (should-map-param-type "long-field" "number" {:format "int64"})
            ;(should-map-param-type "one-of-field" "") ; TODO
            ;(should-map-param-type "map-field" ""); TODO
            (should-map-param-type "ref-field" "number" {:format "int64"})
            ;(should-map-param-type "seq-field" ""); TODO
            (should-map-param-type "string-field" "string")
            ;(should-map-param-type "timestamp-field" ""); TODO
            (should-map-param-type "uri-field" "string" {:format "uri"})
            (should-map-param-type "uuid-field" "string" {:format "uuid"}))))

      (it "required field"
        (let [spec {:params
                    {:type
                     {:req-1 {:type :string :validate schema/present?}
                      :req-2 {:type :string :validations [{:validate schema/present?}]}}}}
              [req-1 req-2] (sut/->parameters spec)]
          (should (:required req-1))
          (should (:required req-2))))
      )

    (context "schemas"

      (context "complex types"

        (context "one of"

          (it "of single primitive"
            (let [apron {:type :one-of :specs [{:type :int}]}]
              (should= {:oneOf [{:type "integer"}]}
                (sut/apron->openapi-schema apron))))

          (it "of multiple primitives"
            (let [apron {:type :one-of :specs [{:type :int} {:type :string} {:type :boolean}]}]
              (should= {:oneOf [{:type "integer"} {:type "string"} {:type "boolean"}]}
                (sut/apron->openapi-schema apron))))

          (it "of single object"
            (let [apron {:type :one-of :specs [{:type {:req-1 {:type :int}}}]}]
              (should= {:oneOf [{:type       "object"
                                 :properties {:req-1 {:type "integer"}}}]}
                (sut/apron->openapi-schema apron)))))

        (context "seq"
          (it "of primitives"
            (let [apron {:type :seq :spec {:type :int}}]
              (should= {:type "array" :items {:type "integer"}}
                (sut/apron->openapi-schema apron))))

          (it "of objects"
            (let [apron {:type :seq :spec {:type {:child-1 {:type :int}
                                                  :child-2 {:type :string}}}}]
              (should= {:type "array" :items {:type       "object"
                                              :properties {:child-1 {:type "integer"}
                                                           :child-2 {:type "string"}}}}
                (sut/apron->openapi-schema apron))))))

      (context "requestBody"

        (it "primitive type"
          (should= {:required false
                    :content  {"application/json" {:schema {:type "integer"}}}}
            (sut/->request-body {:body {:type :int}})))

        (it "required primitive"
          (should= {:required true
                    :content  {"application/json" {:schema {:type "integer"}}}}
            (sut/->request-body {:body {:type :int :validate schema/present?}})))

        (it "object"
          (should= {:required true
                    :content  {"application/json" {:schema {:type       "object"
                                                            :required   [:req-1]
                                                            :properties {:req-1 {:type "string"}}}}}}
            (sut/->request-body
              {:body {:type {:req-1 {:type :string :validate schema/present?}}}})))

        (it "array of primitives"
          (should= {:required false
                    :content  {"application/json" {:schema {:type  "array"
                                                            :items {:type "string"}}}}}
            (sut/->request-body
              {:body {:type [:string]}})))

        (it "array of objects"
          (should= {:required false
                    :content  {"application/json" {:schema {:type  "array"
                                                            :items {:type       "object"
                                                                    :properties {:child-1 {:type "integer"}
                                                                                 :child-2 {:type "string"}}}}}}}
            (sut/->request-body
              {:body {:type [{:child-1 {:type :int}
                              :child-2 {:type :string}}]}}))))

      (context "responses"

        (it "none"
          (should= {201 {:description "Created!"}}
            (sut/->responses {201 {:description "Created!"}})))

        (it "primitive type"
          (should= {200 {:description "Hello!"
                         :content     {"application/json" {:schema {:type "integer"}}}}}
            (sut/->responses {200 {:schema {:type :int} :description "Hello!"}})))

        (it "object"
          (should= {201 {:description "Goodbye."
                         :content     {"application/json" {:schema {:type       "object"
                                                                    :properties {:req-1 {:type "string"}}}}}}}
            (sut/->responses
              {201 {:schema {:type {:req-1 {:type :string}}} :description "Goodbye."}})))

        (it "multiple response codes"
          (should= {200 {:description "Hello!"
                         :content     {"application/json" {:schema {:type "integer"}}}}
                    201 {:description "Goodbye."
                         :content     {"application/json" {:schema {:type       "object"
                                                                    :properties {:req-1 {:type "string"}}}}}}}
            (sut/->responses
              {200 {:schema {:type :int} :description "Hello!"}
               201 {:schema {:type {:req-1 {:type :string}}} :description "Goodbye."}}))))))

  )