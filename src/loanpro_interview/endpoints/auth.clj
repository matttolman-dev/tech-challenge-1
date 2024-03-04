(ns loanpro-interview.endpoints.auth
  (:require [loanpro-interview.db :as db]
            [omniconf.core :as cfg]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rcc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [loanpro-interview.middleware :as m]
            [ksuid.core :as ksuid]
            [ring.middleware.json :as rj]
            [buddy.hashers :as hashers]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codec])
  (:use [metis.core])
  (:import (java.sql SQLIntegrityConstraintViolationException)))

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
              {:status  204
               :session {:token session-id}})
            {:status 401}))
        {:status 401}))))

(defn routes
  ([] (routes db/get-connection))
  ([db-conn-provider] (routes db-conn-provider #(java.time.Instant/now)))
  ([db-conn-provider time-provider]
   ["auth/"
    {:middleware [rj/wrap-json-params
                  rcc/coerce-request-middleware
                  muuntaja/format-response-middleware
                  m/params-to-keywords
                  (m/with-connect-db db-conn-provider)
                  m/risk-filter
                  rcc/coerce-response-middleware]}
    ["login"
     {:post {:middleware [m/risk-logger]
             :parameters {:form {:username string? :password string?}}
             :responses  {204 {}
                          401 {}
                          429 {}}
             :handler    (fn [req]
                           (let [{params :params conn :conn} req
                                 errs (login-validator params)]
                             (if (not (empty? errs))
                               {:status 400
                                :body   errs}
                               (login-user params conn))))}}]
    ["signup"
     {:post {:parameters {:json {:username string? :password string? :password-confirm string?}}
             :responses  {204 {}
                          400 {:body map?}
                          429 {}}
             :handler    (fn [req]
                           (let [{params :params conn :conn} req
                                 errs (new-user-validator params)]
                             (if (not (empty? errs))
                               {:status 400
                                :body   errs}
                               (let [id (ksuid/to-string (ksuid/new-random-with-time (time-provider)))
                                     success? (not= 0 (db/create-user!
                                       (-> params
                                           (assoc :id id :password (hashers/derive (:password params)))
                                           (dissoc :password-confirm))
                                       {:connection conn}))]
                                 (if success?
                                   (login-user params conn)
                                   {:status 400
                                    :body {:username ["username taken"]}})))))}}]]))
