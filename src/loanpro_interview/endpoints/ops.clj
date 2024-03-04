(ns loanpro-interview.endpoints.ops
  (:require [clojure.spec.alpha :as s]
            [reitit.coercion.spec]
            [loanpro-interview.middleware :as m]
            [clojure.math :as math]))

(s/def ::x number?)

(s/def ::y number?)

(s/def :num/res number?)

(s/def :str/res string?)

(s/def :num/body (s/keys :req-un [:num/res]))

(s/def :str/body (s/keys :req/un [:str/res]))

(s/def :xy/params
  (s/keys :req-un [::x ::y]))

(s/def :x/params
  (s/keys :req-un [::x]))

(defn add [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (+ x y)}})

(s/fdef add
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body])
        :fn (s/and #(= (:ret %) (+ (-> % :args :params :x)
                                   (-> % :args :params :y)))))

(defn subtract [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (- x y)}})

(s/fdef subtract
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body])
        :fn (s/and #(= (:ret %) (- (-> % :args :params :x)
                                   (-> % :args :params :y)))))

(defn multiply [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (* x y)}})

(s/fdef multiply
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body])
        :fn (s/and #(= (:ret %) (* (-> % :args :params :x)
                                   (-> % :args :params :y)))))

(defn divide [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (/ x y)}})

(s/fdef divide
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body])
        :fn (s/and #(= (:ret %) (/ (-> % :args :params :x)
                                   (-> % :args :params :y)))))

(defn square-root [{{:keys [x]} :params}]
  {:status 200
   :body   {:res (math/sqrt x)}})

(s/fdef square-root
        :args (s/cat :request (s/keys :req-un [:x/params]))
        :ret (s/keys :req [:num/body])
        :fn (s/and #(= (:ret %) (math/sqrt (-> % :args :params :x)))))

(defn random-str [http-get]
  (fn [_]
    (let [rnd-res @(http-get "https://www.random.org/strings/?num=1&len=10&digits=on&upperalpha=on&loweralpha=on&format=plain&rnd=new")
          {status :status body :body} rnd-res]
      (if (not= status 200)
        {:status status}
        {:status 200
         :body   {:res (clojure.string/trim body)}}))))

(s/fdef random-str
        :args (s/fspec :args (s/cat) :ret any?)
        :ret ::m/request-handler)

(defn routes
  [guid-provider http-get]
  ["ops/"
   {:middleware [(m/with-validate-session)
                 m/can-do-operation?
                 (m/record-operation guid-provider)]}
   ["add" {:post {:parameters {:json {:x number?, :y number?}}
                  :responses  {200 {:body {:res number?}}
                               402 {}}
                  :handler    add
                  :op-name :addition}}]
   ["subtract" {:post {:parameters {:json {:x number?, :y number?}}
                       :responses  {200 {:body {:res number?}}
                                    402 {}}
                       :handler    subtract
                       :op-name    :subtraction}}]
   ["multiply" {:post {:parameters {:json {:x number?, :y number?}}
                       :responses  {200 {:body {:res number?}}
                                    402 {}}
                       :handler    multiply
                       :op-name    :multiplication}}]
   ["divide" {:post {:parameters {:json {:x number?, :y number?}}
                     :responses  {200 {:body {:res number?}}
                                  402 {}}
                     :handler    divide
                     :op-name    :division}}]
   ["square-root" {:post {:parameters {:json {:x number?}}
                          :responses  {200 {:body {:res number?}}
                                       402 {}}
                          :handler    square-root
                          :op-name    :square-root}}]
   ["random-str" {:post {:responses {200 {:body {:res string?}}
                                     402 {}}
                         :handler   (random-str http-get)
                         :op-name   :random-string}}]])
