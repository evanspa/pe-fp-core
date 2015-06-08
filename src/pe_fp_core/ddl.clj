(ns pe-fp-core.ddl
  (:require [clojure.java.jdbc :as j]
            [pe-user-core.ddl :as uddl]
            [pe-jdbc-utils.core :as jcore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def tbl-vehicle "vehicle")
(def tbl-fuelstation "fuelstation")
(def tbl-fplog "fplog")
(def tbl-envlog "envlog")

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

(def v1-vehicle-add-fuel-capacity-col
  (format "ALTER TABLE %s ADD COLUMN fuel_capacity numeric NULL", tbl-vehicle))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel purchase log table, constraints and triggers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def v0-create-fplog-ddl
  (str (format "CREATE TABLE IF NOT EXISTS %s (" tbl-fplog)
       "id                        serial      PRIMARY KEY, "
       (format "user_id           integer     NOT NULL REFERENCES %s (id), " uddl/tbl-user-account)
       (format "vehicle_id        integer     NOT NULL REFERENCES %s (id), " tbl-vehicle)
       (format "fuelstation_id    integer     NOT NULL REFERENCES %s (id), " tbl-fuelstation)
       "purchased_at              timestamptz NULL, "
       "got_car_wash              boolean     NULL, "
       "car_wash_per_gal_discount numeric     NULL, "
       "num_gallons               numeric     NULL, "
       "octane                    integer     NULL, "
       "gallon_price              numeric     NULL, "
       "created_at                timestamptz NOT NULL, "
       "updated_at                timestamptz NOT NULL, "
       (format "%s                integer     NOT NULL, " col-updated-count)
       "deleted_at                timestamptz NULL)"))

(def v0-create-fplog-updated-count-inc-trigger-function-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger-function db-spec
                                             tbl-fplog
                                             col-updated-count)))

(def v0-create-fplog-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger db-spec
                                    tbl-fplog
                                    col-updated-count
                                    (jcore/incrementing-trigger-function-name tbl-fplog
                                                                              col-updated-count))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log table, constraints and triggers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def v0-create-envlog-ddl
  (str (format "CREATE TABLE IF NOT EXISTS %s (" tbl-envlog)
       "id                        serial      PRIMARY KEY, "
       (format "user_id           integer     NOT NULL REFERENCES %s (id), " uddl/tbl-user-account)
       (format "vehicle_id        integer     NOT NULL REFERENCES %s (id), " tbl-vehicle)
       "logged_at                 timestamptz NULL, "
       "reported_avg_mpg          numeric     NULL, "
       "reported_avg_mph          numeric     NULL, "
       "reported_outside_temp     numeric     NULL, "
       "odometer                  numeric     NULL, "
       "dte                       numeric     NULL, "
       "created_at                timestamptz NOT NULL, "
       "updated_at                timestamptz NOT NULL, "
       (format "%s                integer     NOT NULL, " col-updated-count)
       "deleted_at                timestamptz NULL)"))

(def v0-create-envlog-updated-count-inc-trigger-function-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger-function db-spec
                                             tbl-envlog
                                             col-updated-count)))

(def v0-create-envlog-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-incremented-trigger db-spec
                                    tbl-envlog
                                    col-updated-count
                                    (jcore/incrementing-trigger-function-name tbl-envlog
                                                                              col-updated-count))))
