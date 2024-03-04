(ns loanpro-interview.db
  (:require [omniconf.core :as cfg]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr]
            [loanpro-interview.conf :as conf]
            [yesql.core :refer [defqueries]]))

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

(def auth-levels
  {:inactive-auth 10
   :basic-auth    20
   :secure-auth   30})

(def operations
  {:addition 1
   :subtraction 2
   :multiplication 3
   :division 4
   :square-root 5
   :random-string 6})

(defn is-operation? [op-name]
  (contains? operations op-name))

(defn op-to-id [op-name]
  {:pre [(is-operation? op-name)]}
  (get operations op-name))

; DB queries are stored in the resources file
; This allows for better IDE completion as well as tracking what queries are being ran
; It also allows for DBAs to more easily audit and optimize queries

(defqueries "queries/session.sql")
(defqueries "queries/user.sql")
(defqueries "queries/risk.sql")

(defn get-connection []
  (db-conf))

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
