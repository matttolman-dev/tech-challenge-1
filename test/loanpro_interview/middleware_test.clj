(ns loanpro-interview.middleware-test
  (:require [clojure.test :refer :all]
            [ksuid.core :as ksuid]
            [loanpro-interview.db :as db]
            [loanpro-interview.middleware :refer :all]
            [loanpro-interview.test-utils :as test-utils]))

(def ^:private echo-handler (fn [x] x))

(deftest with-connect-db-test
  (let [db (test-utils/db-conf "middleware-db-conn")
        handler echo-handler
        req {}]
    (is (= {:conn db} (((with-connect-db (fn [] db)) handler) req)))))

(deftest with-validate-session-test
  (let [db (test-utils/db-conf "middleware-session-validate")
        handler echo-handler
        req {:conn db}
        {user-id :id} (test-utils/create-user db "session")
        session-id (ksuid/to-string (ksuid/new-random))]
    (db/create-session! {:session session-id
                         :user user-id
                         :level 20}
                        {:connection db})
    (testing "No session"
      (is (= {:status 401} (((with-validate-session) handler) req))))
    (testing "No downgrade - 20"
      (let [req (assoc req :session {:token session-id})]
        (is (= (assoc req :auth-level 20
                          :user-id user-id)
               (((with-validate-session) handler) req)))))
    (testing "No downgrade - 30"
      (db/set-session-auth-level!
        {:id session-id
         :auth_level 30}
        {:connection db})
      (let [req (assoc req :session {:token session-id})]
        (is (= (assoc req :auth-level 30
                          :user-id user-id)
               (((with-validate-session) handler) req)))))
    (testing "Downgrade - 30"
      (let [req (assoc req :session {:token session-id})]
        (is (= (assoc req :auth-level 20
                          :user-id user-id)
               (((with-validate-session
                   (fn []
                     (+ 900000 (quot (System/currentTimeMillis) 1000))))
                 handler) req)))))
    (testing "No downgrade, long time - 20"
      (db/set-session-auth-level!
        {:id session-id
         :auth_level 20}
        {:connection db})
      (let [req (assoc req :session {:token session-id})]
        (is (= (assoc req :auth-level 20
                          :user-id user-id)
               (((with-validate-session
                   (fn []
                     (+ 900000 (quot (System/currentTimeMillis) 1000))))
                 handler) req)))))))

(deftest params-to-keywords-test
  (let [in-params {"abc" 1 "def" 2 :hij 3}
        handler echo-handler
        req {:params in-params}]
    (is (= {:params {:abc 1 :def 2 :hij 3}}
           ((params-to-keywords handler) req)))))

(deftest risk-filter-test
  (let [db (test-utils/db-conf "middleware-risk")
        handler echo-handler
        {username :username} (test-utils/create-user db "risk")
        req {:params {:username username}
             :remote-addr "127.0.0.1"
             :headers {"device-id" "test"}
             :conn db}]
    (testing "no risky behavior"
      (is (= req ((risk-filter handler) req))))
    (testing "risky behavior"
      (doall
        (->>
          (range)
          (take 20)
          (map (fn [_]
                 (db/auth-log-attempt! (assoc (get-fingerprints req) :success 0)
                                       {:connection db})))))
      (is (= {:status 429} ((risk-filter handler) req)))
      ; test that only one match is needed to block
      (let [req (assoc req :remote-addr "127.0.0.2" :headers {"device-id" "test"})]
        (is (= {:status 429} ((risk-filter handler) req))))
      (let [req (assoc req :remote-addr "127.0.0.2" :params {:username "test3"})]
        (is (= {:status 429} ((risk-filter handler) req))))
      (let [req (assoc req :headers {"device-id" "test"} :params {:username "test3"})]
        (is (= {:status 429} ((risk-filter handler) req))))
      ; Test that all params being different goes through
      (let [req (assoc req :remote-addr "127.0.0.2" :headers {"device-id" "test5"} :params {:username "test35"})]
        (is (= req ((risk-filter handler) req)))))))

(deftest with-auth-level-test
  (let [db (test-utils/db-conf "middleware-aal")
        handler echo-handler
        req {:conn db}]
    (testing "No session"
      (is (= 403 (:status (((with-auth-level) handler) req)))))
    (testing "Insufficient auth"
      (is (= 403 (:status (((with-auth-level 20) handler)
                           (assoc req :auth-level 10))))))
    (testing "Equal auth"
      (is (not= 403 (:status (((with-auth-level 20) handler)
                           (assoc req :auth-level 20))))))
    (testing "Better auth"
      (is (not= 403 (:status (((with-auth-level 20) handler)
                           (assoc req :auth-level 30))))))))

(deftest record-operation-test
  (let [db (test-utils/db-conf "middleware-record-op")
        guid-provider #(ksuid/to-string (ksuid/new-random))
        res {:status 200 :body {:total 23}}
        handler (fn [_] res)
        {user-id :id} (test-utils/create-user db "record-op")
        req {:conn db
             :user-id user-id
             :request-method :post
             :reitit.core/match {:result {:post {:data {:op-name :addition}}}}}]
    (testing "No history"
      (is (= res
             (((record-operation guid-provider) handler) req))))
    (testing "Single op ran"
      (db/record-op! {:txid (guid-provider)
                      :user user-id
                      :op 1
                      :res "{\"total\": 23}"}
                     {:connection db})
      (is (= res
             (((record-operation guid-provider) handler) req))))
    (testing "No balance"
      (db/add-balance! {:txid (guid-provider)
                        :user user-id
                        :amount (-> (db/user-balance {:id user-id} {:connection db})
                                    first
                                    :balance
                                    (-))}
                       {:connection db})
      (is (= {:status 402}
             (((record-operation guid-provider) handler) req))))
    (testing "Added balance"
      (db/add-balance! {:txid (guid-provider)
                        :user user-id
                        :amount 600}
                       {:connection db})
      (is (= res
             (((record-operation guid-provider) handler) req))))))

(let [db (test-utils/db-conf "middleware-can-do")
      guid-provider #(ksuid/to-string (ksuid/new-random))
      handler echo-handler
      {user-id :id} (test-utils/create-user db "can-do")
      req {:conn db
           :user-id user-id
           :request-method :post
           :reitit.core/match {:result {:post {:data {:op-name :addition}}}}}]
  (testing "No history"
    (is (= (assoc req
             :op-name :addition
             :op 1)
           ((can-do-operation? handler) req))))
  (testing "Single op ran"
    (db/record-op! {:txid (guid-provider)
                    :user user-id
                    :op 1
                    :res "{\"total\": 23}"}
                   {:connection db})
    (is (= (assoc req
             :op-name :addition
             :op 1)
           ((can-do-operation? handler) req))))
  (testing "No balance"
    (db/add-balance! {:txid (guid-provider)
                      :user user-id
                      :amount (-> (db/user-balance {:id user-id} {:connection db})
                                  first
                                  :balance
                                  (-))}
                     {:connection db})
    (is (= {:status 402}
           ((can-do-operation? handler) req))))
  (testing "Added balance"
    (db/add-balance! {:txid (guid-provider)
                      :user user-id
                      :amount 600}
                     {:connection db})
    (is (= (assoc req
             :op-name :addition
             :op 1)
           ((can-do-operation? handler) req)))))
