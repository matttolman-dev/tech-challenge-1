(ns loanpro-interview.endpoints.api
  (:require [loanpro-interview.db :as db]
            [loanpro-interview.endpoints.ops :as ops]
            [loanpro-interview.endpoints.auth :as auth]
            [loanpro-interview.middleware :as m]
            [reitit.ring.coercion :as rcc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.middleware.json :as rj]))

(defn routes
  ([] (routes db/get-connection))
  ([db-conn-provider] (routes db-conn-provider #(java.time.Instant/now)))
  ([db-conn-provider time-provider]
   ["api/"
    {:middleware [rj/wrap-json-params
                  rcc/coerce-request-middleware
                  muuntaja/format-response-middleware
                  m/params-to-keywords
                  (m/with-connect-db db-conn-provider)
                  rcc/coerce-response-middleware
                  m/log-request]}
    ["v1/"
     (ops/routes)
     (auth/routes time-provider)]]))
