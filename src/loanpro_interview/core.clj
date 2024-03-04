(ns loanpro-interview.core
  (:require [loanpro-interview.conf :as conf]
            [loanpro-interview.db :as db]
            [loanpro-interview.endpoints.api :as api]
            [muuntaja.core :as m]
            [org.httpkit.server :as hk-server]
            [omniconf.core :as cfg]
            [reitit.ring :as ring]
            [ring.middleware.session]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.session.cookie]
            [clojure.tools.logging :as log])
  (:import (java.util.concurrent Executors TimeUnit)))

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
                                0 5 TimeUnit/SECONDS))))

(defn -main [& args]
  (apply start-server args)
  (start-cleanup))
