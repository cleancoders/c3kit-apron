(ns c3kit.apron.schema.markdown
  "Render apron schemas as human-readable markdown.

   schema->markdown takes a single spec and returns nested-bullet markdown.
   ->doc takes a route/doc spec (same shape that schema.openapi/->doc
   accepts) and produces a full markdown document."
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.doc :as doc]
            [clojure.string :as s]))

(def ^:private type-labels
  {:any       "any"
   :bigdec    "number"
   :boolean   "boolean"
   :date      "date"
   :double    "number"
   :float     "number"
   :ignore    "any"
   :instant   "datetime"
   :int       "integer"
   :keyword   "keyword"
   :kw-ref    "keyword"
   :long      "integer"
   :ref       "integer"
   :string    "string"
   :timestamp "datetime"
   :uri       "uri"
   :uuid      "uuid"})

(defn- type-label [type]
  (get type-labels type (name type)))

(defn- indent [text prefix]
  (->> (s/split-lines text)
       (map #(str prefix %))
       (s/join "\n")))

(defn- annotations [spec]
  (str (when-let [d (:description spec)] (str " — " d))
       (when (contains? spec :example)
         (str " _e.g._ `" (pr-str (:example spec)) "`"))))

(defn- markdown-emit [spec children]
  (case (:type spec)
    :one-of
    {:header (str "one of: " (s/join ", " (map :header (:specs children))))
     :body   ""}

    :seq
    {:header (str "array of " (:header (:spec children)))
     :body   (:body (:spec children))}

    :map
    (let [schema-specs  (:schema spec)
          schema-walked (:schema children)
          required      (set (doc/required-fields schema-specs))
          field-line    (fn [[k child]]
                          (let [child-spec (get schema-specs k)
                                req        (when (contains? required k) ", required")
                                body       (:body child)]
                            (str "- **" (name k) "** (" (:header child) req ")"
                                 (annotations child-spec)
                                 (when (seq body) (str "\n" (indent body "  "))))))
          fields        (some->> schema-walked seq (map field-line) (s/join "\n"))
          extra         (when-let [vspec (:value-spec spec)]
                          (str (when fields "\n")
                               "- _any other key_ (" (:header (:value-spec children)) ")"
                               (annotations vspec)))]
      {:header "object"
       :body   (str fields extra)})

    {:header (type-label (:type spec))
     :body   ""}))

(defn schema->markdown
  "Render a single spec as markdown (nested bullets). For a top-level object,
   the 'object' header is omitted and the field list is returned directly.
   For other composite types (seq, one-of), the header is kept and any
   nested body is indented beneath it."
  [spec]
  (let [{:keys [header body]} (schema/walk-schema markdown-emit spec)]
    (cond
      (empty? body)       header
      (= "object" header) body
      :else               (str header "\n" (indent body "  ")))))

(defn- method->str [method]
  (s/upper-case (name method)))

(defn- render-parameters [request-schema]
  (when-let [params (get-in request-schema [:params :type])]
    (let [lines (for [[k v] params]
                  (str "- **" (name k) "** (" (type-label (:type v))
                       (when (doc/required? v) ", required")
                       ", query)"
                       (annotations v)))]
      (str "### Parameters\n\n" (s/join "\n" lines)))))

(defn- render-body [request-schema]
  (when-let [body (:body request-schema)]
    (str "### Request Body\n\n" (schema->markdown body))))

(defn- render-responses [response-schema]
  (when (seq response-schema)
    (let [sections (for [[code {:keys [schema description]}] response-schema]
                     (str "#### " code
                          (when description (str " — " description))
                          (when schema (str "\n\n" (schema->markdown schema)))))]
      (str "### Responses\n\n" (s/join "\n\n" sections)))))

(defn- render-route [{:keys [path method summary request-schema response-schema]}]
  (let [sections (keep identity
                       [(str "## " (method->str method) " " path)
                        (when summary summary)
                        (render-parameters request-schema)
                        (render-body request-schema)
                        (render-responses response-schema)])]
    (s/join "\n\n" sections)))

(defn ->doc
  "Render a routes-doc spec (same shape as schema.openapi/->doc accepts) as a
   markdown document."
  [spec]
  (or (doc/maybe-invalid-doc spec)
      (let [{:keys [title version routes]} spec
            head (str "# " title "\n\nVersion: " version)]
        (if (seq routes)
          (str head "\n\n" (s/join "\n\n" (map render-route routes)))
          head))))
