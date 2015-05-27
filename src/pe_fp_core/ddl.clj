(ns pe-fp-core.ddl
  (:require [clojure.java.jdbc :as j]
            [pe-user-core.ddl :as uddl]
            [pe-jdbc-utils.core :as jcore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def tbl-vehicle "vehicle")
(def tbl-fuelstation "fuelstation")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Column Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def col-updated-count "updated_count")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constraint Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def constr-vehicle-uniq-name    "vehicle_name_key")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DDL vars
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle table, constraints and triggers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def v0-create-vehicle-ddl
  (str (format "CREATE TABLE IF NOT EXISTS %s (" tbl-vehicle)
       "id              serial      PRIMARY KEY, "
       (format "user_id integer     NOT NULL REFERENCES %s (id), " uddl/tbl-user-account)
       "name            text        NOT NULL, "
       "default_octane  integer     NULL, "
       "created_at      timestamptz NOT NULL, "
       "updated_at      timestamptz NOT NULL, "
       (format "%s      integer     NOT NULL, " col-updated-count)
       "deleted_at      timestamptz NULL)"))

(def v0-add-unique-constraint-vehicle-name
  (format "ALTER TABLE %s ADD CONSTRAINT %s UNIQUE (name)"
          tbl-vehicle
          constr-vehicle-uniq-name))

(def v0-create-vehicle-updated-count-inc-trigger-function-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger-function db-spec
                                             tbl-vehicle
                                             col-updated-count)))

(def v0-create-vehicle-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger db-spec
                                    tbl-vehicle
                                    col-updated-count
                                    (jcore/incrementing-trigger-function-name tbl-vehicle
                                                                              col-updated-count))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuelstation table, constraints and triggers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def v0-create-fuelstation-ddl
  (str (format "CREATE TABLE IF NOT EXISTS %s (" tbl-fuelstation)
       "id              serial           PRIMARY KEY, "
       (format "user_id integer          NOT NULL REFERENCES %s (id), " uddl/tbl-user-account)
       "name            text             NULL, "
       "street          text             NULL, "
       "city            text             NULL, "
       "state           text             NULL, "
       "zip             text             NULL, "
       "latitude        double precision NULL, "
       "longitude       double precision NULL, "
       "created_at      timestamptz      NOT NULL, "
       "updated_at      timestamptz      NOT NULL, "
       (format "%s      integer          NOT NULL, " col-updated-count)
       "deleted_at      timestamptz      NULL)"))

(def v0-create-index-on-fuelstation-name
  (format "CREATE INDEX ON %s (name)" tbl-fuelstation))

(def v0-create-fuelstation-updated-count-inc-trigger-function-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger-function db-spec
                                             tbl-fuelstation
                                             col-updated-count)))

(def v0-create-fuelstation-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger db-spec
                                    tbl-fuelstation
                                    col-updated-count
                                    (jcore/incrementing-trigger-function-name tbl-fuelstation
                                                                              col-updated-count))))
