(ns loanpro-interview.endpoints.account
  (:require [clojure.tools.logging :as log]
            [loanpro-interview.middleware :as m]
            [loanpro-interview.db :as db]))

(defn routes [guid-provider]
  ["account/"
   {:middleware [(m/with-validate-session)]}
   ["balance"
    {:get {:responses {200 {:body {:balance number?}}}
           :handler (fn [{user-id :user-id conn :conn}]
                      {:status 200
                       :body   {:balance (:balance (first (db/user-balance {:id user-id} {:connection conn})))}})}
     :put {:parameters {:json {:amount number?}}
           :responses  {200 {:body {:balance number?}}
                        400 {:body map?}}
           :handler    (fn [{user-id :user-id conn :conn {amount :amount} :params}]
                         (if (<= amount 0)
                           (do
                             (log/warn (str "[invalid_amount][amount=" amount "]"))
                             {:status 400
                              :body   {:amount ["invalid amount"]}})
                           (let [txid (guid-provider)]
                             (db/add-balance! {:txid txid :amount amount :user user-id} {:connection conn})
                             {:status 200
                              :body   {:balance (:balance (first (db/user-balance {:id user-id} {:connection conn})))}})))}}]])