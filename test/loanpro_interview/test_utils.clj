(ns loanpro-interview.test-utils
  (:require
    [buddy.hashers :as hashers]
    [clojure.java.io :refer [delete-file]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as stest]
    [ksuid.core :as ksuid]
    [ragtime.jdbc :as r]
    [loanpro-interview.db :as db]
    [ragtime.repl :as rr]))

(s/check-asserts true)

(stest/instrument)

(defn db-conf [db-prefix]
  ; Always cleanup before creating a new DB
  (let [db-name (str "test/" db-prefix "-test.db")
        conf {:dbtype "sqlite"
              :dbname db-name}]
    (delete-file db-name true)
    (rr/migrate {:datastore  (r/sql-database conf)
                 :migrations (r/load-resources "migrations")})
    conf))

(defn create-user-app [test-app]
  (:session (test-app {:request-method :post
                       :uri            "/api/v1/auth/signup"
                       :headers        {"content-type" "application/json"}
                       :params         {"username"         "test@example.com"
                                        "password"         "Password1!"
                                        "password-confirm" "Password1!"}})))

(defn create-user [conn prefix]
  (let [id (ksuid/to-string (ksuid/new-random))
        username (str prefix "-test@example.com")]
    (db/create-user! {:id       id
                      :username username
                      :password (hashers/derive "Password1!")}
                     {:connection conn})
    {:id id
     :username username}))
