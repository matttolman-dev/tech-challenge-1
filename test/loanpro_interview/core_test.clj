(ns loanpro-interview.core-test
  (:require [clojure.test :refer :all]
            [ksuid.core :as ksuid]
            [loanpro-interview.core :refer :all]
            [loanpro-interview.db :as db]
            [loanpro-interview.test-utils :as test-utils]))

(deftest app-routing-account-balance
  (let [conn (test-utils/db-conf "core-account-balance")
        test-app (app (fn [] conn))]
    (testing "Unauthorized requests should be denied"
      (is (= {:status 401}
             (test-app {:request-method :get
                        :uri            "/api/v1/account/balance"})))
      (is (= {:status 401}
             (test-app {:request-method :put
                        :params         {:amount 400}
                        :uri            "/api/v1/account/balance"}))))
    (let [session
          (test-utils/create-user-app test-app)]
      (testing "Authorized requests should be allowed"
        (is (= 200
               (:status (test-app {:request-method :put
                                   :params         {:amount 400}
                                   :uri            "/api/v1/account/balance"
                                   :session        session})))))
      (is (= 200
             (:status (test-app {:request-method :get
                                 :uri            "/api/v1/account/balance"
                                 :session        session}))))
      (testing "logging out invalidates session id"
        (is (= 204
               (:status (test-app {:request-method :post
                                   :uri            "/api/v1/auth/logout"
                                   :session        session}))))
        (is (= 401
               (:status (test-app {:request-method :get
                                   :session        session
                                   :uri            "/api/v1/account/balance"})))))
      (testing "logging in creates a new session"
        (let [{session :session} (test-app {:request-method :post
                                            :params         {:username "test@example.com"
                                                             :password "Password1!"}
                                            :uri            "/api/v1/auth/login"})]
          (is (= 200
                 (:status (test-app {:request-method :get
                                 :uri            "/api/v1/account/balance"
                                 :session        session})))))))))

(deftest app-routing-ops
  (let [conn (test-utils/db-conf "core-ops")
        test-app (app (fn [] conn)
                      #(ksuid/to-string (ksuid/new-random))
                      (fn [ui]
                        (is (= ui "https://www.random.org/strings/?num=1&len=10&digits=on&upperalpha=on&loweralpha=on&format=plain&rnd=new"))
                        (atom {:status 200
                               :body   "abcdefg"})))]
    (testing "Unauthorized requests should be denied"
      (is (= {:status 401}
             (test-app {:request-method :post
                        :uri            "/api/v1/ops/add"
                        :params         {"x" 12 "y" 34}})))
      (is (= {:status 401}
             (test-app {:request-method :post
                        :params         {"x" 45 "y" 3}
                        :uri            "/api/v1/ops/subtract"})))
      (is (= {:status 401}
             (test-app {:request-method :post
                        :params         {"x" 45 "y" 3}
                        :uri            "/api/v1/ops/multiply"})))
      (is (= {:status 401}
             (test-app {:request-method :post
                        :params         {"x" 45 "y" 3}
                        :uri            "/api/v1/ops/divide"})))
      (is (= {:status 401}
             (test-app {:request-method :post
                        :params         {"x" 45}
                        :uri            "/api/v1/ops/square-root"})))
      (is (= {:status 401}
             (test-app {:request-method :post
                        :uri            "/api/v1/ops/random-str"}))))
    (let [session (test-utils/create-user-app test-app)]
      (testing "Authorized requests should be allowed"
        (is (= 200
               (:status (test-app {:request-method :post
                                   :uri            "/api/v1/ops/add"
                                   :params         {"x" 12 "y" 34}
                                   :session        session}))))
        (is (= 200
               (:status (test-app {:request-method :post
                                   :params         {"x" 45 "y" 3}
                                   :uri            "/api/v1/ops/subtract"
                                   :session        session}))))
        (is (= 200
               (:status (test-app {:request-method :post
                                   :params         {"x" 45 "y" 3}
                                   :uri            "/api/v1/ops/multiply"
                                   :session        session}))))
        (is (= 200
               (:status (test-app {:request-method :post
                                   :params         {"x" 45 "y" 3}
                                   :uri            "/api/v1/ops/divide"
                                   :session        session}))))
        (is (= 200
               (:status (test-app {:request-method :post
                                   :params         {"x" 45}
                                   :uri            "/api/v1/ops/square-root"
                                   :session        session}))))
        (is (= 200
               (:status (test-app {:request-method :post
                                   :params         {"x" 45}
                                   :uri            "/api/v1/ops/random-str"
                                   :session        session})))))
      (let [user-id (:user_id (first (db/session-by-id {:id (:token session)} {:connection conn})))
            balance (:balance (first (db/user-balance {:id user-id} {:connection conn})))]
        (db/add-balance! {:txid (ksuid/to-string (ksuid/new-random))
                          :user user-id
                          :amount (- balance)}
                         {:connection conn})
        (testing "Lack of credits should result in 402"
          (is (= 402
                 (:status (test-app {:request-method :post
                                     :uri            "/api/v1/ops/add"
                                     :params         {"x" 12 "y" 34}
                                     :session        session}))))
          (is (= 402
                 (:status (test-app {:request-method :post
                                     :params         {"x" 45 "y" 3}
                                     :uri            "/api/v1/ops/subtract"
                                     :session        session}))))
          (is (= 402
                 (:status (test-app {:request-method :post
                                     :params         {"x" 45 "y" 3}
                                     :uri            "/api/v1/ops/multiply"
                                     :session        session}))))
          (is (= 402
                 (:status (test-app {:request-method :post
                                     :params         {"x" 45 "y" 3}
                                     :uri            "/api/v1/ops/divide"
                                     :session        session}))))
          (is (= 402
                 (:status (test-app {:request-method :post
                                     :params         {"x" 45}
                                     :uri            "/api/v1/ops/square-root"
                                     :session        session}))))
          (is (= 402
                 (:status (test-app {:request-method :post
                                     :params         {"x" 45}
                                     :uri            "/api/v1/ops/random-str"
                                     :session        session})))))))))

