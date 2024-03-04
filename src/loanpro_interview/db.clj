(ns loanpro-interview.db
  (:require [omniconf.core :as cfg]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr]
            [loanpro-interview.conf :as conf]
            [clojure.spec.alpha :as s]
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

(s/def ::conn (s/keys :dbtype string?
                      :dbname string?
                      :host (s/or string? nil?)
                      :user (s/or string? nil?)
                      :password (s/or string? nil?)
                      :port (s/or int? nil?)))

(s/def ::conn-provider
  (s/fspec
    :args (s/cat)
    :ret ::conn))

(def auth-levels
  "Possible authentication levels for a user. Allows scoping actions based on auth.

  These define basic ranges of authentication, not absolute values. It is possible to have \"in-between\" levels.
  All that is required is that a session have at least the auth level required for a permission.
  "
  {:inactive-auth 10
   :basic-auth    20
   :secure-auth   30})

(s/assert (s/map-of keyword? int?) auth-levels)

(defn is-auth-level? [level]
  "Determines if an auth level is valid"
  (and (>= level 0) (<= level 30)))

(s/fdef is-auth-level?
        :args (s/cat :level pos-int?)
        :ret boolean?)

(s/def ::auth-level is-auth-level?)

(def operations
  "Possible operations for cost calculations. Costs are stored in the database"
  {:addition 1
   :subtraction 2
   :multiplication 3
   :division 4
   :square-root 5
   :random-string 6})

(s/assert (s/map-of keyword? int?) operations)

(defn is-operation? [op-name]
  "Returns whetehr a keyword is a valid operation name"
  (contains? operations op-name))

(s/fdef is-operation?
        :args (s/cat :op-name keyword?)
        :ret boolean?)

(s/def ::operation is-operation?)

(defn op-to-id [op-name]
  (get operations op-name))

(s/fdef op-to-id
        :args (s/cat :op-name ::operation)
        :ret int?)

; DB queries are stored in the resources file
; This allows for better IDE completion as well as tracking what queries are being ran
; It also allows for DBAs to more easily audit and optimize queries

(defqueries "queries/session.sql")
(defqueries "queries/user.sql")
(defqueries "queries/risk.sql")

(defn get-connection []
  "Retrieves a database connection for use in queries"
  (db-conf))

; The migrate function should not be ran on startup in a real environment
; (we don't want multiple servers migrating at once, or doing a migration
;  on a restart)
; Instead, it'd be part of the CI/CD deployment pipeline
; Since I don't have a full CI/CD deployment pipeline ready, I'm getting
;  around it by using leiningen commands

(defn- migrate-conf []
  "Gets configuration for the database migrations"
  {:datastore  (r/sql-database (db-conf))
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
