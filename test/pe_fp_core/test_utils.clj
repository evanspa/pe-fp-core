(ns pe-fp-core.test-utils
  (:require [clojure.java.jdbc :as j]))

(def db-name "test_db")

(defn db-spec-fn
  ([]
   (db-spec-fn nil))
  ([db-name]
   (let [subname-prefix "//localhost:5432/"
         db-spec
         {:classname "org.postgresql.Driver"
          :subprotocol "postgresql"
          :subname (if db-name
                     (str subname-prefix db-name)
                     subname-prefix)
          :user "postgres"}]
     (j/with-db-connection [con-db db-spec]
       (let [jdbc-conn (:connection con-db)]
         (.addDataType jdbc-conn "geometry" org.postgis.PGgeometry)))
     db-spec)))

(def db-spec-without-db (db-spec-fn nil))

(def db-spec (db-spec-fn db-name))
