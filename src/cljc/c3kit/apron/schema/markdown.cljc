(ns c3kit.apron.schema.markdown
  "Render apron schemas as human-readable markdown.

   schema->markdown takes a single spec and returns nested-bullet markdown.
   ->doc takes a route/doc spec (same shape that schema.openapi/->doc
   accepts) and produces a full markdown document."
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.doc :as doc]
            [clojure.string :as s]))

(defn- type-label [type]
  (name type))

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
    {:header (str "seq of " (:header (:spec children)))
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
      {:header "map"
       :body   (str fields extra)})

    {:header (type-label (:type spec))
     :body   ""}))

(defn spec->markdown
  "Render a single spec as markdown (nested bullets). For a top-level map,
   the 'map' header is omitted and the field list is returned directly.
   For other composite types (seq, one-of), the header is kept and any
   nested body is indented beneath it."
  [spec]
  (let [{:keys [header body]} (schema/walk-schema markdown-emit spec)]
    (cond
      (empty? body)       header
      (= "map" header)    body
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
    (str "### Request Body\n\n" (spec->markdown body))))

(defn- render-responses [response-schema]
  (when (seq response-schema)
    (let [sections (for [[code {:keys [schema description]}] response-schema]
                     (str "#### " code
                          (when description (str " — " description))
                          (when schema (str "\n\n" (spec->markdown schema)))))]
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

;; region ----- Tables -----

(defn- cell [x]
  (-> (str x)
      (s/replace "|" "\\|")
      (s/replace "\n" " ")))

(defn- anchor [nm]
  (-> (clojure.core/name nm)
      (s/replace #"[^a-zA-Z0-9]+" "-")
      s/lower-case))

(defn- ref-link [nm]
  (str "[" (clojure.core/name nm) "](#" (anchor nm) ")"))

(defn- type-phrase [spec]
  (let [spec (schema/normalize-spec spec)]
    (cond
      (:name spec)
      (let [base (case (:type spec)
                   :map "map"
                   :seq (str "seq of " (type-phrase (:spec spec)))
                   (type-label (:type spec)))]
        (str base " (see " (ref-link (:name spec)) ")"))
      :else
      (case (:type spec)
        :map    "map"
        :seq    (str "seq of " (type-phrase (:spec spec)))
        :one-of (str "one of: " (s/join ", " (map type-phrase (:specs spec))))
        (type-label (:type spec))))))

(defn- map-schema [spec]
  (when (= :map (:type spec))
    (some-> (:schema spec) (dissoc :*))))

(defn- field-row [required [k raw-spec]]
  (let [spec (schema/normalize-spec raw-spec)]
    (str "| " (name k)
         " | " (cell (type-phrase spec))
         " | " (if (contains? required k) "yes" "")
         " | " (cell (or (:description spec) ""))
         " | " (cell (if (contains? spec :example) (pr-str (:example spec)) ""))
         " |")))

(defn- object-table [schema-map]
  (let [required (set (doc/required-fields schema-map))
        entries  (sort-by (comp name key) schema-map)]
    (s/join "\n"
            (concat ["| Field | Type | Required | Description | Example |"
                     "|---|---|---|---|---|"]
                    (map #(field-row required %) entries)))))

(defn- collect-named [spec acc]
  (let [spec (schema/normalize-spec spec)
        acc  (if (:name spec) (assoc acc (name (:name spec)) spec) acc)
        kids (case (:type spec)
               :map    (vals (dissoc (:schema spec) :*))
               :seq    [(:spec spec)]
               :one-of (:specs spec)
               [])]
    (reduce (fn [acc k] (collect-named k acc)) acc kids)))

(defn- anon-sections [path spec]
  "Yield sections for the anonymous nested maps reachable from spec, stopping
   at named sub-specs (they are rendered separately)."
  (let [spec (schema/normalize-spec spec)]
    (cond
      (:name spec)           []            ; named; skip
      (= :seq (:type spec))  (anon-sections path (:spec spec))
      :else
      (when-let [sm (map-schema spec)]
        (let [body    (object-table sm)
              entries (sort-by (comp name key) sm)
              subs    (mapcat (fn [[k v]] (anon-sections (conj path k) v)) entries)]
          (cons {:depth (count path) :title (s/join "." (map name path)) :body body}
                subs))))))

(defn- named-section [[nm spec]]
  (let [sm (map-schema spec)]
    {:depth 1
     :title nm
     :body  (if sm
              (let [entries (sort-by (comp name key) sm)
                    subs    (mapcat (fn [[k v]] (anon-sections [k] v)) entries)]
                (s/join "\n\n"
                        (cons (object-table sm)
                              (map (fn [{:keys [depth title body]}]
                                     (str (apply str (repeat (min (+ 2 depth) 6) "#"))
                                          " " nm "." title "\n\n" body))
                                   subs))))
              (type-phrase spec))}))

(defn- render-section [{:keys [depth title body]}]
  (let [hashes (apply str (repeat (min (+ 1 depth) 6) "#"))]
    (str hashes " " title "\n\n" body)))

(defn spec->markdown-table
  "Render a spec as Markdown tables — one per object.

   Nested anonymous objects get their own sub-sections titled by path.
   Specs tagged with a :name are rendered once in a top-level Schemas section
   and linked from use sites via Markdown anchors.

   Falls back to spec->markdown for non-map top-level specs."
  [spec]
  (let [spec  (schema/normalize-spec spec)
        named (collect-named spec {})]
    (if-let [sm (map-schema spec)]
      (let [sm-sorted  (sort-by (comp name key) sm)
            root       (when-not (:name spec)
                         {:depth 1 :title "Schema" :body (object-table sm)})
            anon       (when-not (:name spec)
                         (mapcat (fn [[k v]] (anon-sections [k] v)) sm-sorted))
            named-secs (map named-section (sort-by key named))
            all        (concat (when root [root])
                               (map #(update % :depth inc) anon)
                               named-secs)]
        (s/join "\n\n" (map render-section all)))
      (spec->markdown spec))))

;; endregion
