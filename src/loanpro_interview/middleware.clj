(ns loanpro-interview.middleware
  (:require [clojure.tools.logging :as log]
            [loanpro-interview.db :as db]
            [buddy.core.hash :as hash]
            [buddy.core.codecs :as codec]
            [ksuid.core :as ksuid]
            [jsonista.core :as j])
  (:import (java.text SimpleDateFormat)))

(defn with-connect-db
  [conn-provider]
  "Adds a database connection to the request"
  (fn [handler]
    (fn [request]
      (handler (assoc request :conn (conn-provider))))))

(defn- parse-date-time [dt]
  "Parses a DB date time into a unix timestamp (seconds_"
  (let [format (new SimpleDateFormat "yyyy-MM-dd hh:mm:ss")
        parsed (.parse format dt)
        timestamp (/ (.getTime parsed) 1000)]
    timestamp))

(defn- downgrade? [auth-level last-auth-time cur-time]
  "Detects whether an auth level should be downgraded"
  (and (>= auth-level (:secure-auth db/auth-levels)) (< (parse-date-time last-auth-time) (- cur-time (* 10 60)))))

(defn with-validate-session
  "Validates the current session and adds session data to the request object"
  ([] (with-validate-session #(quot (System/currentTimeMillis) 1000)))
  ([timestamp-provider]
   (fn [handler]
     (fn [request]
       (let [{{id :token} :session conn :conn} request]
         (if (not id)
           {:status 401}
           (let [db-session (first (db/session-by-id {:id id} {:connection conn}))]
             (if (not db-session)
               {:status 401 :session nil}
               (let [{user-id        :user_id
                      auth-level     :auth_level
                      last-auth-time :last_auth_time} db-session]
                 (if (downgrade? auth-level last-auth-time (timestamp-provider))
                   (do (db/set-session-auth-level! {:session id :level (:basic-auth db/auth-levels)} {:connection conn})
                       (handler (assoc request :auth-level (:basic-auth db/auth-levels) :user-id user-id)))
                   (handler (assoc request :auth-level auth-level :user-id user-id))))))))))))

(defn params-to-keywords [handler]
  "Middleware to convert keys in the params object to keywords"
  (fn [request]
    (let [{params :params} request]
      (handler (->> params
                    (map (fn [[k v]]
                           [(if (not (keyword? k))
                              (keyword k)
                              k) v]))
                    (into {})
                    (assoc request :params))))))

(defn- get-fingerprint [orig]
  "Retrieves a fingerprint for a piece of data"
  (-> orig
      (hash/sha3-256)
      (codec/bytes->b64-str)))

(defn- get-fingerprints [request]
  "Gets all fingerpritns for a request"
  (let [{params :params} request
        uname (get-fingerprint (-> params :username))
        ip (get-fingerprint (-> request :remote-addr))
        device (get-fingerprint (-> request :headers (get "device-id" (-> request :headers (get "user-agent")))))]
    {:uname  uname
     :ip     ip
     :device device}))

(defn risk-filter [handler]
  "Filters out risky requests with a 429"
  (fn [request]
    (let [{conn :conn} request
          rating (db/auth-risk-rating (get-fingerprints request) {:connection conn})
          is-risky? (-> rating first :risky (not= 0))]
      (if is-risky?
        {:status 429}
        (handler request)))))

(defn risk-logger [handler]
  "Logs an authentication attempt for risk filtering"
  (fn [request]
    (let [{conn :conn} request
          res (handler request)
          {status :status} res
          success? (if (< status 400) 1 0)]
      (db/auth-log-attempt! (assoc (get-fingerprints request) :success success?) {:connection conn})
      res)))

(defn with-auth-level
  "Enforces a minimum authentication level"
  ([] (with-auth-level (:basic-auth db/auth-levels)))
  ([min-auth-level]
   (fn [handler]
     (fn [request]
       (let [{auth-level :auth-level} request]
         (if (>= auth-level min-auth-level)
           (handler request)
           {:status 403}))))))

(defn- get-op-from-request [request]
  (-> request :reitit.core/match :result :post :data :op-name))

(defn can-do-operation? [handler]
  (fn [request]
    (let [op-name (get-op-from-request request)
          op-id (db/op-to-id op-name)
          {user-id :user-id conn :conn} request
          can-do? (not= 0 (or (:can_do (first (db/user-can-do-op {:id user-id :op op-id} {:connection conn}))) 0))]
      (if (not can-do?)
        {:status 402}
        (handler
          (assoc request :op-name op-name :op op-id))))))

(defn record-operation
  ([] (record-operation #(java.time.Instant/now)))
  ([time-provider]
   (fn [handler]
     (fn [request]
       (let [op-name (get-op-from-request request)
             op-id (db/op-to-id op-name)
             {user-id :user-id conn :conn} request
             res (handler request)]
             (if (= 200 (:status res))
               (let [txid (ksuid/to-string (ksuid/new-random-with-time (time-provider)))
                     res-json (j/write-value-as-string (:body res))]
                 (if (= 0 (db/record-op! {:op op-id :user user-id :txid txid :res res-json} {:connection conn}))
                   {:status 402}
                   res))
               res))))))

(defn log-request [handler]
  (fn [request]
    (let [tid (or (-> request :headers (get "tid"))
                  (ksuid/to-string (ksuid/new-random)))]
      (log/info (str "[request_start][tid=" tid "]"))
      (let [res (handler (assoc request :tid tid))
            {status :status} res]
        (log/info (str "[request_end][tid=" tid "][status=" status "]"))
        res))))
