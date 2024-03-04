(ns loanpro-interview.endpoints.ops
  (:require [reitit.coercion.spec]
            [loanpro-interview.middleware :as m]
            [clojure.math :as math]
            [org.httpkit.client :as http]))

(defn routes
  []
  ["ops/"
   {:middleware [(m/with-validate-session)
                 m/can-do-operation?
                 (m/record-operation)]}
   ["add" {:post {:parameters {:json {:x number?, :y number?}}
                  :responses  {200 {:body {:res number?}}
                               402 {}}
                  :handler    (fn [{{:keys [x y]} :params}]
                                {:status 200
                                 :body   {:res (+ x y)}})
                  :op-name    :addition}}]
   ["subtract" {:post {:parameters {:json {:x number?, :y number?}}
                       :responses  {200 {:body {:res number?}}
                                    402 {}}
                       :handler    (fn [{{:keys [x y]} :params}]
                                     {:status 200
                                      :body   {:res (- x y)}})
                       :op-name    :subtraction}}]
   ["multiply" {:post {:parameters {:json {:x number?, :y number?}}
                       :responses  {200 {:body {:res number?}}
                                    402 {}}
                       :handler    (fn [{{:keys [x y]} :params}]
                                     {:status 200
                                      :body   {:res (* x y)}})
                       :op-name    :multiplication}}]
   ["divide" {:post {:parameters {:json {:x number?, :y number?}}
                     :responses  {200 {:body {:res number?}}
                                  402 {}}
                     :handler    (fn [{{:keys [x y]} :params}]
                                   {:status 200
                                    :body   {:res (/ x y)}})
                     :op-name    :division}}]
   ["square-root" {:post {:parameters {:json {:x number?}}
                          :responses  {200 {:body {:res number?}}
                                       402 {}}
                          :handler    (fn [{{:keys [x y]} :params}]
                                        {:status 200
                                         :body   {:res (math/sqrt x)}})
                          :op-name    :square-root}}]
   ["random-str" {:post {:responses {200 {:body {:res string?}}
                                     402 {}}
                         :handler   (fn []
                                      (let [rnd-res @(http/get "https://www.random.org/strings/?num=1&len=10&digits=on&upperalpha=on&loweralpha=on&format=plain&rnd=new")
                                            {status :status body :body} rnd-res]
                                        (if (not= status 200)
                                          {:status status}
                                          {:status 200
                                           :body   {:res body}})))
                         :op-name   :random-string}}]
   ])
