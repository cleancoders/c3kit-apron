(ns c3kit.apron.log
  "Hand-rolled cross-platform logging (no Timbre/SLF4J).

  Level macros: `trace`, `debug`, `info`, `warn`, `error`, `fatal`, `report`.
  Level controls: `off!`, `fatal!`, `error!`, `warn!`, `info!`, `debug!`, `all!`,
  `level`, `set-level!`, `with-level`.

  Test helpers: `capture-logs` (macro), `parse-captured-logs`, `captured-logs-str`.
  Raw `@captured-logs` holds maps `{:level :message}` — not a stable public layout;
  prefer the parse helpers in tests.

  Levels (ascending severity): :trace < :debug < :info < :warn < :error < :fatal < :report.
  A call is emitted when its level is ≥ the configured minimum (default :info).
  When a call is suppressed, its arguments are not evaluated (macros wrap args in `when`).

  Implementation dynamics (`*min-level-override*`, `*capturing?*`) and `-emit!` are not part of
  the supported API — use the macros and level helpers."
  (:refer-clojure :exclude [time])
  #?(:cljs (:require-macros [c3kit.apron.log :refer [trace debug info warn error fatal report
                                                      capture-logs with-level time]]))
  (:require [c3kit.apron.corec :as ccc]
            [clojure.string :as str]))

(def ^:private level-order
  [:trace :debug :info :warn :error :fatal :report])

(def ^:private level-rank
  (zipmap level-order (range)))

(defonce ^:private state (atom {:min-level :info}))

;; Dynamic overrides — public only because macros/binding require them; treat as internal.
(def ^:dynamic *min-level-override* nil)
(def ^:dynamic *capturing?* false)

(def captured-logs
  "Atom buffer of captured log maps. Prefer parse-captured-logs / captured-logs-str."
  (atom []))

(defn level
  "Current effective minimum log level (dynamic override wins over global atom)."
  []
  (or *min-level-override* (:min-level @state)))

(defn may-log?
  "true when a call at lvl should be emitted under the current min-level.
  Safe for callers to use when guarding expensive work outside log macros."
  [lvl]
  (>= (get level-rank lvl -1)
      (get level-rank (level) 0)))

(defn- format-args [args]
  (str/join " " (map str args)))

(defn- platform-print!
  [lvl message]
  #?(:clj  (println message)
     :cljs (if (exists? js/console)
             (let [log (.-log js/console)
                   f   (case lvl
                         (:trace :debug) (or (.-debug js/console) log)
                         (:info :report) (or (.-info js/console) log)
                         :warn (or (.-warn js/console) log)
                         (:error :fatal) (or (.-error js/console) log)
                         log)]
               (.call f js/console message))
             (println message))))

(defn -emit!
  "Internal emit path. Prefer level macros.
  Defensive may-log? check; macros are responsible for not evaluating args when suppressed."
  [lvl args]
  (when (may-log? lvl)
    (let [message (format-args args)]
      (if *capturing?*
        (swap! captured-logs conj {:level lvl :message message})
        (platform-print! lvl message)))
    nil))

#?(:clj
   (do
     ;; Args sit inside `when` so suppressed levels do not evaluate them (lazy args contract).
     (defmacro trace [& args] `(when (may-log? :trace) (-emit! :trace (list ~@args))))
     (defmacro debug [& args] `(when (may-log? :debug) (-emit! :debug (list ~@args))))
     (defmacro info [& args] `(when (may-log? :info) (-emit! :info (list ~@args))))
     (defmacro warn [& args] `(when (may-log? :warn) (-emit! :warn (list ~@args))))
     (defmacro error [& args] `(when (may-log? :error) (-emit! :error (list ~@args))))
     (defmacro fatal [& args] `(when (may-log? :fatal) (-emit! :fatal (list ~@args))))
     (defmacro report [& args] `(when (may-log? :report) (-emit! :report (list ~@args))))))

(defn- -set-min-level-silent!
  "Set global (atom) min-level without emitting a report line."
  [new-level]
  (swap! state assoc :min-level new-level)
  new-level)

(defn set-level!
  "Set global min-level. When the *effective* level changes, report \"Setting log level: …\".
  Note: under `with-level`, the dynamic override still wins until the binding exits;
  bang helpers update the global atom, which takes effect after `with-level`."
  [new-level]
  (when-not (= (level) new-level)
    (report (str "Setting log level: " new-level))
    (-set-min-level-silent! new-level)))

(defn off! [] (set-level! :report))
(defn fatal! [] (set-level! :fatal))
(defn error! [] (set-level! :error))
(defn warn! [] (set-level! :warn))
(defn info! [] (set-level! :info))
(defn debug! [] (set-level! :debug))
(defn all! [] (set-level! :trace))

#?(:clj
   (defmacro with-level
     "Run body with a temporary min-level override (dynamic)."
     [level & body]
     `(binding [*min-level-override* ~level]
        ~@body)))

(defn capture-logs*
  "Runtime implementation of capture-logs. Restores the global atom min-level
  (not the effective level), so nesting under with-level does not pollute the atom."
  [f]
  (let [prev-global (:min-level @state)]
    (reset! captured-logs [])
    (try
      (-set-min-level-silent! :trace)
      (binding [*capturing?* true
                *min-level-override* nil]
        (f))
      (finally
        (-set-min-level-silent! prev-global)))))

#?(:clj
   (defmacro capture-logs
     "Clear capture buffer, force min-level :trace for body, capture without printing.
     Restores previous *global* min-level afterward (not a dynamic with-level override)."
     [& body]
     `(capture-logs* (fn [] ~@body))))

(defn parse-captured-logs
  "Return captured entries as ({:level … :message …} …)."
  []
  (mapv #(select-keys % [:level :message]) @captured-logs))

(defn captured-logs-str
  "Newline-joined message strings from the capture buffer."
  []
  (str/join "\n" (map :message @captured-logs)))

(defn test-levels [msg]
  (report msg)
  (fatal msg)
  (error msg)
  (warn msg)
  (info msg)
  (debug msg)
  (trace msg))

(defn table-spec [& cols]
  (let [width      (+ (apply + (map second cols)) (count cols))
        format-str (str/join " " (map #(str "%-" (second %) "s") cols))]
    {:cols     cols
     :format   format-str
     :width    width
     :title-fn (fn [title]
                 (let [pad (/ (- width (.length title)) 2)]
                   (str (str/join "" (take pad (repeat " "))) title "\n")))
     :header   (str (apply (partial ccc/formats format-str) (map first cols)) "\n"
                    (str/join "" (take width (repeat "-"))) "\n")}))

(defn color-pr
  "For ANSI color codes: https://en.wikipedia.org/wiki/ANSI_escape_code"
  [message color]
  (println (str "\u001b[" color "m" message "\u001b[0m")))

(defn -platform-time []
  #?(:clj (. System (nanoTime)) :cljs (.getTime (js/Date.))))

(defn -nanos []
  #?(:clj (. System (nanoTime)) :cljs (* (.now js/performance) 1000.0)))

#?(:clj
   (defmacro time
     "Same as clojure.core/time but logs (info) instead of printing elapsed time."
     [expr]
     `(let [start#  (-nanos)
            ret#    ~expr
            millis# (/ (double (- (-nanos) start#)) 1000000.0)]
        (info (str "Elapsed time: " millis# " msecs"))
        ret#)))
