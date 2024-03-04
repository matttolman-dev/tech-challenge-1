(ns loanpro-interview.core-test
  (:require [clojure.test :refer :all]
            [loanpro-interview.core :refer :all]))

()

(deftest app-routing-404
  (let [test-app (app)]
    (test-app ))
  (testing "FIXME, I fail."
    (is (= 0 1))))
