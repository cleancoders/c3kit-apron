(ns c3kit.apron.schema
  "Defines data structure, coerces, validates."
  (:refer-clojure :exclude [uri?])
  (:require
    [c3kit.apron.corec :as ccc]
    [clojure.edn :as edn]
    [clojure.string :as str]
    #?(:cljs [com.cognitect.transit.types]) ;; https://github.com/cognitect/transit-cljs/issues/41
    ))

(comment
  "Schema Sample"
  {:field
   {:type        :string ;; see type-validators for list
    :db          [:unique-value] ;; passed to database
    :coerce      [#(str % "y")] ;; single/list of coerce fns
    :validate    [#(> (count %) 1)] ;; single/list of validation fns
    :message     "message describing the field" ;; coerce failure message (or :validate failure message)
    :validations [{:validate fn :message "msg"}] ;; multiple validation/message pairs
    :present     [#(str %)] ;; single/list of presentation fns
    }})

(def stdex
  #?(:clj  clojure.lang.ExceptionInfo
     :cljs cljs.core/ExceptionInfo))

(defn coerce-ex [v type] (ex-info (str "can't convert " (pr-str v) " to " type) {:value v :type type}))
(defn coerce-ex? [e] (and (instance? stdex e) (:coerce? (ex-data e))))

(def date #?(:clj java.util.Date :cljs js/Date))

(defn exmessage [e]
  (when e
    #?(:clj  (.getMessage e)
       :cljs (cljs.core/ex-message e))))

; Common Validations --------------------------------------

(defn present? [v]
  (not (or (nil? v)
           (and (string? v) (str/blank? v)))))

(defn nil-or [f] (some-fn nil? f))

(def email-pattern #"[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")

(defn email? [value] (boolean (re-matches email-pattern value)))

(defn bigdec? [v] #?(:clj (instance? BigDecimal v) :cljs (number? v)))

(defn uri? [value]
  #?(:clj  (instance? java.net.URI value)
     :cljs (string? value)))

(defn is-enum? [enum]
  (let [enum-name (name (:enum enum))
        enum-set  (ccc/map-set #(keyword enum-name (name %)) (:values enum))]
    (fn [value]
      (or (nil? value)
          (contains? enum-set value)))))

; Common Coercions ----------------------------------------

#?(:cljs
   (defn parse! [f v]
     (let [result (f v)]
       (if (js/isNaN result)
         (throw (js/Error "parsed NaN"))
         result))))

(defn ->boolean [value]
  (cond (nil? value) nil
        (boolean? value) value
        (string? value) (not= "false" (str/lower-case value))
        :else (boolean value)))

(defn ->string [value] (some-> value str))
(defn str-or-nil [v] (->string v))

(defn ->keyword [value]
  (cond
    (nil? value) nil
    (keyword? value) value
    :else (let [s (str value)]
            (if (str/starts-with? s ":")
              (keyword (subs s 1))
              (keyword s)))))

(defn ->float [v]
  (cond
    (nil? v) nil
    (string? v) (when-not (str/blank? v)
                  (try
                    #?(:clj (Double/parseDouble v) :cljs (parse! js/parseFloat v))
                    (catch #?(:clj Exception :cljs :default) _
                      (throw (coerce-ex v "float")))))
    #?@(:clj [(char? v) (-> v str ->float)])
    #?@(:cljs [(js/isNaN v) nil])
    (integer? v) (double v)
    (#?(:clj float? :cljs number?) v) v
    (bigdec? v) #?(:clj (.doubleValue v) :cljs v)
    :else (throw (coerce-ex v "float"))))

(defn ->int [v]
  (cond
    (nil? v) nil
    (string? v) (when-not (str/blank? v)
                  (try
                    #?(:clj  (long (Double/parseDouble v))
                       :cljs (parse! js/parseInt v))
                    (catch #?(:clj Exception :cljs :default) _
                      (throw (coerce-ex v "int")))))
    (keyword? v) (throw (coerce-ex v "int"))
    #?@(:clj [(char? v) (-> v str ->int)])
    #?@(:cljs [(js/isNaN v) nil])
    (integer? v) v
    (#?(:clj float? :cljs number?) v) (long v)
    (bigdec? v) #?(:clj (.intValue v) :cljs v)
    :else (throw (coerce-ex v "int"))))

(defn ->bigdec [v]
  (cond
    (nil? v) nil
    (string? v) (when-not (str/blank? v)
                  (try
                    #?(:clj  (bigdec v)
                       :cljs (parse! js/parseFloat v))
                    (catch #?(:clj Exception :cljs :default) _
                      (throw (coerce-ex v "bigdec")))))
    #?@(:clj [(char? v) (-> v str ->bigdec)])
    #?@(:cljs [(js/isNaN v) nil])
    (integer? v) #?(:clj (bigdec v) :cljs (double v))
    (#?(:clj float? :cljs number?) v) #?(:clj (bigdec v) :cljs v)
    #?(:clj (bigdec? v)) #?(:clj v)
    :else (throw (coerce-ex v "bigdec"))))

(defn ->date [v]
  (cond
    (nil? v) nil
    (instance? date v) v
    (integer? v) (doto (new #?(:clj java.util.Date :cljs js/Date)) (.setTime v))
    #?(:cljs (instance? goog.date.Date v)) #?(:cljs (js/Date. (.getTime v)))
    (string? v) (cond
                  (str/blank? v) nil
                  (str/starts-with? v "#inst") (edn/read-string v)
                  :else (throw (coerce-ex v "date")))
    :else (throw (coerce-ex v "date"))))

(defn ->sql-date [v]
  (cond
    (nil? v) nil
    (instance? #?(:clj java.sql.Date :cljs js/Date) v) v
    #?(:clj (instance? java.util.Date v)) #?(:clj (java.sql.Date. (.getTime v)))
    (integer? v) #?(:clj (java.sql.Date. v) :cljs (doto (new js/Date) (.setTime v)))
    #?(:cljs (instance? goog.date.Date v)) #?(:cljs (js/Date. (.getTime v)))
    (string? v) (cond
                  (str/blank? v) nil
                  (str/starts-with? v "#inst") #?(:clj (java.sql.Date. (.getTime (edn/read-string v))) :cljs (edn/read-string v))
                  :else (throw (coerce-ex v "sql-date")))
    :else (throw (coerce-ex v "sql-date"))))

(defn ->timestamp [v]
  (cond
    (nil? v) nil
    (instance? #?(:clj java.sql.Timestamp :cljs js/Date) v) v
    #?(:clj (instance? java.util.Date v)) #?(:clj (java.sql.Timestamp. (.getTime v)))
    (integer? v) #?(:clj (java.sql.Timestamp. v) :cljs (doto (new js/Date) (.setTime v)))
    #?(:cljs (instance? goog.date.Date v)) #?(:cljs (js/Date. (.getTime v)))
    (string? v) (cond
                  (str/blank? v) nil
                  (str/starts-with? v "#inst") #?(:clj (java.sql.Timestamp. (.getTime (edn/read-string v))) :cljs (edn/read-string v))
                  :else (throw (coerce-ex v "timestamp")))
    :else (throw (coerce-ex v "timestamp"))))

(defn ->uri [v]
  (cond
    (nil? v) nil
    #?@(:clj [(instance? java.net.URI v) v])
    (string? v) #?(:clj (java.net.URI/create v) :cljs v)
    :else (throw (coerce-ex v "uri"))))


;; MDM : https://github.com/cognitect/transit-cljs/issues/41
#?(:cljs (extend-type com.cognitect.transit.types/UUID IUUID))

(defn ->uuid [v]
  (cond
    (nil? v) nil
    (uuid? v) v
    (string? v) #?(:clj (java.util.UUID/fromString v) :cljs (uuid v))
    :else (throw (coerce-ex v "uuid"))))

; Type Tables ---------------------------------------------

(def type-validators
  {:bigdec    (nil-or bigdec?)
   :boolean   (nil-or boolean?)
   :double    (nil-or #?(:clj float? :cljs number?))
   :float     (nil-or #?(:clj float? :cljs number?))
   :instant   (nil-or #(instance? date %))
   :date      (nil-or #?(:clj #(instance? java.sql.Date %) :cljs #(instance? date %)))
   :timestamp (nil-or #?(:clj #(instance? java.sql.Timestamp %) :cljs #(instance? date %)))
   :int       (nil-or integer?)
   :keyword   (nil-or keyword?)
   :kw-ref    (nil-or keyword?)
   :long      (nil-or integer?)
   :ref       (nil-or integer?)
   :string    (nil-or string?)
   :uri       (nil-or uri?)
   :uuid      (nil-or uuid?)
   :ignore    (constantly true)
   :schema    (constantly true)})

(def type-coercers
  {:bigdec    ->bigdec
   :boolean   ->boolean
   :double    ->float
   :float     ->float
   :instant   ->date
   :date      ->sql-date
   :timestamp ->timestamp
   :int       ->int
   :keyword   ->keyword
   :kw-ref    ->keyword
   :long      ->int
   :ref       ->int
   :string    ->string
   :uri       ->uri
   :uuid      ->uuid
   :ignore    identity
   :schema    identity})


; Common Schema Attributes --------------------------------

(def omit
  "Used as a :present value to remove the entry from presentation"
  (constantly nil))

(defn kind [key]
  {:type     :keyword
   :value    key
   :validate [#(or (nil? %) (= key %))]
   :coerce   [#(or % key)]
   :message  (str "mismatch; must be " key)})

(def id {:type :ref})

; Processing ---------------------------------------------

(defn- multiple? [thing]
  (or (sequential? thing)
      (set? thing)))

(defn- ->vec [v]
  (cond
    (nil? v) []
    (multiple? v) (vec v)
    :else [v]))

(defn ->seq [v]
  (cond
    (nil? v) []
    (multiple? v) v
    :else (list v)))

(defn type-coercer! [type]
  (or (get type-coercers type)
      (throw (ex-info (str "unhandled coercion type: " (pr-str type)) {:coerce? true}))))

(defn type-validator! [type]
  (or (get type-validators type)
      (throw (ex-info (str "unhandled validation type: " (pr-str type)) {}))))

(defn- <-type [type]
  (let [?seq   (multiple? type)
        type   (if ?seq (first type) type)
        schema (when (map? type) type)
        type   (if schema :schema type)]
    [?seq type schema]))

(defn -coerce-value! [coerce-fn value ?seq]
  (if ?seq
    (when (some? value) (mapv coerce-fn (->seq value)))
    (coerce-fn value)))

(declare coerce!)
(defn- -coerce-object! [schema value ?seq]
  (if ?seq
    (mapv (partial coerce! schema) value)
    (coerce! schema value)))

(defn- do-coercion [{:keys [type] :as spec} value]
  (let [[?seq type schema] (<-type type)
        value (reduce #(-coerce-value! %2 %1 ?seq) value (->vec (:coerce spec)))
        value (if (and schema value)
                (-coerce-object! schema value ?seq)
                value)]
    (-coerce-value! (type-coercer! type) value ?seq)))

(defn- validation-ex [message value] (ex-info "invalid" {:invalid? true :message (or message "is invalid") :value value}))
(defn- validation-ex? [e] (and (instance? stdex e)
                               (:invalid? (ex-data e))))

(defn -validate-seq! [valid-fn message vals]
  (let [vals   (map-indexed vector vals)
        errors (reduce (fn [results [idx val]]
                         (if (valid-fn val)
                           results
                           (assoc results idx message))) {} vals)]
    (when (not-empty errors)
      (throw (validation-ex errors vals)))))

(defn- -validate-value! [valid? message value ?seq]
  (if (and ?seq (some? value))
    (-validate-seq! valid? message value)
    (when-not (valid? value) (throw (validation-ex message value)))))

(defn- -validate*?-value! [validate-fn message value ?seq]
  (if (multiple? validate-fn)
    (doseq [v-fn validate-fn] (-validate-value! v-fn message value ?seq))
    (-validate-value! validate-fn message value ?seq)))

(declare validate!)
(declare validate)
(declare error-message-map)
(defn- -errors-idx [schema entities]
  (let [entities (map-indexed vector entities)]
    (reduce (fn [results [idx entity]]
              (if-let [error (error-message-map (validate schema entity))]
                (assoc results idx error)
                results)) {} entities)))

(defn- -validate-object! [schema value ?seq]
  (if ?seq
    (let [errors (-errors-idx schema value)]
      (when (not-empty errors)
        (throw (validation-ex errors value))))
    (validate! schema value)))

(defn- do-validation [{:keys [type] :as spec} value]
  (let [[?seq type schema] (<-type type)
        {:keys [message validations]} spec]
    (when (and ?seq (not (multiple? value)) value) (throw (validation-ex (str "[" type "] expected") value)))
    (-validate-value! (type-validator! type) message value ?seq)
    (when (and schema (some? value)) (-validate-object! schema value ?seq))
    (some-> spec :validate (-validate*?-value! message value ?seq))
    (doseq [{:keys [validate message]} validations]
      (-validate*?-value! validate message value ?seq))))

; Error Handling ------------------------------------------

(defrecord SchemaError [errors schema before after]
  Object
  (toString [_] (str "SchemaError: " errors)))

(defn make-error [errors schema before after]
  (SchemaError. errors schema before after))

(defn error? [result]
  (or (instance? SchemaError result)
      (and (map? result)
           (contains? result :errors)
           (contains? result :schema)
           (contains? result :before)
           (contains? result :after))))

(defn without-ex
  "replace exceptions with ex-data"
  [result]
  (update result :errors #(reduce (fn [r [k ex]]
                                    (assoc r k (dissoc (ex-data ex) :schema))) {} %)))

(defn- extract-msg [m k err]
  (let [data (ex-data err)]
    (if (error? data)
      (assoc m k (reduce-kv extract-msg {} (:errors data)))
      (assoc m k (or (:message data) "is invalid")))))

(defn error-message-map
  "Nil when there are no errors, otherwise a map {<field> <message>} ."
  ([result]
   (when (error? result)
     (when-let [errors (seq (:errors result))]
       (apply merge (map (fn [[k e]] (extract-msg {} k e)) errors))))))

(defn messages
  "Sequence of error messages in a validate/coerce/conform result; nil if none."
  [result]
  (when-let [errors (error-message-map result)]
    (mapv (fn [[k v]] (str (name k) " " v)) errors)))

; Single Value Actions ------------------------------------

(defn coerce-value
  "returns coerced value or throws an exception"
  ([schema key value] (coerce-value (get schema key) value))
  ([spec value]
   (try
     (do-coercion spec value)
     (catch #?(:clj Exception :cljs :default) e
       (if (coerce-ex? e)
         (throw e)
         (throw (ex-info "coercion failed" {:message (:message spec "coercion failed") :value value} e)))))))

(defn validate-value!
  "throws an exception when validation fails, value otherwise"
  ([schema key value] (validate-value! (get schema key) value))
  ([spec value]
   (do-validation spec value)
   value))

(defn valid-value?
  "return true or false"
  ([schema key value] (valid-value? (get schema key) value))
  ([spec value]
   (try (validate-value! spec value) true (catch #?(:clj Exception :cljs :default) _ false))))

(defn validate-coerced-value!
  "throws an exception when validation fails, value otherwise."
  ([spec value coerced]
   (try
     (validate-value! spec coerced)
     coerced
     (catch
       #?(:clj Exception :cljs :default) e
       (if (validation-ex? e)
         (throw e)
         (throw (ex-info "validation error" {:message (:message spec "is invalid") :value value :coerced coerced} e)))))))

(defn conform-value
  "coerce and validate, returns coerced value or throws"
  ([schema key value] (conform-value (get schema key) value))
  ([spec value]
   (let [coerced (coerce-value spec value)]
     (validate-coerced-value! spec value coerced))))

(declare present!)
(defn present-value
  "returns a presentable representation of the value"
  ([schema key value] (present-value (get schema key) value))
  ([{:keys [type] :as spec} value]
   (let [[?seq _type schema] (<-type type)
         presenters   (->vec (:present spec))
         presenter-fn (cond-> (fn [v] (reduce #(%2 %1) v presenters))
                              schema
                              (comp (partial present! schema)))]
     (if ?seq
       (when (some? value) (vec (ccc/map-some presenter-fn value)))
       (presenter-fn value)))))

; Entity Actions ------------------------------------------

(defn result-or-ex [f spec value]
  (try
    (f spec value)
    (catch #?(:clj Exception :cljs :default) e e)))

(defn- error-or-result [errors schema entity result]
  (if (seq errors)
    (SchemaError. errors schema entity result)
    result))

(defn- process-fields [processor schema entity]
  (loop [errors {} result {} specs schema]
    (if (seq specs)
      (let [[key spec] (first specs)
            value        (get entity key)
            field-result (result-or-ex processor spec value)]
        (if (ccc/ex? field-result)
          (recur (assoc errors key field-result) result (rest specs))
          (let [result (cond-> result (some? field-result) (assoc key field-result))]
            (recur errors result (rest specs)))))
      (error-or-result errors schema entity result))))

(defn- coerce-whole-entity [result schema entity]
  (loop [errors {} result result specs (filter (comp :coerce second) (:* schema))]
    (if (seq specs)
      (let [[key spec] (first specs)
            value (result-or-ex coerce-value spec result)]
        (if (ccc/ex? value)
          (recur (assoc errors key value) result (rest specs))
          (recur errors (assoc result key value) (rest specs))))
      (error-or-result errors schema entity result))))

(defn- validate-whole-entity [result schema entity]
  (let [specs (filter (comp (some-fn :validate :validations) second) (:* schema))]
    (loop [errors {} result result specs specs]
      (if (seq specs)
        (let [[key spec] (first specs)
              value (result-or-ex (fn [spec value]
                                    (try
                                      (validate-value! spec value)
                                      (get result key)
                                      (catch #?(:clj Exception :cljs :default) ex ex)))
                                  (assoc spec :type :ignore) result)]
          (if (ccc/ex? value)
            (recur (assoc errors key value) result (rest specs))
            (recur errors (assoc result key value) (rest specs))))
        (error-or-result errors schema entity result)))))

(defn- present-whole-entity [result schema entity]
  (loop [errors {} result result specs (filter (comp :present second) (:* schema))]
    (if (seq specs)
      (let [[key spec] (first specs)
            value (result-or-ex present-value spec result)]
        (if (ccc/ex? value)
          (recur (assoc errors key value) result (rest specs))
          (recur errors (assoc result key value) (rest specs))))
      (error-or-result errors schema entity result))))

(defn coerce
  "Returns coerced entity or SchemaError if any coercion failed. Use error? to check result.
  Use Case: 'I want to change my data into the types specified by the schema.'"
  [schema entity]
  (let [result (process-fields coerce-value (dissoc schema :*) entity)]
    (if (error? result)
      result
      (coerce-whole-entity result schema entity))))

(defn validate
  "Returns entity with all values true, or SchemaError when one or more invalid fields. Use error? to check result.
  Use Case: 'I want to make sure all the data is valid according to the schema.'"
  [schema entity]
  (let [result (process-fields validate-value! (dissoc schema :*) entity)]
    (if (error? result)
      result
      (validate-whole-entity result schema entity))))

(defn conform
  "Returns coerced entity or SchemaError upon any coercion or validation failure. Use error? to check result.
  Use Case: 'I want to coerce my data then validate the coerced data, all according to the schema.'
  Use Case: Data comes in from a web-form so strings have to be coerced into numbers, etc., then
            we need to validate that the data is good."
  [schema entity]
  (let [result (process-fields conform-value (dissoc schema :*) entity)]
    (if (error? result)
      result
      (let [coerced (coerce-whole-entity result schema entity)]
        (if (error? result)
          result
          (validate-whole-entity coerced schema entity))))))

(defn present
  "Returns presentable entity or SchemaError upon any presentation failure. Use error? to check result."
  [schema entity]
  (let [result (process-fields present-value schema entity)]
    (if (error? result)
      result
      (let [result (present-whole-entity result schema entity)]
        (if (error? result)
          result
          (ccc/remove-nils result))))))

;(defn coercion-errors [schema entity]
;  (messages (coerce schema entity)))

(defn validation-errors [schema entity]
  (error-message-map (validate schema entity)))

(defn conform-errors [schema entity]
  (error-message-map (conform schema entity)))

(defn validate! [schema entity]
  (let [result (validate schema entity)]
    (if (error? result)
      (throw (ex-info "Invalid entity" result))
      result)))

(defn coerce! [schema entity]
  (let [result (coerce schema entity)]
    (if (error? result)
      (throw (ex-info "Uncoercable entity" result))
      result)))

(defn conform! [schema entity]
  (let [result (conform schema entity)]
    (if (error? result)
      (throw (ex-info "Unconformable entity" result))
      result)))

(defn conform-all! [schema entities]
  (let [conforms (map #(conform schema %) entities)
        errors   (filter error? conforms)]
    (if (seq errors)
      (throw (ex-info "Unconformable entities" (make-error (apply merge (map #(get % :errors) errors)) schema entities conforms)))
      conforms)))

(defn present! [schema entity]
  (let [result (present schema entity)]
    (if (error? result)
      (throw (ex-info "Unpresentable entity" result))
      result)))

(defn- validate->validations [{:keys [validate message] :as spec}]
  (if validate
    (-> (dissoc spec :validate)
        (update :validations ccc/conjv {:validate validate :message message}))
    spec))

(defn merge-specs [a b]
  (let [a (validate->validations a)
        b (validate->validations b)]
    (if-let [validations (seq (concat (:validations a []) (:validations b [])))]
      (assoc (merge a b) :validations (vec validations))
      (merge a b))))

(defn merge-schemas [& schemas]
  (let [entity-specs (apply merge-with merge-specs (map :* schemas))
        attr-specs   (apply merge-with merge-specs (map #(dissoc % :*) schemas))]
    (if (seq entity-specs)
      (assoc attr-specs :* entity-specs)
      attr-specs)))
