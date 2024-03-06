(ns loanpro-interview.endpoints.ops_test
  (:require [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]
            [loanpro-interview.test-utils]
            [loanpro-interview.endpoints.ops :refer :all]))

(deftest add-test
  (is (= (-> (add {:params {:x "0.1" :y "0.2"}}) :body :res) "0.3"))
  (is (= (-> (add {:params {:x "1" :y "2"}}) :body :res) "3")))

(deftest subtract-test
  (is (= (-> (subtract {:params {:x "0.1" :y "0.2"}}) :body :res) "-0.1"))
  (is (= (-> (subtract {:params {:x "1" :y "2"}}) :body :res) "-1")))

(deftest multiply-test
  (is (= (-> (multiply {:params {:x "0.2" :y "0.2"}}) :body :res) "0.04"))
  (is (= (-> (multiply {:params {:x "3" :y "2"}}) :body :res) "6")))

(deftest divide-test
  (is (= (-> (divide {:params {:x "0.3" :y "2"}}) :body :res) "0.15"))
  (is (= (-> (divide {:params {:x "1" :y "2"}}) :body :res) "0.5")))

(deftest square-root-test
  (is (= (-> (square-root {:params {:x "0.04"}}) :body :res) "0.2"))
  (is (= (-> (square-root {:params {:x "4"}}) :body :res) "2.0")))

(deftest random-str-test
  (let [http-get (fn [_] (atom {:status 200 :body "abcdefg"}))]
    (is (= (:body @(http-get ""))
           (:res (:body ((random-str http-get)
                         {})))))))
