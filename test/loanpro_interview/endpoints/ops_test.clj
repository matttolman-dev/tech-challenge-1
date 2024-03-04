(ns loanpro-interview.endpoints.ops_test
  (:require [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]
            [loanpro-interview.test-utils]
            [loanpro-interview.endpoints.ops :refer :all]))

(deftest add-test
  (stest/check `add))

(deftest subtract-test
  (stest/check `subtract))

(deftest multiply-test
  (stest/check `multiply))

(deftest divide-test
  (stest/check `divide))

(deftest square-root-test
  (stest/check `square-root))

(deftest random-str-test
  (let [http-get (fn [_] (atom {:status 200 :body "abcdefg"}))]
    (is (= (:body @(http-get ""))
           (:res (:body ((random-str http-get)
                         {})))))))
