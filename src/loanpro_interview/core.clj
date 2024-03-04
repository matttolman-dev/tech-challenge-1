(ns loanpro-interview.core
  (:require [loanpro-interview.conf :as conf]
            [loanpro-interview.db :as db]
            [loanpro-interview.endpoints.api :as api]
            [chime.core :refer [periodic-seq]]
            [muuntaja.core :as m]
            [org.httpkit.server :as hk-server]
            [omniconf.core :as cfg]
            [reitit.ring :as ring]
            [ring.middleware.session]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.session.cookie]
            [clojure.tools.logging :as log])
  (:import (java.time ZoneOffset)))

; Defining as a method so that changes to routes are reloaded in the REPL on server restart
(defn app [] (ring/ring-handler
               (ring/router
                 ; Because reitit works with data, we can simply nest routes from other files to get our full router
                 ["/"
                  (api/routes)]
                 ; Global middleware
                 {:data {:coercion   reitit.coercion.spec/coercion
                         :muuntaja   m/instance
                         :middleware [parameters/parameters-middleware]}})))

; Defining this for REPL interactions (allows stopping/starting server)
(defonce server (atom nil))

; Defining this for REPL interactions (allows stopping/starting server)
(defn stop-server []
  (let [s @server]
    (when-not (nil? s)
      (log/info "Stopping server")
      (s))
    (reset! server nil)))

; Defining this for REPL interactions (allows stopping/starting server)
(defn start-server [& args]
  (stop-server)
  (log/info "Starting server")
  (apply conf/load-conf-once args)
  (reset! server (hk-server/run-server
                   (ring.middleware.session/wrap-session (app) {:store (ring.middleware.session.cookie/cookie-store
                                                                         {:key (byte-array (map byte (cfg/get :session :key)))})})
                   {:port (cfg/get :port)})))

(defn -main [& args]
  (apply start-server args)
  (->> (periodic-seq (java.time.Instant/now) (java.time.Duration/ofHours 1))
       (filter (fn [_]
                 (let [time (.atZone (java.time.Instant/now) (ZoneOffset/UTC))
                       seconds (.getSecond time)
                       minutes (.getMinute time)]
                   (and (zero? seconds) (zero? minutes)))))
       (map (fn [_]
              (log/info "Cleaning up stale sessions")
              (db/cleanup-sessions! {} {:connection (db/get-connection)})))))
