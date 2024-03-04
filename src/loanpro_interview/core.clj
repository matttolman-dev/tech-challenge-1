(ns loanpro-interview.core
  (:require [loanpro-interview.conf :as conf]
            [loanpro-interview.api :as api]
            [muuntaja.core :as m]
            [org.httpkit.server :as hk-server]
            [omniconf.core :as cfg]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rcc]
            [ring.middleware.session]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.session.cookie]
            [reitit.ring.middleware.muuntaja :as muuntaja]))

; Defining as a method so that changes to routes are reloaded in the REPL on server restart
(defn app [] (ring/ring-handler
               (ring/router
                 ; Because reitit works with data, we can simply nest routes from other files to get our full router
                 ["/"
              api/routes]
                 ; Any session
                 {:data {:coercion   reitit.coercion.spec/coercion
                         :muuntaja   m/instance
                         :middleware [parameters/parameters-middleware
                                      rcc/coerce-request-middleware
                                      muuntaja/format-response-middleware
                                      rcc/coerce-response-middleware]}})))

; Defining this for REPL interactions (allows stopping/starting server)
(defonce server (atom nil))

; Defining this for REPL interactions (allows stopping/starting server)
(defn stop-server []
  (let [s @server]
    (if (not (nil? s))
      (s))
    (reset! server nil)))

; Defining this for REPL interactions (allows stopping/starting server)
(defn start-server [& args]
  (stop-server)
  (apply conf/load-conf-once args)
  (reset! server (hk-server/run-server
                   (ring.middleware.session/wrap-session (app) {:store (ring.middleware.session.cookie/cookie-store
                                                                       {:key (byte-array (map byte (cfg/get :session :key)))})})
                   {:port (cfg/get :port)})))

(defn -main [& args]
  (apply start-server args))
