(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.java.jdbc :as j]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.test :as test]
            [clojure.stacktrace :refer (e)]
            [pe-core-utils.core :as ucore]
            [pe-fp-core.validation :as val]
            [pe-user-core.core :as usercore]
            [pe-fp-core.core :as core]
            [pe-jdbc-utils.core :as jcore]
            [pe-fp-core.test-utils :refer [db-spec-without-db
                                           db-spec-fn]]
            [pe-user-core.ddl :as uddl]
            [pe-fp-core.ddl :as fpddl]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]))

(def dev-db-name "dev")

(def db-spec-dev (db-spec-fn dev-db-name))

(defn refresh-dev-db
  []
  ;; Database setup
  (jcore/drop-database db-spec-without-db dev-db-name)
  (jcore/create-database db-spec-without-db dev-db-name)

  ;; User / auth-token setup
  (j/db-do-commands db-spec-dev
                    true
                    uddl/schema-version-ddl
                    uddl/v0-create-user-account-ddl
                    uddl/v0-add-unique-constraint-user-account-email
                    uddl/v0-add-unique-constraint-user-account-username
                    uddl/v0-create-authentication-token-ddl)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (uddl/v0-create-updated-count-inc-trigger-function-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (uddl/v0-create-user-account-updated-count-trigger-fn db-spec-dev))

  ;; Vehicle setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-vehicle-ddl
                    fpddl/v0-add-unique-constraint-vehicle-name)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-vehicle-updated-count-inc-trigger-function-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-vehicle-updated-count-trigger-fn db-spec-dev))

  ;; Fuelstation setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-fuelstation-ddl
                    fpddl/v0-create-index-on-fuelstation-name)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fuelstation-updated-count-inc-trigger-function-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fuelstation-updated-count-trigger-fn db-spec-dev))

  ;; Fuel purchase log setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-fplog-ddl)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fplog-updated-count-inc-trigger-function-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fplog-updated-count-trigger-fn db-spec-dev))

  ;; Environment log setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-envlog-ddl)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-envlog-updated-count-inc-trigger-function-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-envlog-updated-count-trigger-fn db-spec-dev)))
