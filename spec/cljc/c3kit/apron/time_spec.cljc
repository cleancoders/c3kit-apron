(ns c3kit.apron.time-spec
  #?(:clj (:import
            [java.util Date]
            [java.text SimpleDateFormat]))
  (:require
    [c3kit.apron.time :as sut :refer [now local before? after? between? year month day hour minute sec
                                      parse unparse years months days hours minutes seconds before after
                                      ago from-now formatter ->utc utc-offset utc millis-since-epoch
                                      earlier? later? earlier later]]
    [speclj.core #?(:clj :refer :cljs :refer-macros) [describe it should should= should-not]]))

(describe "Time"

  (it "now"
    (let [now (now)]
      #?(:clj  (should= Date (.getClass now))
         :cljs (should= true (instance? js/Date now)))
      #?(:clj  (should (> 100 (- (System/currentTimeMillis) (.getTime now))))
         :cljs (should (> 100 (- (. (js/Date.) (getTime)) (.getTime now)))))))

  (it "millis->seconds"
    (should= 0 (sut/millis->seconds 0))
    (should= 0 (sut/millis->seconds 999))
    (should= 1 (sut/millis->seconds 1000))
    (should= 1 (sut/millis->seconds 1500))
    (should= 15 (sut/millis->seconds 15000)))

  (it "seconds-since-epoch"
    (let [now     (now)
          seconds (long (/ (sut/millis-since-epoch now) 1000))]
      (should= seconds (sut/seconds-since-epoch now))))

  (it "creating Dates and getting the pieces"
    (let [dt (local 2011 1 1)]
      (should= 2011 (year dt))
      (should= 1 (month dt))
      (should= 1 (day dt))
      (should= 0 (hour dt))
      (should= 0 (minute dt))
      (should= 0 (sec dt)))
    (let [dt (local 2011 1 2 3 4)]
      (should= 2011 (year dt))
      (should= 1 (month dt))
      (should= 2 (day dt))
      (should= 3 (hour dt))
      (should= 4 (minute dt))
      (should= 0 (sec dt)))
    (let [dt (local 2012 3 4 5 6 7)]
      (should= 2012 (year dt))
      (should= 3 (month dt))
      (should= 4 (day dt))
      (should= 5 (hour dt))
      (should= 6 (minute dt))
      (should= 7 (sec dt))))

  (it "creating from epoch"
    ;January 1, 1970, 00:00:00 GMT.
    (let [epoch (->utc (sut/from-epoch 0))]
      (should= 1970 (year epoch))
      (should= 1 (month epoch))
      (should= 1 (day epoch))
      (should= 0 (hour epoch))
      (should= 0 (minute epoch))
      (should= 0 (sec epoch))))

  ; Only run in AZ timezone
  #_(it "utc offset AZ"
      (should= (* -1 (-> 7 hours)) (utc-offset))
      (should= (* -1 (-> 7 hours)) (utc-offset (now))))

  ; Only run in Central timezone
  #_(it "utc offset in Central TZ"
      (should= (* -1 (-> 5 hours)) (utc-offset (parse :dense "20221105000000")))
      (should= (* -1 (-> 5 hours)) (utc-offset (parse :dense "20221106000000")))
      (should= (* -1 (-> 6 hours)) (utc-offset (parse :dense "20221106070000")))
      (should= (* -1 (-> 6 hours)) (utc-offset (parse :dense "20221107000000"))))

  ; Only run in Central timezone
  #_(it "creates dates relative to now in day increments - across timezone"
      (let [start (local 2022 11 05)] ;; 1 day1 before DSL begins
        (should= (parse :dense "20221105050000") start)
        (should= (parse :dense "20221106050000") (after start (-> 1 days)))
        (should= (parse :dense "20221107060000") (after start (-> 2 days)))
        (should= (parse :dense "20221108060000") (after start (-> 3 days)))))

  (it "local vs utc after DST"
    (let [local-time (local 2020 1 1 1 1 1)
          utc-time   (utc 2020 1 1 1 1 1)]
      (should= (utc-offset local-time) (- (millis-since-epoch utc-time) (millis-since-epoch local-time)))))

  (it "local vs utc during DST"
    (let [local-time (local 2020 6 1 1 1 1)
          utc-time   (utc 2020 6 1 1 1 1)]
      (should= (utc-offset local-time) (- (millis-since-epoch utc-time) (millis-since-epoch local-time)))))

  (it "before? and after?"
    (should= true (before? (local 2011 1 1) (local 2011 1 2)))
    (should= false (before? (local 2011 1 3) (local 2011 1 2)))
    (should= false (after? (local 2011 1 1) (local 2011 1 2)))
    (should= true (after? (local 2011 1 3) (local 2011 1 2))))

  (it "checks if a date is between two other dates"
    (should= true (between? (local 2011 1 2) (local 2011 1 1) (local 2011 1 3)))
    (should= false (between? (local 2011 1 3) (local 2011 1 2) (local 2011 1 1))))

  (it "creates dates relative to now in second increments"
    (should= true (before? (-> 1 seconds ago) (now)))
    (should= true (before? (-> 2 seconds ago) (-> 1 seconds ago)))
    (should= true (after? (-> 1 seconds from-now) (now)))
    (should= true (after? (-> 2 seconds from-now) (-> 1 seconds from-now)))
    (should= true (after? (-> 0.5 seconds from-now) (now))))

  (it "creates dates relative to now in minute increments"
    (should= true (before? (-> 1 minutes ago) (now)))
    (should= true (before? (-> 1 minutes ago) (-> 59 seconds ago)))
    (should= false (before? (-> 1 minutes ago) (-> 61 seconds ago)))
    (should= true (after? (-> 1 minutes from-now) (now)))
    (should= true (after? (-> 1 minutes from-now) (-> 59 seconds from-now)))
    (should= false (after? (-> 1 minutes from-now) (-> 61 seconds from-now)))
    (should= true (after? (-> 0.5 minutes from-now) (now))))

  (it "creates dates relative to now in hour increments"
    (should= true (before? (-> 1 hours ago) (now)))
    (should= true (before? (-> 1 hours ago) (-> 59 minutes ago)))
    (should= false (before? (-> 1 hours ago) (-> 61 minutes ago)))
    (should= true (after? (-> 1 hours from-now) (now)))
    (should= true (after? (-> 1 hours from-now) (-> 59 minutes from-now)))
    (should= false (after? (-> 1 hours from-now) (-> 61 minutes from-now)))
    (should= true (after? (-> 0.5 hours from-now) (now))))

  (it "creates dates relative to now in day increments"
    (should= true (before? (-> 1 days ago) (now)))
    (should= true (before? (-> 1 days ago) (-> 23 hours ago)))
    (should= false (before? (-> 1 days ago) (-> 25 hours ago)))
    (should= true (after? (-> 1 days from-now) (now)))
    (should= true (after? (-> 1 days from-now) (-> 23 hours from-now)))
    (should= false (after? (-> 1 days from-now) (-> 25 hours from-now)))
    (should= true (after? (-> 0.5 days from-now) (now))))

  (it "create dates relative to other dates by month increment"
    (should= "20110201" (unparse :ymd (after (local 2011 1 1) (months 1))))
    (should= "20101201" (unparse :ymd (before (local 2011 1 1) (months 1))))
    (should= true (after? (-> 1 months from-now) (-> 27 days from-now)))
    (should= false (after? (-> 1 months from-now) (-> 32 days from-now)))
    (should= true (before? (-> 1 months ago) (-> 27 days ago)))
    (should= false (before? (-> 1 months ago) (-> 32 days ago))))

  (it "earlier later aliases"
    (should= before? earlier?)
    (should= after? later?)
    (should= before earlier)
    (should= after later))

  (it "leap-year?"
    (should= false (sut/leap-year? 2011))
    (should= true (sut/leap-year? 2012))
    (should= false (sut/leap-year? 2100))
    (should= true (sut/leap-year? 2400)))

  (it "days in month"
    (should= 31 (sut/days-in-month 2000 0))
    (should= 29 (sut/days-in-month 2000 1))
    (should= 28 (sut/days-in-month 2001 1))
    (should= 31 (sut/days-in-month 2001 2))
    (should= 30 (sut/days-in-month 2001 3))
    (should= 31 (sut/days-in-month 2001 4))
    (should= 30 (sut/days-in-month 2001 5))
    (should= 31 (sut/days-in-month 2001 6))
    (should= 31 (sut/days-in-month 2001 7))
    (should= 30 (sut/days-in-month 2001 8))
    (should= 31 (sut/days-in-month 2001 9))
    (should= 30 (sut/days-in-month 2001 10))
    (should= 31 (sut/days-in-month 2001 11)))

  (it "rolling over a month with not enough days"
    (should= "20110228" (unparse :ymd (after (local 2011 1 31) (months 1)))))

  (it "create dates relative to other dates by year increment"
    (should= "20120101" (unparse :ymd (after (local 2011 1 1) (years 1))))
    (should= "20100101" (unparse :ymd (before (local 2011 1 1) (years 1))))
    (should= true (after? (-> 1 years from-now) (-> 11 months from-now)))
    (should= false (after? (-> 1 years from-now) (-> 13 months from-now)))
    (should= true (before? (-> 1 years ago) (-> 11 months ago)))
    (should= false (before? (-> 1 years ago) (-> 13 months ago))))

  (it "month and year units are rounded"
    (should= [:months 1] (months 0.5))
    (should= [:months 0] (months 0.4))
    (should= [:years 1] (years 0.5))
    (should= [:years 0] (years 0.4)))

  (it "parses and formats dates in HTTP format"
    (let [date (parse :http "Sun, 06 Nov 1994 08:49:37 GMT")]
      (should= true (after? date (local 1994 11 5)))
      (should= true (before? date (local 1994 11 7)))
      ;      (should= "Sun, 06 Nov 1994 02:49:37 -0600" (unparse :http date)) ; only works in certain CST zone
      ))

  (it "parses and formats dates in custom format"
    (let [date (parse "MMM d, yyyy HH:mm" "Nov 6, 1994 08:49")]
      (should= "Nov 6, 1994 08:49" (unparse "MMM d, yyyy HH:mm" date))))

  (it "parses and formats dates in custom format object"
    (let [format (formatter "MMM d, yyyy HH:mm")
          date   (parse format "Nov 6, 1994 08:49")]
      (should= "Nov 6, 1994 08:49" (unparse format date))))

  #?(:clj  (it "parses and formats dates in ISO 8601 format"
             (let [date (parse :iso8601 "1994-11-06 08:49:12 GMT")]
               (should= "1994-11-06 08:49:12+0000" (unparse :iso8601 date))))
     :cljs (it "parses and formats dates in ISO 8601 format"
             (let [date (parse :iso8601 "1994-11-06 08:49:12Z")]
               (should= "1994-11-06 08:49:12Z" (unparse :iso8601 date)))))

  (it "parses REF 3339 format"
    (let [date1 (parse :ref3339 "2022-03-04T23:59:02-05:00")
          date2 (parse :ref3339 "2022-03-04T23:59:02+05:00")
          date3 (parse :ref3339 "2022-03-04T23:59:02-00:00")
          date4 (parse :ref3339 "2022-03-04T23:59:02Z")]
      (should= "2022-03-05T04:59:02Z" (unparse :ref3339 date1))
      (should= "2022-03-04T18:59:02Z" (unparse :ref3339 date2))
      (should= "2022-03-04T23:59:02Z" (unparse :ref3339 date3))
      (should= "2022-03-04T23:59:02Z" (unparse :ref3339 date4))))

  (it "parses and formats :webform datas"
    (let [date (parse :webform "2020-03-31")
          utc  (sut/->utc date)]
      (should= 2020 (year utc))
      (should= 3 (month utc))
      (should= 31 (day utc))
      (should= 0 (hour utc))
      (should= 0 (minute utc))
      (should= 0 (sec utc))
      (should= "2020-03-31" (unparse :webform date))))

  (it "time range"
    (let [time1 (sut/local 1939 9 1)
          time2 (sut/local 1945 9 2)
          ww2   (sut/bounds time1 time2)]
      (should (sut/bounds? ww2))
      (should= time1 (sut/start-of ww2))
      (should= time2 (sut/end-of ww2))
      (should-not (sut/during? ww2 (sut/local 1920 1 1)))
      (should (sut/during? ww2 (sut/local 1941 1 1)))
      (should-not (sut/during? ww2 (sut/local 1950 1 1)))))

  )
