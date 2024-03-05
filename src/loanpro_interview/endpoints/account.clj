(ns loanpro-interview.endpoints.account
  (:require [clojure.tools.logging :as log]
            [jsonista.core :as j]
            [loanpro-interview.middleware :as m]
            [loanpro-interview.db :as db]))

(defn get-balance [{user-id :user-id conn :conn}]
  {:status 200
   :body   {:balance (:balance (first (db/user-balance {:id user-id} {:connection conn})))}})

(defn add-balance [guid-provider]
  (fn [{user-id :user-id conn :conn {amount :amount} :params}]
    (if (or (not (number? amount)) (<= amount 0))
      (do
        (log/warn (str "[invalid_amount][amount=" amount "]"))
        {:status 400
         :body   {:amount ["invalid amount"]}})
      (let [txid (guid-provider)]
        (db/add-balance! {:txid txid :amount amount :user user-id} {:connection conn})
        {:status 200
         :body   {:balance (:balance (first (db/user-balance {:id user-id} {:connection conn})))}}))))

(defn history [req]
  (let [{conn :conn user-id :user-id {cursor :cursor page-size :num} :params} req
        query {:user user-id :cursor (parse-long (or cursor "0")) :page_size (parse-long (or page-size "1"))}]
    (if (< (:cursor query) 0)
      {:status 400
       :body   {:cursor "invalid"}}
      (if (< (:page_size query) 1)
        {:status 400
         :body   {:page-size "invalid"}}
        (let [history-end (db/history-end {:user user-id} {:connection conn})
              history (db/history query {:connection conn})
              max-history (apply max (cons 0 (map #(:cursor %) history)))
              end-history (or (-> history-end first :id) 0)]
          {:status 200
           :body   {:more    (< max-history end-history)
                    :cursor  max-history
                    :history (map (fn [e]
                                    (assoc e :response (j/read-value (:response e)))) history)}})))))

(defn routes [guid-provider]
  ["account/"
   {:middleware [(m/with-validate-session)]}
   ["balance"
    {:get {:responses {200 {:body {:balance number?}}}
           :handler   get-balance}
     :put {:parameters {:json {:amount number?}}
           :responses  {200 {:body {:balance number?}}
                        400 {:body map?}}
           :handler    (add-balance guid-provider)}}]
   ["history"
    {:get {:responses  {200 {:body map?}
                        400 {:body map?}}
           :parameters {:query {:cursor number? :num number?}}
           :handler    history}}]])
