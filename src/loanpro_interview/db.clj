(ns loanpro-interview.db
  (:require [next.jdbc :as jdbc]
            [omniconf.core :as cfg]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr]
            [loanpro-interview.conf :as conf]))

(defn- db-conf []
  (let [raw-conf {:dbtype (cfg/get :db :type)
                  :dbname (cfg/get :db :name)
                  :host (cfg/get :db :host)
                  :user (cfg/get :db :user)
                  :password (cfg/get :db :pwd)
                  :port (cfg/get :db :port)}]
    ; Only keep the keys that were defined
    (into {}
          (filter #(not (nil? (second %))))
          raw-conf)))

(def ^:private db (db-conf))

(def ds (jdbc/get-datasource db))

; The migrate function should not be ran on startup in a real environment
; (we don't want multiple servers migrating at once, or doing a migration
;  on a restart)
; Instead, it'd be part of the CI/CD deployment pipeline
; Since I don't have a full CI/CD deployment pipeline ready, I'm getting
;  around it by using leiningen commands

(defn- migrate-conf []
  "Gets configuration for the database migrations"
  {:datastore  (r/sql-database db)
   :migrations (r/load-resources (cfg/get :migrations))})

; Used by leinengen for DB forward migration
(defn migrate []
  "Migrate the database to the latest schema version"
  (conf/load-conf-once)
  (rr/migrate (migrate-conf)))

; Used by leinengen for a DB rollback
(defn rollback []
  "Rollback the database a single migration"
  (conf/load-conf-once)
  (rr/rollback (migrate-conf)))

; Used by leinengen for a DB reset
(defn reset []
  "Reset a database to a clean, initial state"
  (conf/load-conf-once)
  ; rollback all change sets detected
  (rr/rollback (migrate-conf) (-> (:migrations (migrate-conf))
                                  (count)))
  ; run all of our migrations
  (migrate))
