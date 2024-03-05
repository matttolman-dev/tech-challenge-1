(ns loanpro-interview.middleware
  (:require [clojure.tools.logging :as log]
            [loanpro-interview.db :as db]
            [buddy.core.hash :as hash]
            [buddy.core.codecs :as codec]
            [clojure.spec.alpha :as s]
            [jsonista.core :as j]
            [ring.core.spec])
  (:import (java.text SimpleDateFormat)
           (org.apache.commons.lang3.exception ExceptionUtils)))

(s/def ::params map?)

(s/def ::ring-request
  (s/keys
    :opt-un [:ring.request/server-port
             :ring.request/server-name
             :ring.request/remote-addr
             :ring.request/uri
             :ring.request/scheme
             :ring.request/protocol
             :ring.request/headers
             :ring.request/request-method
             :ring.request/query-string
             :ring.request/body
             :reitit.core/match
             ::params]))


(s/def ::ring-response
  (s/keys :req-un [:ring.response/status]
          :opt-un [:ring.response/headers
                   :ring.response/body]))

(s/def ::request-handler (s/fspec :args (s/cat :request ::ring-request)
                                  :ret ::ring-response))

; using any? since an fspec creates a lot of excess calls when instrumentation is on for some reason
(s/def ::ring-handler any?)

(s/def ::middleware (s/fspec
                      :args (s/cat :handler ::ring-handler)
                      :ret ::request-handler))

(s/def ::timestamp-provider
  (s/fspec
    :args (s/cat)
    :ret int?))

(s/def ::guid-provider
  (s/fspec
    :args (s/cat)
    :ret (s/and string? #(= (count %) 27))))

(defn with-connect-db
  [conn-provider]
  "Adds a database connection to the request"
  (fn [handler]
    (fn [request]
      (handler (assoc request :conn (conn-provider))))))

(s/fdef with-connect-db
        :args (s/cat :conn-provider ::db/conn-provider)
        :ret ::middleware)

(defn- parse-date-time [dt]
  "Parses a DB date time into a unix timestamp (seconds_"
  (let [format (new SimpleDateFormat "yyyy-MM-dd hh:mm:ss")
        parsed (.parse format dt)
        timestamp (/ (.getTime parsed) 1000)]
    timestamp))


(s/def ::date-time (s/and string? #(re-matches #"\d{4}-(0\d|1[0-2])-([0-2]\d|3[01]) ([01]?\d|2[0-3]):([0-5]?\d):([0-5]?\d|60)" %)))

(s/fdef parse-date-time
        :args (s/and (s/cat :dt ::date-time))
        :ret int?)

(defn- downgrade? [auth-level last-auth-time cur-time]
  "Detects whether an auth level should be downgraded"
  (and (>= auth-level (:secure-auth db/auth-levels)) (< (parse-date-time last-auth-time) (- cur-time (* 10 60)))))

(s/fdef downgrade?
        :args (s/cat :auth-level ::db/auth-level
                     :last-auth-time ::date-time
                     :cur-time int?)
        :ret boolean?)

(defn with-validate-session
  "Validates the current session and adds session data to the request object"
  ([] (with-validate-session #(quot (System/currentTimeMillis) 1000)))
  ([timestamp-provider]
   (fn [handler]
     (fn [request]
       (let [{{id :token} :session conn :conn} request]
         (if (not id)
           (do
             (log/warn (str "[no_session]"))
             {:status 401})
           (let [db-session (first (db/session-by-id {:id id} {:connection conn}))]
             (if (not db-session)
               (do
                 (log/warn "[invalid_session]")
                 {:status 401 :session nil})
               (let [{user-id        :user_id
                      auth-level     :auth_level
                      last-auth-time :last_auth_time} db-session]
                 (log/info (str "[user_id=" user-id "]"))
                 (if (downgrade? auth-level last-auth-time (timestamp-provider))
                   (do
                     (log/warn "[downgrading_session]")
                     (db/set-session-auth-level! {:id id :auth_level (:basic-auth db/auth-levels)} {:connection conn})
                     (handler (assoc request :auth-level (:basic-auth db/auth-levels) :user-id user-id)))
                   (handler (assoc request :auth-level auth-level :user-id user-id))))))))))))

(s/fdef with-validate-session
        :args (s/alt :nullary (s/cat)
                     :provider (s/cat :timestamp-provider ::timestamp-provider))
        :ret ::middleware)

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

(s/fdef params-to-keywords
        :args (s/cat :handler ::ring-handler)
        :ret ::request-handler)

(defn- get-fingerprint [orig]
  "Retrieves a fingerprint for a piece of data"
  (-> orig
      (hash/sha3-256)
      (codec/bytes->b64-str)))

(s/fdef get-fingerprint
        :args (s/cat :orig string?)
        :ret string?)

(defn get-fingerprints [request]
  "Gets all fingerpritns for a request"
  (let [uname (get-fingerprint (or (-> request :params :username) "<none>"))
        ip (get-fingerprint (or (-> request :remote-addr) "127.0.0.1"))
        device (get-fingerprint (or (-> request :headers (get "device-id" (-> request :headers (get "user-agent")))) "<unknown>"))]
    {:uname  uname
     :ip     ip
     :device device}))

(s/fdef get-fingerprints
        :args (s/cat :request ::ring-request)
        :ret (s/keys :req [::uname string? ::ip string? ::device string?]))

(defn risk-filter [handler]
  "Filters out risky requests with a 429"
  (fn [request]
    (let [{conn :conn} request
          rating (db/auth-risk-rating (get-fingerprints request) {:connection conn})
          is-risky? (-> rating first :risky (not= 0))]
      (if is-risky?
        (do
          (log/warn "[risk_triggered]")
          {:status 429})
        (handler request)))))

(s/fdef risk-filter
        :args (s/cat :handler ::ring-handler)
        :ret ::request-handler)

(defn risk-logger [handler]
  "Logs an authentication attempt for risk filtering"
  (fn [request]
    (let [{conn :conn} request
          res (handler request)
          {status :status} res
          success? (if (< status 400) 1 0)]
      (when-not success? (log/warn "[unsuccessful_auth_attempt]"))
      (db/auth-log-attempt! (assoc (get-fingerprints request) :success success?) {:connection conn})
      res)))

(s/fdef risk-logger
        :args (s/cat :handler ::ring-handler)
        :ret ::request-handler)

(defn with-auth-level
  "Enforces a minimum authentication level"
  ([] (with-auth-level (:basic-auth db/auth-levels)))
  ([min-auth-level]
   (fn [handler]
     (fn [request]
       (let [{auth-level :auth-level} request]
         (if (>= (or auth-level 0) min-auth-level)
           (handler request)
           (do
             (log/warn "[insufficient_auth_level]")
             {:status 403})))))))

(s/fdef with-auth-level
        :args (s/alt :default-auth-level (s/cat)
                     :auth-level (s/cat :min-auth-level ::db/auth-level))
        :ret (s/fspec
               :args (s/cat :handler ::ring-handler)
               :ret ::request-handler))

(defn- get-op-from-request [request]
  "Gets the operation from the associated request"
  (-> request :reitit.core/match :result (get (:request-method request)) :data :op-name))

(s/fdef get-op-from-request
        :args (s/cat :request ::ring-request)
        :ret (s/alt :nil nil?
                    :op ::db/operation))

(defn can-do-operation? [handler]
  "Middleware to determine if a user has enough balance to perform an operation

  This retrieves the operation from the route definition instead of having it passed in.
  E.g.

  [\"add\" {:post {:handler (fn [_] (do (something)))
                   :op-name :addition ; the operation to charge credits for
                   :parameters {:json {:my-param any?}}}}]

  If there is not sufficient balance, it will return a 402
  "
  (fn [request]
    (let [op-name (get-op-from-request request)]
      (if (nil? op-name)
        (do
          (log/error "[no_operation_for_route]")
          {:status 500})
        (let [op-id (db/op-to-id op-name)
              {user-id :user-id conn :conn} request
              can-do? (not= 0 (or (:can_do (first (db/user-can-do-op {:id user-id :op op-id} {:connection conn}))) 0))]
          (if (not can-do?)
            (do
              (log/warn "[insufficient_credits][not_processing]")
              {:status 402})
            (handler
              (assoc request :op-name op-name :op op-id))))))))

(s/fdef can-do-operation?
        :args (s/cat :handler ::ring-handler)
        :ret ::request-handler)

(defn record-operation
  "Records an operation and charges the user.
    E.g.

  [\"add\" {:post {:handler (fn [_] (do (something)))
                   :op-name :addition ; the operation to charge credits for
                   :parameters {:json {:my-param any?}}}}]

  If the user doesn't have enough balance, it will discard the result and return a 402."
  [guid-provider]
  (fn [handler]
    (fn [request]
      (let [op-name (get-op-from-request request)
            op-id (db/op-to-id op-name)
            {user-id :user-id conn :conn} request]
        (log/info (str "[performing_op=" op-name "]"))
        (let [res (handler request)]
          (if (= 200 (:status res))
            (let [txid (guid-provider)
                  res-json (j/write-value-as-string (:body res))]
              (try
                (if (= 0 (db/record-op! {:op op-id :user user-id :txid txid :res res-json} {:connection conn}))
                  (do
                    (log/warn (str "[insufficient_credits][discarding_result][op=" op-name "]"))
                    {:status 402})
                  res)
                (catch Exception e
                  (log/warn (str "[insufficient_credits][discarding_result][op=" op-name "]"))
                  {:status 402})))
            res))))))

(s/fdef record-operation
        :args (s/cat :guid-provider ::guid-provider)
        :ret ::middleware)

(defn log-request [guid-provider]
  "Logs a request's start, end, and any errors. It will also add a tid to the request to associate the logs."
  (fn [handler]
    (fn [request]
      (let [tid (or (-> request :headers (get "tid"))
                    (guid-provider))]
        (log/info (str "[request_start][addr=" (:remote-addr request) "][path=" (:uri request) "][method=" (:request-method request) "][tid=" tid "]" ))
        (try
          (let [res (handler (assoc request :tid tid))
                {status :status} res]
            (log/info (str "[request_end][tid=" tid "][status=" status "]"))
            res)
          (catch Exception e
            (log/error (str "[request_error][tid=" tid "][err=" (-> e (.getMessage)) "][stack=" (-> e (ExceptionUtils/getStackTrace)) "]"))
            {:status 500}))))))

(s/fdef log-request
        :args (s/cat :guid-provider ::guid-provider)
        :ret ::middleware)

(defn cors [permit]
  (fn [handler]
    (fn [request]
      (let [res (if (= :options (:request-method request))
                  {:status 200}
                  (handler request))]
        (-> res
            (assoc-in [:headers "access-control-allow-origin"] permit)
            (assoc-in [:headers "access-control-allow-headers"] "*")
            (assoc-in [:headers "access-control-allow-methods"] "Cookies,device-id,origin,user-agent,accept-encoding,accept-language")
            (assoc-in [:headers "access-control-allow-credentials"] true))))))
