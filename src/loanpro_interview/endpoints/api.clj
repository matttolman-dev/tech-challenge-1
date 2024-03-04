(ns loanpro-interview.endpoints.api
  (:require [loanpro-interview.db :as db]
            [loanpro-interview.endpoints.ops :as ops]
            [loanpro-interview.endpoints.auth :as auth]
            [loanpro-interview.endpoints.account :as account]
            [loanpro-interview.middleware :as m]
            [reitit.ring.coercion :as rcc]
            [ksuid.core :as ksuid]
            [org.httpkit.client :as http]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.middleware.json :as rj]))

(defn routes
  ([] (routes db/get-connection))
  ([db-conn-provider] (routes db-conn-provider #(ksuid/to-string (ksuid/new-random))))
  ([db-conn-provider guid-provider] (routes db-conn-provider guid-provider http/get))
  ([db-conn-provider guid-provider http-get]
   ["/"
    {:middleware [rj/wrap-json-params
                  m/params-to-keywords
                  muuntaja/format-response-middleware
                  (m/with-connect-db db-conn-provider)
                  rcc/coerce-response-middleware]}
    ["v1/"
     (ops/routes guid-provider http-get)
     (auth/routes guid-provider)
     (account/routes guid-provider)]]))
