(ns loanpro-interview.db
  (:require [next.jdbc :as jdbc]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr]))

(def db {:dbtype "sqlite" :dbname "app.db"})

(def ds (jdbc/get-datasource db))

; The migrate function should not be ran on startup in a real environment
; (we don't want multiple servers migrating at once, or doing a migration
;  on a restart)
; Instead, it'd be part of the CI/CD deployment pipeline
; Since I don't have a full CI/CD deployment pipeline ready, I'm getting
;  around it by using leiningen commands

(defn migrate-conf []
  {:datastore  (r/sql-database db)
   :migrations (r/load-resources "migrations")})

(defn migrate []
  (rr/migrate (migrate-conf)))

(defn rollback []
  (rr/rollback (migrate-conf)))
