(ns pe-fp-core.test-utils
  (:require [clojure.java.jdbc :as j]))

(def db-name "test_db")

(def subprotocol "postgresql")

(defn db-spec-fn
  ([]
   (db-spec-fn nil))
  ([db-name]
   (let [subname-prefix "//localhost:5432/"
         db-spec
         {:classname "org.postgresql.Driver"
          :subprotocol subprotocol
          :subname (if db-name
                     (str subname-prefix db-name)
                     subname-prefix)
          :user "postgres"}]
     (j/with-db-connection [con-db db-spec]
       (let [jdbc-conn (:connection con-db)]
         (.addDataType jdbc-conn "geometry" org.postgis.PGgeometry)))
     db-spec)))

(def db-spec-without-db
  (with-meta
    (db-spec-fn nil)
    {:subprotocol subprotocol}))

(def db-spec
  (with-meta
    (db-spec-fn db-name)
    {:subprotocol subprotocol}))
