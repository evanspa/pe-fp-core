(ns pe-fp-core.ddl
  (:require [clojure.java.jdbc :as j]
            [pe-user-core.ddl :as uddl]
            [pe-jdbc-utils.core :as jcore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def tbl-vehicle "vehicle")
(def tbl-fuelstation "fuelstation")
(def tbl-fuelstation-type "fuelstation_type")
(def tbl-fplog "fplog")
(def tbl-envlog "envlog")
(def tbl-price-event "price_event")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Column Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def col-updated-count "updated_count")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constraint Names
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def constr-vehicle-uniq-name    "vehicle_name_key")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Changes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def v1-vehicle-add-fuel-capacity-col
  (format "ALTER TABLE %s ADD COLUMN fuel_capacity numeric NULL", tbl-vehicle))

(def v2-vehicle-drop-erroneous-unique-name-constraint
  (format "ALTER TABLE %s DROP CONSTRAINT %s"
          tbl-vehicle
          constr-vehicle-uniq-name))

(def v2-vehicle-add-proper-unique-name-constraint
  (format "ALTER TABLE %s ADD CONSTRAINT %s UNIQUE (user_id, name)"
          tbl-vehicle
          constr-vehicle-uniq-name))

(def v3-vehicle-drop-erroneous-unique-name-constraint-again
  (format "ALTER TABLE %s DROP CONSTRAINT %s"
          tbl-vehicle
          constr-vehicle-uniq-name))

(def v3-vehicle-add-proper-unique-name-constraint-take-2
  (format "CREATE UNIQUE INDEX %s ON %s (user_id, name) WHERE deleted_at IS NULL"
          constr-vehicle-uniq-name
          tbl-vehicle))

(def v4-fplog-add-odometer-col
  (format "ALTER TABLE %s ADD COLUMN odometer numeric NULL" tbl-fplog))

(def v5-vehicle-add-diesel-col
  (format "ALTER TABLE %s ADD COLUMN is_diesel boolean NULL" tbl-vehicle))

(def v5-fplog-add-diesel-col
  (format "ALTER TABLE %s ADD COLUMN is_diesel boolean NULL" tbl-fplog))

(def v5-vehicle-add-vin-col
  (format "ALTER TABLE %s ADD COLUMN vin text NULL" tbl-vehicle))

(def v5-vehicle-add-plate-col
  (format "ALTER TABLE %s ADD COLUMN plate text NULL" tbl-vehicle))

(def v5-vehicle-add-has-dte-readout-col
  (format "ALTER TABLE %s ADD COLUMN has_dte_readout boolean NULL" tbl-vehicle))

(def v5-vehicle-add-has-mpg-readout-col
  (format "ALTER TABLE %s ADD COLUMN has_mpg_readout boolean NULL" tbl-vehicle))

(def v5-vehicle-add-has-mph-readout-col
  (format "ALTER TABLE %s ADD COLUMN has_mph_readout boolean NULL" tbl-vehicle))

(def v5-vehicle-add-has-outside-temp-readout-col
  (format "ALTER TABLE %s ADD COLUMN has_outside_temp_readout boolean NULL" tbl-vehicle))

(def v6-create-fuelstation-type-ddl
  (str (format "CREATE TABLE IF NOT EXISTS %s (" tbl-fuelstation-type)
       "type_id         integer PRIMARY KEY, "
       "type_name       text    NOT NULL, "
       "type_sort_order integer NOT NULL)"))

(def v6-fuelstation-add-fstype-col
  (format "ALTER TABLE %s ADD COLUMN type_id integer REFERENCES %s(type_id)"
          tbl-fuelstation
          tbl-fuelstation-type))

(def v6-create-price-event-ddl
  (str (format "CREATE TABLE IF NOT EXISTS %s (" tbl-price-event)
       "id                 serial           PRIMARY KEY, "
       (format "fplog_id   integer          NOT NULL REFERENCES %s (id), " tbl-fplog)
       (format "fs_type_id integer          NOT NULL REFERENCES %s (type_id), " tbl-fuelstation-type)
       "price              numeric          NOT NULL, "
       "octane             integer          NULL, "
       "is_diesel          boolean          NOT NULL, "
       "event_date         timestamptz      NOT NULL, "
       "latitude           double precision NOT NULL, "
       "longitude          double precision NOT NULL)"))

(def v6-create-postgis-extension "create extension postgis")

(defn v6-add-location-col-sql
  [db-spec]
  (j/query db-spec
           (format "select AddGeometryColumn ('public', '%s', 'location', 4326, 'POINT', 2)"
                   tbl-price-event)))

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

(def v0-create-vehicle-updated-count-inc-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger-fn db-spec
                               tbl-vehicle
                               col-updated-count)))

(def v0-create-vehicle-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger db-spec
                            tbl-vehicle
                            col-updated-count
                            (jcore/inc-trigger-fn-name tbl-vehicle
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

(def v0-create-fuelstation-updated-count-inc-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger-fn db-spec
                               tbl-fuelstation
                               col-updated-count)))

(def v0-create-fuelstation-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger db-spec
                            tbl-fuelstation
                            col-updated-count
                            (jcore/inc-trigger-fn-name tbl-fuelstation
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

(def v0-create-fplog-updated-count-inc-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger-fn db-spec
                               tbl-fplog
                               col-updated-count)))

(def v0-create-fplog-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger db-spec
                            tbl-fplog
                            col-updated-count
                            (jcore/inc-trigger-fn-name tbl-fplog
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

(def v0-create-envlog-updated-count-inc-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger-fn db-spec
                               tbl-envlog
                               col-updated-count)))

(def v0-create-envlog-updated-count-trigger-fn
  (fn [db-spec]
    (jcore/auto-inc-trigger db-spec
                            tbl-envlog
                            col-updated-count
                            (jcore/inc-trigger-fn-name tbl-envlog
                                                       col-updated-count))))
