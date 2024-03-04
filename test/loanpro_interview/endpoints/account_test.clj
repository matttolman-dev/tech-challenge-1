(ns loanpro-interview.endpoints.account-test
  (:require [clojure.test :refer :all]
            [ksuid.core :as ksuid]
            [loanpro-interview.db :as db]
            [loanpro-interview.endpoints.account :refer :all]
            [loanpro-interview.test-utils :as test-utils]))

(deftest endpoint-account-test
  (let [db (test-utils/db-conf "endpoints-account")
        guid-provider #(ksuid/to-string (ksuid/new-random))
        {user-id :id} (test-utils/create-user db "record-op")
        req {:conn db
             :user-id user-id}]
    (testing "No history"
      (is (= 500
             (-> (get-balance req) :body :balance))))
    (testing "Single op ran"
      (db/record-op! {:txid (guid-provider)
                      :user user-id
                      :op 1
                      :res "{\"total\": 23}"}
                     {:connection db})
      (is (= 490
             (-> (get-balance req) :body :balance))))
    (testing "No balance"
      (db/add-balance! {:txid (guid-provider)
                        :user user-id
                        :amount (-> (db/user-balance {:id user-id} {:connection db})
                                    first
                                    :balance
                                    (-))}
                       {:connection db})
      (is (= 0
             (-> (get-balance req) :body :balance))))
    (testing "Added balance"
      (is (= (-> ((add-balance guid-provider)
                      {:user-id user-id
                       :conn    db
                       :params  {:amount 600}})
                     :body
                     :balance) 600))
      (is (= (-> (get-balance req) :body :balance) 600)))))
