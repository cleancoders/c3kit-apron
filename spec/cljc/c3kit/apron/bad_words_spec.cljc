(ns c3kit.apron.bad-words-spec
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [describe it should-not should]]
            [clojure.string :as str]
            [c3kit.apron.bad-words :as sut]))

;region test profanity
(defn expand-patterns [pattern-set]
  (reduce (fn [acc pattern]
            (let [base-word    (str/replace pattern "*" "")
                  example-word (str/replace pattern "*" "x")]
              (conj acc base-word example-word)))
          #{}
          pattern-set))

(def variation-words (expand-patterns sut/patterns))

(def test-words #{"@ss" "4ss" "4ssh0l3" "c0ck" "sh!t" "sh!+" "b!+ch" "b17ches" "f4gg!t" "motha fuckah"})
; endregion

(describe "bad-words"

  (it "does not"
    (should-not (sut/contains-profanity? ""))
    (should-not (sut/contains-profanity? "hello"))
    (should-not (sut/contains-profanity? "dam"))
    (should-not (sut/contains-profanity? "shirt"))
    (should-not (sut/contains-profanity? "Hell0 there!"))
    (should-not (sut/contains-profanity? "shitaki"))
    (should-not (sut/contains-profanity? "shellfish"))
    (should-not (sut/contains-profanity? "assemble")))

  (it "does"
    (should (sut/contains-profanity? "asshol3"))
    (should (sut/contains-profanity? "shitty"))
    (should (sut/contains-profanity? "sh1tty"))
    (should (sut/contains-profanity? "hell?"))
    (should (sut/contains-profanity? "shi+"))
    (should (sut/contains-profanity? "shi+y"))
    (should (sut/contains-profanity? "bullshit"))
    (should (sut/contains-profanity? "bullsh1t"))
    (should (sut/contains-profanity? "bullsh!t"))
    (should (sut/contains-profanity? "bullsh1tty"))
    (should (sut/contains-profanity? "what the hell?")))

  (it "catches all specified bad words"
    (doseq [word sut/words]
      (when-not (sut/contains-profanity? word) (println (str "Failure: " word)))
      (should (sut/contains-profanity? word)))

    (doseq [test-word test-words]
      (when-not (sut/contains-profanity? test-word) (println (str "Failure: " test-word)))
      (should (sut/contains-profanity? test-word))))

  (it "catches extensions of common swears"
    (doseq [word variation-words]
      (when-not (sut/contains-profanity? word) (println (str "Failure: " word)))
      (should (sut/contains-profanity? word))))

  )