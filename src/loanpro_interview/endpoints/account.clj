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

(defn history [req]
  (let [{conn :conn user-id :user-id {cursor :cursor page-size :num filter :filter} :params} req
        page-size (parse-long (or page-size "10"))
        cursor (parse-long (or cursor "0"))
        query {:user user-id :cursor cursor :page_size page-size}]
    (if (< (:cursor query) 0)
      {:status 400
       :body   {:cursor "invalid"}}
      (if (< (:page_size query) 1)
        {:status 400
         :body   {:page-size "invalid"}}
        (if (and filter (not-empty filter))
          ; If there is a filter, do a search + scan of the database to paginate O(N^2)
          (let [filter (str "%" (str/lower-case filter) "%")
                query (assoc query :filter filter)
                search-bounds (db/search-bounds {:user user-id :filter filter} {:connection conn})
                history-plus-1 (db/search-history query {:connection conn})
                history (take page-size history-plus-1)
                has-next (not (empty? (drop page-size history-plus-1)))
                next-cursor (inc cursor)]
            (clojure.pprint/pprint [search-bounds history-plus-1 page-size query])
            {:status 200
             :body   {:more    has-next
                      :start   (or (:start search-bounds) 0)
                      :end     (or (:end search-bounds) 0)
                      :cursor  next-cursor
                      :page    cursor
                      :pages   (math/ceil (/ (or (:total search-bounds) 0) page-size))
                      :history (map (fn [e]
                                      (assoc e :response (j/read-value (:response e)))) history)}})

          ; If there is no filter, use a cursor directly (less scanning the database) O(N)
          (let [history-bounds (db/history-bounds {:user user-id} {:connection conn})
                history (db/history query {:connection conn})
                cursors (cons 0 (map #(:cursor %) history))
                max-history (apply max cursors)
                min-history (apply min cursors)
                {end-history :end start-history :start} (first history-bounds)]
            {:status 200
             :body   {:more    (< max-history end-history)
                      :start   start-history
                      :end     end-history
                      :cursor  max-history
                      :page    (quot (+ (max min-history 1) (- start-history) 1) page-size)
                      :pages   (quot (+ end-history (- start-history) 1) page-size)
                      :history (map (fn [e]
                                      (assoc e :response (j/read-value (:response e)))) history)}}))))))

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
           :parameters {:query {:cursor number? :num number? :filter string?}}
           :handler    history}}]])
