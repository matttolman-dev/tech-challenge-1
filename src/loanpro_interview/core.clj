(ns loanpro-interview.core
  (:require [loanpro-interview.conf :as conf]
            [loanpro-interview.db :as db]
            [loanpro-interview.endpoints.api :as api]
            [loanpro-interview.middleware :as m]
            [muuntaja.core :as mc]
            [org.httpkit.server :as hk-server]
            [omniconf.core :as cfg]
            [reitit.ring :as ring]
            [ring.middleware.session]
            [org.httpkit.client :as http]
            [ksuid.core :as ksuid]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.session.cookie]
            [clojure.tools.logging :as log])
  (:import (java.util.concurrent Executors TimeUnit)))

; Defining as a method so that changes to routes are reloaded in the REPL on server restart
(defn app
  ([] (app db/get-connection))
  ([db-conn-provider] (app db-conn-provider #(ksuid/to-string (ksuid/new-random))))
  ([db-conn-provider guid-provider] (app db-conn-provider guid-provider http/get))
  ([db-conn-provider guid-provider http-get]
   (ring/ring-handler
     (ring/router
       [""
        ["" {:get {:handler (fn [_] {:status 302 :headers {"location" "/ui/index.html"}})}}]
        ["/" {:get {:handler (fn [_] {:status 302 :headers {"location" "/ui/index.html"}})}}]
        ; Because reitit works with data, we can simply nest routes from other files to get our full router
        ["/api"
         (api/routes db-conn-provider guid-provider http-get)]
        ["/ui/*" (ring/create-resource-handler)]]
       ; Global middleware
       {:data {:coercion   reitit.coercion.spec/coercion
               :muuntaja   mc/instance
               :middleware [parameters/parameters-middleware
                            (m/log-request guid-provider)]}}))))

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

; Executor for background processes
(defonce background-executor (Executors/newScheduledThreadPool 1))

; Atom for cleanup thread
(defonce cleanup-process (atom nil))

; Used for REPL to stop cleanup process
(defn stop-cleanup []
  (when-let [process @cleanup-process]
    (.cancel process false)))

; Process to clean up stale sessions from the database
(defn start-cleanup []
  (stop-cleanup)
  (reset! cleanup-process (-> background-executor
                              (.scheduleAtFixedRate
                                #(do
                                   (log/debug "Cleaning up stale sessions")
                                   (log/info (str "Cleaned up "
                                                  (db/cleanup-sessions! {} {:connection (db/get-connection)})
                                                  " stale sessions")))
                                0 1 TimeUnit/HOURS))))

(defn -main [& args]
  (apply start-server args)
  (start-cleanup))
