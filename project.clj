(defproject loanpro-interview "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]

                 ; This is for the HTTP server
                 ; I use this since it's lighter to work with than jetty
                 ; For production, I usually add this behind some sort
                 ;  of reverse proxy/loadbalancer
                 ;  (e.g. AWS Loadbalancer, nginx, Apache, etc.)
                 [http-kit/http-kit "2.7.0"]

                 ; Get default ring middleware
                 [ring/ring "1.11.0"]

                 ; Server router
                 ; I like this one since it's data-based
                 ; The data-based nature makes it easier to
                 ;  test routes without having to spin up a serve
                 ;  (meaning we can have unit tests for our router)
                 ; Additionally, it makes it easier to split routes between
                 ;  different files
                 ; And it has a tree-based nature for middleware
                 ; This basically means that middleware is applied as the
                 ;  router traverses the tree, so middleware on parent nodes
                 ;  will be applied below
                 ; That way if you want to require authentication for all /api/*
                 ;  requests, you just add the authentication middleware to the
                 ;  api node, and then all routes will be authenticated
                 [metosin/reitit "0.7.0-alpha7"]

                 ; Database connector
                 [org.clojure/java.jdbc "0.7.12"]

                 ; Query management
                 [yesql "0.5.3"]

                 ; JSON parsing
                 [ring/ring-json "0.5.1"]

                 ; Using SQLite for this sample project
                 ; Makes setting up integration tests easier
                 ;  and makes deploying easier as well
                 ; For this project, I'm going to stick with basic SQL queries
                 ;  (no collation, extensions, etc.)
                 ;
                 ; For production of a full application, I'd probably use PostgreSQL
                 ;   (or the AWS Aurora flavor of PostgreSQL)
                 [org.xerial/sqlite-jdbc "3.45.1.0"]

                 ; KSUID (id generator)
                 [com.github.toanphan19/ksuid "0.2.0"]

                 ; Data validation library
                 [metis "0.3.3"]

                 ; Crypto
                 [buddy/buddy-core "1.11.423"]
                 [buddy/buddy-hashers "2.0.167"]

                 ; Database migrations library
                 ; Up and down migrations can be provided
                 [ragtime "0.8.0"]

                 ; For configuration management
                 [com.grammarly/omniconf "0.4.3"]

                 ; Useful core extensions
                 [org.apache.commons/commons-lang3 "3.14.0"]

                 ; JSON encoder/decoder
                 [metosin/jsonista "0.3.8"]

                 ; For response formatting
                 [metosin/muuntaja "0.6.8"]

                 ; Logging libraries
                 [ch.qos.logback/logback-classic "1.5.1"]
                 [org.slf4j/osgi-over-slf4j "2.0.12"]
                 [org.slf4j/log4j-over-slf4j "2.0.12"]
                 [org.slf4j/jcl-over-slf4j "2.0.12"]
                 [org.slf4j/jul-to-slf4j "2.0.12"]
                 [org.slf4j/slf4j-api "2.0.12"]
                 [org.clojure/tools.logging "1.3.0"]]
  :repl-options {:init-ns loanpro-interview.core}
  :aliases {"migrate" ["run" "-m" "loanpro-interview.db/migrate"]
            "rollback" ["run" "-m" "loanpro-interview.db/rollback"]
            "db-reset" ["run" "-m" "loanpro-interview.db/reset"]})
