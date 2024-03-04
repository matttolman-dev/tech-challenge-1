(ns loanpro-interview.endpoints.auth
  (:require [clojure.tools.logging :as log]
            [loanpro-interview.db :as db]
            [reitit.coercion.spec]
            [loanpro-interview.middleware :as m]
            [buddy.hashers :as hashers]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codec])
  (:use [metis.core]))

(defvalidator new-user-validator
              [:username [:presence :email]]
              [:password [:presence
                          :length {:greater-than-or-equal-to 6}
                          :confirmation {:confirm :password-confirm}]])

(defvalidator login-validator
              [:username [:presence :email]]
              [:password [:presence]])

(defn- login-user [params conn]
  (let [{username :username password :password} params]
    (let [{user-id :id pwd-hash :password status :status} (first (db/user-creds-by-username {:username username} {:connection conn}))]
      (if (and user-id pwd-hash)
        (let [{valid? :valid} (hashers/verify password pwd-hash)]
          (if valid?
            (let [session-id (codec/bytes->b64-str (nonce/random-bytes 16))]
              (db/create-session! {:session session-id
                                   :user    user-id
                                   :level   (if (= status 1) (:basic-auth db/auth-levels)
                                                             (:inactive-auth db/auth-levels))}
                                  {:connection conn})
              (log/info (str "[session_create_for=" user-id "]"))
              {:status  204
               :session {:token session-id}})
            {:status 401}))
        {:status 401}))))

(defn routes
  [guid-provider]
  ["auth/"
   ["login"
    {:post {:middleware [m/risk-filter m/risk-logger]
            :parameters {:form {:username string? :password string?}}
            :responses  {204 {}
                         401 {}
                         429 {}}
            :handler    (fn [req]
                          (let [{params :params conn :conn} req
                                errs (login-validator params)]
                            (if (not (empty? errs))
                              (do
                                (log/warn (str "[invalid_login_request]" (with-out-str (clojure.pprint/pprint errs))))
                                {:status 400
                                 :body   errs})
                              (login-user params conn))))}}]
   ["signup"
    {:post {:middleware [m/risk-filter]
            :parameters {:json {:username string? :password string? :password-confirm string?}}
            :responses  {204 {}
                         400 {:body map?}
                         429 {}}
            :handler    (fn [req]
                          (let [{params :params conn :conn} req
                                errs (new-user-validator params)]
                            (if (not (empty? errs))
                              (do
                                (log/warn (str "[invalid_signup_request]" (with-out-str (clojure.pprint/pprint errs))))
                                {:status 400
                                 :body   errs})
                              (let [id (guid-provider)
                                    success? (not= 0 (db/create-user!
                                                       (-> params
                                                           (assoc :id id :password (hashers/derive (:password params)))
                                                           (dissoc :password-confirm))
                                                       {:connection conn}))]
                                (if success?
                                  (login-user params conn)
                                  (do
                                    (let [errs {:username ["username taken"]}]
                                      (log/warn (str "[invalid_signup_request]" (with-out-str (clojure.pprint/pprint errs))))
                                      {:status 400
                                       :body errs})))))))}}]
   ["logout"
    {:post {:responses {204 {}}
            :handler   (fn [req]
                         (when-let [{{token :token} :session conn :conn} req]
                           (db/clear-session! {:id token} {:connection conn})
                           (log/info "[session_ended]"))
                         {:status  204
                          :session nil})}}]])
