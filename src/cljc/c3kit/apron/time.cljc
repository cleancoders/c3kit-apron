(ns c3kit.apron.time
  (:require [clojure.math :as math]
            #?@(:cljs ([cljs-time.format :as timef]
                       [cljs-time.coerce :as timec]
                       [cljs-time.core :as time])))
  #?(:clj (:import (java.util Date TimeZone)
                   (java.text SimpleDateFormat)
                   (java.time LocalDateTime Month ZoneId Instant ZonedDateTime Period))))

(defn milliseconds
  "Our atomic unit"
  [n] n)

(defn seconds
  "Converts seconds to milliseconds"
  [n] (math/round (double (* n 1000))))

(defn minutes
  "Converts minutes to milliseconds"
  [n] (math/round (double (* n 60000))))

(defn hours
  "Converts hours to milliseconds"
  [n] (math/round (double (* n 3600000))))

(defn days
  "Converts days to milliseconds"
  [n] [:days (if (float? n) (math/round n) n)])

(defn months
  "Converts a number into a format that the Calendar object understands to be an amount of months"
  [n] [:months (if (float? n) (math/round n) n)])

(defn years
  "Converts a number into a format that the Calendar object understands to be an amount of years"
  [n] [:years (if (float? n) (math/round n) n)])

(defn millis->seconds
  "Converts milliseconds to seconds"
  [millis]
  (long (/ millis 1000)))

(defn now
  "Returns a java.util.Date or js/Date object that represents the current date and time in UTC"
  []
  #?(:clj (Date.) :cljs (js/Date.)))

#?(:clj (defn- ^Instant date->instant [^Date date]
          (-> date .getTime Instant/ofEpochMilli)))

#?(:clj (defn- ^LocalDateTime ->local-date-time [^Date date]
          (LocalDateTime/ofInstant (date->instant date) (ZoneId/systemDefault))))

(defn utc-offset
  "The offset (milliseconds) between the local timezone and UTC. (AZ -> -7hrs)"
  ([] (utc-offset (now)))
  ([date]
   #?(:clj  (-> (ZoneId/systemDefault)
                .getRules
                (.getOffset (date->instant date))
                .getTotalSeconds
                (* 1000))
      :cljs (* -1 (minutes (js-invoke date "getTimezoneOffset"))))))

(defn from-epoch
  "Create Date relative to epoch, adjusted for timezone offset
  (from-epoch 0)"
  [^long millis-since-epoch]
  #?(:clj (Date. millis-since-epoch) :cljs (js/Date. millis-since-epoch)))

(def epoch (from-epoch 0))

(defn instant? [thing] (instance? #?(:clj Date :cljs js/Date) thing))
(defn millis-since-epoch [date] (.getTime date))
(defn seconds-since-epoch [date] (-> date millis-since-epoch millis->seconds))

(defn millis-between
  "Milliseconds that separate the two times.  Negative if b is after a."
  [a b]
  (- (millis-since-epoch a) (millis-since-epoch b)))

(defn ->utc
  "Returns a new date representing time in UTC timezone, assuming given date is in local timezone."
  [^Date date]
  (from-epoch (- (millis-since-epoch date) (utc-offset date))))

(defn ->local
  "Returns a new date representing time in the timezone, assuming given date is in UTC timezone."
  [^Date date]
  (from-epoch (+ (millis-since-epoch date) (utc-offset date))))

(defn local
  "Create a Date assuming parameters are local timezone.
  e.g. in AZ: (local 2020 1 1 0 0 0) -> 2020-01-01T07:00:00.000-00:00"
  ([year month day] (local year month day 0 0 0))
  ([year month day hour minute] (local year month day hour minute 0))
  ([year month day hour minute second]
   #?(:clj  (-> (LocalDateTime/of ^long year Month/JANUARY 1 0 0)
                (.plusMonths (dec month))
                (.plusDays (dec day))
                (.plusHours hour)
                (.plusMinutes minute)
                (.plusSeconds second)
                (.atZone (ZoneId/systemDefault))
                .toInstant
                Date/from)
      :cljs (js/Date. year (dec month) day hour minute second))))

(defn utc
  "Create a Date assuming parameters are UTC timezone.
  e.g. (utc 2020 1 1 0 0 0) -> 2020-01-01T00:00:00.000-00:00"
  ([year month day] (utc year month day 0 0 0))
  ([year month day hour minute] (utc year month day hour minute 0))
  ([year month day hour minute second] (->local (local year month day hour minute second))))

(defn before?
  "Expects two Dates as arguments. The function returns true if the
  first date comes before the second date and returns false otherwise."
  [^Date first ^Date second]
  #?(:clj  (.before first second)
     :cljs (< (js-invoke first "getTime")
              (js-invoke second "getTime"))))

(defn after?
  "Expects two Date as arguments. The function returns true if the
  first date comes after the second date and returns false otherwise."
  [^Date first ^Date second]
  #?(:clj  (.after first second)
     :cljs (> (js-invoke first "getTime")
              (js-invoke second "getTime"))))

(defn between?
  "Expects the three Dates as arguments. The first date is the date
  being evaluated; the second date is the start date; the last date is the
  end date. The function returns true if the first date is between the start
  and end dates."
  [^Date date ^Date start ^Date end]
  (and
    (after? date start)
    (before? date end)))

(defn leap-year? [year]
  (or (and (= 0 (mod year 4))
           (not (= 0 (mod year 100))))
      (= 0 (mod year 400))))

(defn days-in-month [year month]
  (case month
    0 31
    1 (if (leap-year? year) 29 28)
    2 31
    3 30
    4 31
    5 30
    6 31
    7 31
    8 30
    9 31
    10 30
    11 31))

(defn year
  "Returns the Date's year (local timezone)."
  [^Date datetime]
  #?(:clj  (.getYear (->local-date-time datetime))
     :cljs (js-invoke datetime "getFullYear")))

(defn month
  "Returns the Date's month (local timezone)."
  [^Date datetime]
  #?(:clj  (.getMonthValue (->local-date-time datetime))
     :cljs (inc (js-invoke datetime "getMonth"))))

(defn day
  "Returns the Date's day (local timezone)."
  [^Date datetime]
  #?(:clj  (.getDayOfMonth (->local-date-time datetime))
     :cljs (js-invoke datetime "getDate")))

(defn hour
  "Returns the Date's hour (24-hour clock) (local timezone)."
  [^Date datetime]
  #?(:clj  (.getHour (->local-date-time datetime))
     :cljs (js-invoke datetime "getHours")))

(defn minute
  "Returns the Date's minute."
  [^Date datetime]
  #?(:clj  (.getMinute (->local-date-time datetime))
     :cljs (js-invoke datetime "getMinutes")))

(defn sec
  "Returns the Date's second."
  [^Date datetime]
  #?(:clj  (.getSecond (->local-date-time datetime))
     :cljs (js-invoke datetime "getSeconds")))

#?(:cljs (defmulti -js-mod-time-by-units (fn [_time unit _n _direction] unit)))

#?(:cljs (defmethod -js-mod-time-by-units :days [time _unit n direction]
           (js-invoke time "setDate" (direction (js-invoke time "getDate") n))))

#?(:cljs (defmethod -js-mod-time-by-units :months [time _unit n direction]
           (let [date (js-invoke time "getUTCDate")]
             (js-invoke time "setUTCDate" 1)
             (js-invoke time "setUTCMonth" (direction (js-invoke time "getUTCMonth") n))
             (let [month    (js-invoke time "getUTCMonth")
                   max-date (days-in-month (js-invoke time "getUTCFullYear") month)]
               (js-invoke time "setUTCDate" (min date max-date))))))

#?(:cljs (defmethod -js-mod-time-by-units :years [time _unit n direction]
           (let [year (js-invoke time "getFullYear")]
             (js-invoke time "setFullYear" (direction year n)))))

(defn- mod-time-by-units
  "Modifies the value of a Date object. Expects the first argument to be
  a Date, the second argument to be a vector representing the amount of time to be changed,
  and the last argument to be either a + or - (indicating which direction to modify time)."
  [time [unit n] direction]
  #?(:clj  (let [n      (int (direction n))
                 period (case unit
                          :days (Period/ofDays n)
                          :months (Period/ofMonths n)
                          :years (Period/ofYears n)
                          :else (throw (ex-info (str "invalid duration unit: " unit) {:unit unit})))
                 zdt    (ZonedDateTime/ofInstant (.toInstant time) (ZoneId/of "UTC"))]
             (from-epoch (* 1000 (.toEpochSecond (.plus zdt period)))))
     :cljs (let [new-date (js/Date. (js-invoke time "getTime"))]
             (-js-mod-time-by-units new-date unit n direction)
             new-date)))

(defn- mod-time
  "Modifies the value of a Date. Expects the first argument to be
  a Date object, the second argument to be an amount of milliseconds, and
  the last argument to be either a + or - (indicating which direction to
  modify time)."
  [time bit direction]
  (cond
    (number? bit) #?(:clj  (Date. ^Long (direction (.getTime time) (long bit)))
                     :cljs (js/Date. (direction (js-invoke time "getTime") (long bit))))
    (vector? bit) (mod-time-by-units time bit direction)))

(defn before
  "Rewinds the time on a Date object. Expects a Date object as the first
  argument and a number of milliseconds to rewind time by."
  [time & bits]
  (reduce #(mod-time %1 %2 -) time bits))

(defn after
  "Fast-forwards the time on a Date object. Expects a Date object as the first
  argument and a number of milliseconds to fast-forward time by."
  [time & bits]
  (reduce #(mod-time %1 %2 +) time bits))

(def earlier? before?)
(def later? after?)
(def earlier before)
(def later after)

(defn ago
  "Returns a Date some time (n) before now."
  [n]
  (before (now) n))

(defn from-now
  "Returns a Date some time (n) after now."
  [n]
  (after (now) n))

(defn formatter [format]
  #?(:clj  (let [sdf (SimpleDateFormat. format)]
             (.setTimeZone sdf (TimeZone/getTimeZone "UTC"))
             sdf)
     :cljs (timef/formatter format)))

(def date-formats
  {
   :http       (formatter "EEE, dd MMM yyyy HH:mm:ss ZZZ")
   :rfc1123    (formatter "EEE, dd MMM yyyy HH:mm:ss ZZZ")
   :rfc822     (formatter "EEE, dd MMM yyyy HH:mm:ss Z")
   :ref3339    (formatter #?(:clj  "yyyy-MM-dd'T'HH:mm:ssXXX"
                             :cljs "yyyy-MM-dd'T'HH:mm:ssZZ"))
   :long-no-tz (formatter "EEE, dd MMM yyyy HH:mm:ss")
   :iso8601    (formatter "yyyy-MM-dd HH:mm:ssZ")
   :dense      (formatter "yyyyMMddHHmmss")
   :ymd        (formatter "yyyyMMdd")
   :webform    (formatter "yyyy-MM-dd")
   :web-local  (formatter "yyyy-MM-dd'T'HH:mm")
   :friendly   (formatter "EEE - MMM d, yyyy")
   :short      (formatter "MMM d, yyyy")
   })

(defn- ->formatter [format]
  (cond
    (keyword? format) (format date-formats)
    (string? format) (formatter format)
    (instance? #?(:clj SimpleDateFormat :cljs timef/Formatter) format) format
    :else (throw (ex-info (str "Unhandled date format: " format) {:format format}))))

(defn parse
  "Parses text into a Java Date object. Expects a keyword, string, or SimpleDateFormat
  object as the first object and a string representing the date as the second argument.
  The date is assumed to be in UTC."
  [format value]
  (let [formatter (->formatter format)]
    #?(:clj  (.parse formatter value)
       :cljs (let [goog-dt (timef/parse formatter value)]
               (timec/to-date goog-dt)))))

(defn unparse
  "Returns a string that is populated with a formatted date and time. Expects the
  first argument to be the requested format and the second argument to be the date
  to be formatted.
  The following are options for the first argument:
  1. Keyword - :http, :rfc1123, :iso8601, :dense
  2. String - must be a valid argument to the SimpleDateFormat Java Object
  3. SimpleDateFormat - Java Object"
  [format value]
  (if value
    (let [formatter (->formatter format)]
      #?(:clj  (.format formatter value)
         :cljs (let [goog-dt (timec/from-date value)]
                 (timef/unparse formatter goog-dt))))))

(defn bounds [start end]
  (list start end))

(defn bounds? [thing]
  (and (seq? thing)
       (= 2 (count thing))
       (instant? (first thing))
       (instant? (first (rest thing)))))

(defn start-of [bounds] (first bounds))
(defn end-of [bounds] (first (rest bounds)))

(defn during? [bounds instant]
  (and (after? instant (start-of bounds))
       (before? instant (end-of bounds))))
