(ns pe-fp-core.migration
  (:require [pe-fp-core.core :as core]
            [pe-fp-core.validation :as val]
            [clojure.tools.logging :as log]
            [pe-jdbc-utils.core :as jcore]
            [clojure.java.jdbc :as j]
            [pe-user-core.core :as usercore]
            [pe-fp-core.ddl :as fpddl]
            [pe-core-utils.core :as ucore]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.pprint :refer (pprint)]))

(defn v4-migrations
  [db-spec]
  (let [users (usercore/users db-spec)]
    (doseq [[user-id user] users]
      (let [fplogs (core/fplogs-for-user db-spec user-id)]
        (doseq [[fplog-id fplog] fplogs]
          (when (nil? (:fplog/odometer fplog))
            (let [fplog-created-at (:fplog/created-at fplog)
                  fplog-created-at-long (c/to-long fplog-created-at)
                  rs (j/query db-spec
                              ["select odometer, created_at from envlog where user_id = ? and logged_at = ?"
                               user-id
                               (c/to-timestamp (:fplog/purchased-at fplog))])]
              (let [sorted-rs (sort #(compare (Math/abs (- (c/to-long (:created_at %1)) fplog-created-at-long))
                                              (Math/abs (- (c/to-long (:created_at %2)) fplog-created-at-long)))
                                    rs)
                    odometer (:odometer (first sorted-rs))]
                (when (not (nil? odometer))
                  (j/update! db-spec
                             :fplog
                             {:odometer odometer}
                             ["id = ?" fplog-id]))))))))))
