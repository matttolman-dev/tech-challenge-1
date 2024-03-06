(ns loanpro-interview.endpoints.ops
  (:require [clojure.spec.alpha :as s]
            [reitit.coercion.spec]
            [loanpro-interview.middleware :as m]
            [clojure.math :as math]))

(s/def ::x (s/and string? (s/* #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \.})))

(s/def ::y (s/and string? (s/* #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \.})))

(s/def :num/res (s/and string? (s/* #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \.})))

(s/def :str/res string?)

(s/def :num/body (s/keys :req-un [:num/res]))

(s/def :str/body (s/keys :req/un [:str/res]))

(s/def :xy/params
  (s/keys :req-un [::x ::y]))

(s/def :x/params
  (s/keys :req-un [::x]))

(defn add [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (-> (+ (new BigDecimal x) (new BigDecimal y)) .toString)}})

(s/fdef add
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body]))

(defn subtract [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (-> (- (new BigDecimal x) (new BigDecimal y)) .toString)}})

(s/fdef subtract
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body]))

(defn multiply [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (-> (* (new BigDecimal x) (new BigDecimal y)) .toString)}})

(s/fdef multiply
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body]))

(defn divide [{{:keys [x y]} :params}]
  {:status 200
   :body   {:res (-> (/ (new BigDecimal x) (new BigDecimal y)) .toString)}})

(s/fdef divide
        :args (s/cat :request (s/keys :req-un [:xy/params]))
        :ret (s/keys :req [:num/body]))

(defn square-root [{{:keys [x]} :params}]
  {:status 200
   :body   {:res (-> (math/sqrt (new BigDecimal x)) .toString)}})

(s/fdef square-root
        :args (s/cat :request (s/keys :req-un [:x/params]))
        :ret (s/keys :req [:num/body]))

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
   ; Using string? so we can avoid IEEE-754 rounding issues
   ["add" {:post {:parameters {:json {:x string?, :y string?}}
                  :responses  {200 {:body {:res string?}}
                               402 {}}
                  :handler    add
                  :op-name :addition}}]
   ["subtract" {:post {:parameters {:json {:x string?, :y string?}}
                       :responses  {200 {:body {:res string?}}
                                    402 {}}
                       :handler    subtract
                       :op-name    :subtraction}}]
   ["multiply" {:post {:parameters {:json {:x string?, :y string?}}
                       :responses  {200 {:body {:res string?}}
                                    402 {}}
                       :handler    multiply
                       :op-name    :multiplication}}]
   ["divide" {:post {:parameters {:json {:x string?, :y string?}}
                     :responses  {200 {:body {:res string?}}
                                  402 {}}
                     :handler    divide
                     :op-name    :division}}]
   ["square-root" {:post {:parameters {:json {:x string?}}
                          :responses  {200 {:body {:res string?}}
                                       402 {}}
                          :handler    square-root
                          :op-name    :square-root}}]
   ["random-str" {:post {:responses {200 {:body {:res string?}}
                                     402 {}}
                         :handler   (random-str http-get)
                         :op-name   :random-string}}]])
