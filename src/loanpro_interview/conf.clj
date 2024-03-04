(ns loanpro-interview.conf
  (:require [omniconf.core :as cfg]
            [clojure.java.io :as io]))

(cfg/define
  {:port       {:description "HTTP Port"
                :type        :number
                :default     8080}
   :db         {:nested {:user {:type        :string
                                :required    false
                                :description "Database user for auth"
                                ; This prevents the value from being printed by Omniconf
                                :secret      true}
                         :pwd  {:type        :string
                                :required    false
                                :description "Database password for auth"
                                :secret      true}
                         :type {:type        :string
                                :default     "sqlite"
                                :description "Type of database being connected to"}
                         :host {:type        :string
                                :required    false
                                :description "Host to connect to"}
                         :port {:type        :number
                                :required    false
                                :description "Port to connect to"}
                         :name {:type        :string
                                :default     "app.db"
                                :description "Name for the database"}}}
   :migrations {:type        :string
                :default     "migrations"
                :description "Resources folder for database migrations"}
   :session    {:nested {:key {:type     :string
                               :secret   true
                               :required true}}}
   :users       {:nested {:pwd-key {:type :string
                                    :secret true
                                    :requred true}}}})

(def ^:private loaded (atom false))

; Useful for repl if changing the conf file
(defn- load-conf [& args]
  "Use to reload configuration"
  (when-let [def-conf (io/resource "config.edn")]
    (cfg/populate-from-file def-conf))
  (cfg/populate-from-cmd args)
  (cfg/populate-from-properties)
  (cfg/populate-from-env)
  (cfg/populate-from-cmd args)
  (when-let [conf (cfg/get :conf)]
    (cfg/populate-from-file conf))
  (cfg/verify))

; Used to make sure we don't overwrite args
(defn load-conf-once [& args]
  "Use to ensure that configuration is loaded. Does not reload configuration"
  (if (compare-and-set! loaded false true)
    (try
      (apply load-conf args)
      (catch Exception e
        ; If something goes wrong, release the "loaded" lock so that we can retry later
        (reset! loaded false)
        (throw e)))))
