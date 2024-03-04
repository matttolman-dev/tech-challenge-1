(ns loanpro-interview.endpoints.api
  (:require [loanpro-interview.db :as db]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rcc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [loanpro-interview.middleware :as m]
            [ring.middleware.json :as rj]))

(defn routes
  ([] (routes db/get-connection))
  ([db-conn-provider]
   ["api/"
    ; This middleware applies to all nested API routes
    {:middleware [rj/wrap-json-params
                  rcc/coerce-request-middleware
                  muuntaja/format-response-middleware
                  m/params-to-keywords
                  (m/with-connect-db db-conn-provider)
                  (m/with-validate-session)
                  rcc/coerce-response-middleware]}
    ["v1/"
     ["ops/"
      {:middleware [m/can-do-operation?
                    (m/log-operation)]}
      ["add" {:post {:parameters {:json {:x int?, :y int?}}
                     :responses  {200 {:body {:total int?}}
                                  402 {}}
                     :handler    (fn [{{:keys [x y]} :params}]
                                   {:status 200
                                    :body   {:total (+ x y)}})
                     :op-name    :addition}}]]]]))
