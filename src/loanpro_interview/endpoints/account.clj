(ns loanpro-interview.endpoints.account
  (:require [clojure.math :as math]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
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

(defn- history-select [query filter]
  (let [filter (str/trim (or filter ""))]
    (if (not-empty filter)
      {:query (assoc query :filter (str "%" (str/replace (str/lower-case filter) #"%" "") "%"))
       :bounds db/search-bounds
       :history db/search-history}
      {:query   query
       :bounds  db/history-bounds
       :history db/history})))

(defn history [req]
  (let [{conn :conn user-id :user-id {page :page page-size :num filter :filter} :params} req
        page-size (parse-long (or page-size "10"))
        page (parse-long (or page "0"))
        query {:user user-id :page page :page_size page-size}]
    (if (< (:page query) 0)
      {:status 400
       :body   {:page "invalid"}}
      (if (< (:page_size query) 1)
        {:status 400
         :body   {:page-size "invalid"}}
        ; If there is a filter, do a search + scan of the database to paginate O(N^2)
        (let [{query   :query
               bounds  :bounds
               history :history} (history-select query filter)
              search-bounds (first (bounds {:user user-id :filter filter} {:connection conn}))
              history-plus-1 (history query {:connection conn})
              history (take page-size history-plus-1)
              has-next (not (empty? (drop page-size history-plus-1)))]
          (clojure.pprint/pprint [search-bounds history-plus-1 page-size query])
          {:status 200
           :body   {:more    has-next
                    :page    page
                    :pages   (int (math/ceil (/ (or (:total search-bounds) 0) page-size)))
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
           :parameters {:query {:page number? :num number? :filter string?}}
           :handler    history}}]])
