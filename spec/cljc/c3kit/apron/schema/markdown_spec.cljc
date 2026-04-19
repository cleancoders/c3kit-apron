(ns c3kit.apron.schema.markdown-spec
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.markdown :as sut]
            [speclj.core #?(:clj :refer :cljs :refer-macros)
             [describe it should= should-throw context should-contain should-not-contain]]))

(def exception #?(:clj clojure.lang.ExceptionInfo :cljs js/Error))

(describe "Markdown rendering"

  (context "schema->markdown"

    (it "leaf: bare type"
      (should= "string" (sut/spec->markdown {:type :string})))

    (it "leaf: with description and example"
      (should-contain "string" (sut/spec->markdown {:type :string :description "hi"}))
      (should-contain "int" (sut/spec->markdown {:type :int :example 42})))

    (it "map with known fields"
      (let [md (sut/spec->markdown
                 {:type :map :schema {:name {:type :string}
                                      :age  {:type :int}}})]
        (should-contain "- **name** (string)" md)
        (should-contain "- **age** (int)" md)))

    (it "map marks required fields"
      (let [md (sut/spec->markdown
                 {:type :map :schema {:name {:type :string :validate schema/present?}}})]
        (should-contain "- **name** (string, required)" md)))

    (it "map shows description and example"
      (let [md (sut/spec->markdown
                 {:type :map :schema {:name {:type :string
                                              :description "user name"
                                              :example "alice"}}})]
        (should-contain "— user name" md)
        (should-contain "_e.g._ `\"alice\"`" md)))

    (it "nested map indents"
      (let [md (sut/spec->markdown
                 {:type :map :schema {:parent {:type :map :schema {:name {:type :string}}}}})]
        (should-contain "- **parent** (map)" md)
        (should-contain "  - **name** (string)" md)))

    (it "seq of primitive"
      (should= "seq of int"
               (sut/spec->markdown {:type :seq :spec {:type :int}})))

    (it "seq of map renders 'seq of map' with indented fields"
      (let [md (sut/spec->markdown
                 {:type :seq :spec {:type :map :schema {:name {:type :string}}}})]
        (should-contain "seq of map" md)
        (should-contain "  - **name** (string)" md)))

    (it "one-of"
      (should= "one of: int, string"
               (sut/spec->markdown {:type :one-of :specs [{:type :int} {:type :string}]})))

    (it "dynamic keys render with 'any other key'"
      (let [md (sut/spec->markdown
                 {:type :map
                  :schema {:captain {:type :string}}
                  :value-spec {:type :string :description "crew member name"}})]
        (should-contain "- **captain** (string)" md)
        (should-contain "- _any other key_ (string)" md)
        (should-contain "crew member name" md)))

    )

  (context "schema->markdown-table"

    (it "renders a map as a table"
      (let [md (sut/spec->markdown-table
                 {:type :map :schema {:name {:type :string :validate schema/present?}
                                      :age  {:type :int}}})]
        (should-contain "| Field | Type | Required | Description | Example |" md)
        (should-contain "| name | string | yes |" md)
        (should-contain "| age | int |  |" md)))

    (it "references named schemas with a link"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            md  (sut/spec->markdown-table
                  {:type :map :schema {:pet pet}})]
        (should-contain "map (see [pet](#pet))" md)
        (should-contain "## pet" md)))

    (it "dedups a named schema reached multiple ways"
      (let [pet {:type :map :name :pet :schema {:name {:type :string}}}
            md  (sut/spec->markdown-table
                  {:type :map :schema {:a pet :b pet}})
            pet-sections (re-seq #"(?m)^## pet" md)]
        (should= 1 (count pet-sections))))

    (it "named root skips the anonymous 'Schema' section"
      (let [md (sut/spec->markdown-table
                 {:type :map :name :user :schema {:name {:type :string}}})]
        (should-contain "## user" md)
        (should-not-contain "## Schema" md)))

    )

  (context "->doc"

    (it "requires title and version"
      (should-throw exception (sut/->doc {})))

    (it "minimal doc with no routes"
      (let [md (sut/->doc {:title "Silmarillion" :version "1.0.0"})]
        (should-contain "# Silmarillion" md)
        (should-contain "Version: 1.0.0" md)))

    (it "renders a route heading"
      (let [md (sut/->doc {:title   "API"
                           :version "1.0.0"
                           :routes  [{:path "/resource" :method :get :summary "Fetch it"}]})]
        (should-contain "## GET /resource" md)
        (should-contain "Fetch it" md)))

    (it "renders request body as nested fields"
      (let [md (sut/->doc {:title   "API"
                           :version "1.0.0"
                           :routes  [{:path           "/resource"
                                      :method         :post
                                      :request-schema {:body {:type :map :schema {:name {:type :string}}}}}]})]
        (should-contain "### Request Body" md)
        (should-contain "- **name** (string)" md)))

    (it "renders responses with status code and description"
      (let [md (sut/->doc {:title   "API"
                           :version "1.0.0"
                           :routes  [{:path            "/resource"
                                      :method          :get
                                      :response-schema {200 {:schema      {:type :map :schema {:name {:type :string}}}
                                                             :description "OK"}}}]})]
        (should-contain "### Responses" md)
        (should-contain "#### 200" md)
        (should-contain "— OK" md)
        (should-contain "- **name** (string)" md)))

    )

  )
