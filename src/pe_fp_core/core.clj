(ns pe-fp-core.core
  (:require [pe-fp-core.validation :as val]
            [clojure.tools.logging :as log]
            [pe-jdbc-utils.core :as jcore]
            [clojure.java.jdbc :as j]
            [pe-fp-core.ddl :as fpddl]
            [pe-core-utils.core :as ucore]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn next-vehicle-id
  [db-spec]
  (jcore/seq-next-val db-spec "vehicle_id_seq"))

(defn next-fuelstation-id
  [db-spec]
  (jcore/seq-next-val db-spec "fuelstation_id_seq"))

(defn next-fplog-id
  [db-spec]
  (jcore/seq-next-val db-spec "fplog_id_seq"))

(defn next-envlog-id
  [db-spec]
  (jcore/seq-next-val db-spec "envlog_id_seq"))

(def from-sql-time-fn #(c/from-sql-time %))

(defn rs->vehicle
  [vehicle-rs]
  [(:id vehicle-rs) (-> vehicle-rs
                        (ucore/replace-if-contains :id             :fpvehicle/id)
                        (ucore/replace-if-contains :user_id        :fpvehicle/user-id)
                        (ucore/replace-if-contains :name           :fpvehicle/name)
                        (ucore/replace-if-contains :default_octane :fpvehicle/default-octane)
                        (ucore/replace-if-contains :updated_count  :fpvehicle/updated-count)
                        (ucore/replace-if-contains :updated_at     :fpvehicle/updated-at from-sql-time-fn)
                        (ucore/replace-if-contains :deleted_at     :fpvehicle/deleted-at from-sql-time-fn)
                        (ucore/replace-if-contains :created_at     :fpvehicle/created-at from-sql-time-fn))])

(defn rs->fuelstation
  [fuelstation-rs]
  [(:id fuelstation-rs) (-> fuelstation-rs
                            (ucore/replace-if-contains :id            :fpfuelstation/id)
                            (ucore/replace-if-contains :user_id       :fpfuelstation/user-id)
                            (ucore/replace-if-contains :name          :fpfuelstation/name)
                            (ucore/replace-if-contains :street        :fpfuelstation/street)
                            (ucore/replace-if-contains :city          :fpfuelstation/city)
                            (ucore/replace-if-contains :state         :fpfuelstation/state)
                            (ucore/replace-if-contains :zip           :fpfuelstation/zip)
                            (ucore/replace-if-contains :latitude      :fpfuelstation/latitude)
                            (ucore/replace-if-contains :longitude     :fpfuelstation/longitude)
                            (ucore/replace-if-contains :updated_count :fpfuelstation/updated-count)
                            (ucore/replace-if-contains :updated_at    :fpfuelstation/updated-at from-sql-time-fn)
                            (ucore/replace-if-contains :deleted_at    :fpfuelstation/deleted-at from-sql-time-fn)
                            (ucore/replace-if-contains :created_at    :fpfuelstation/created-at from-sql-time-fn))])

(defn rs->fplog
  [fplog-rs]
  [(:id fplog-rs) (-> fplog-rs
                      (ucore/replace-if-contains :id                        :fplog/id)
                      (ucore/replace-if-contains :user_id                   :fplog/user-id)
                      (ucore/replace-if-contains :vehicle_id                :fplog/vehicle-id)
                      (ucore/replace-if-contains :fuelstation_id            :fplog/fuelstation-id)
                      (ucore/replace-if-contains :purchased_at              :fplog/purchased-at from-sql-time-fn)
                      (ucore/replace-if-contains :got_car_wash              :fplog/got-car-wash)
                      (ucore/replace-if-contains :car_wash_per_gal_discount :fplog/car-wash-per-gal-discount)
                      (ucore/replace-if-contains :num_gallons               :fplog/num-gallons)
                      (ucore/replace-if-contains :octane                    :fplog/octane)
                      (ucore/replace-if-contains :gallon_price              :fplog/gallon-price)
                      (ucore/replace-if-contains :updated_count             :fplog/updated-count)
                      (ucore/replace-if-contains :updated_at                :fplog/updated-at from-sql-time-fn)
                      (ucore/replace-if-contains :deleted_at                :fplog/deleted-at from-sql-time-fn)
                      (ucore/replace-if-contains :created_at                :fplog/created-at from-sql-time-fn))])

(defn rs->envlog
  [envlog-rs]
  [(:id envlog-rs) (-> envlog-rs
                       (ucore/replace-if-contains :id                    :envlog/id)
                       (ucore/replace-if-contains :user_id               :envlog/user-id)
                       (ucore/replace-if-contains :vehicle_id            :envlog/vehicle-id)
                       (ucore/replace-if-contains :logged_at             :envlog/logged-at from-sql-time-fn)
                       (ucore/replace-if-contains :reported_avg_mpg      :envlog/reported-avg-mpg)
                       (ucore/replace-if-contains :reported_avg_mph      :envlog/reported-avg-mph)
                       (ucore/replace-if-contains :reported_outside_temp :envlog/reported-outside-temp)
                       (ucore/replace-if-contains :odometer              :envlog/odometer)
                       (ucore/replace-if-contains :dte                   :envlog/dte)
                       (ucore/replace-if-contains :updated_count         :envlog/updated-count)
                       (ucore/replace-if-contains :updated_at            :envlog/updated-at from-sql-time-fn)
                       (ucore/replace-if-contains :deleted_at            :envlog/deleted-at from-sql-time-fn)
                       (ucore/replace-if-contains :created_at            :envlog/created-at from-sql-time-fn))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel purchase log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn save-new-fplog
  [db-spec user-id vehicle-id fuelstation-id new-fplog-id fplog]
  (let [validation-mask (val/save-fplog-validation-mask fplog)]
    (if (pos? (bit-and validation-mask val/sfplog-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (let [created-at (c/to-timestamp (:fplog/created-at fplog))]
        (j/insert! db-spec
                   :fplog
                   {:user_id                   user-id
                    :vehicle_id                vehicle-id
                    :fuelstation_id            fuelstation-id
                    :id                        new-fplog-id
                    :purchased_at              (c/to-timestamp (:fplog/purchased-at fplog))
                    :got_car_wash              (:fplog/got-car-wash fplog)
                    :car_wash_per_gal_discount (:fplog/car-wash-per-gal-discount fplog)
                    :num_gallons               (:fplog/num-gallons fplog)
                    :octane                    (:fplog/octane fplog)
                    :gallon_price              (:fplog/gallon-price fplog)
                    :created_at                created-at
                    :updated_at                created-at
                    :updated_count             1})))))

(defn save-fplog
  [db-spec fplog-id fplog]
  (j/update! db-spec
             :fplog
             (-> fplog
                 (dissoc :updated_count)
                 (dissoc :fplog/updated-count)
                 (ucore/replace-if-contains :fplog/user-id                   :user_id)
                 (ucore/replace-if-contains :fplog/vehicle-id                :vehicle_id)
                 (ucore/replace-if-contains :fplog/fuelstation-id            :fuelstation_id)
                 (ucore/replace-if-contains :fplog/purchased-at              :purchased_at c/to-timestamp)
                 (ucore/replace-if-contains :fplog/got-car-wash              :got_car_wash)
                 (ucore/replace-if-contains :fplog/car-wash-per-gal-discount :car_wash_per_gal_discount)
                 (ucore/replace-if-contains :fplog/num-gallons               :num_gallons)
                 (ucore/replace-if-contains :fplog/octane                    :octane)
                 (ucore/replace-if-contains :fplog/gallon-price              :gallon_price)
                 (ucore/replace-if-contains :fplog/updated-at                :updated_at c/to-timestamp)
                 (ucore/replace-if-contains :fplog/deleted-at                :deleted_at c/to-timestamp))
             ["id = ?" fplog-id]))

(defn fplogs-for-user
  [db-spec user-id]
  (j/query db-spec
           [(format "select * from %s where user_id = ? order by purchased_at desc"
                    fpddl/tbl-fplog)
            user-id]
           :row-fn rs->fplog))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn save-new-envlog
  [db-spec user-id vehicle-id new-envlog-id envlog]
  (let [validation-mask (val/save-envlog-validation-mask envlog)]
    (if (pos? (bit-and validation-mask val/senvlog-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (let [created-at (c/to-timestamp (:envlog/created-at envlog))]
        (j/insert! db-spec
                   :envlog
                   {:user_id               user-id
                    :vehicle_id            vehicle-id
                    :id                    new-envlog-id
                    :logged_at             (c/to-timestamp (:envlog/logged-at envlog))
                    :reported_avg_mpg      (:envlog/reported-avg-mpg envlog)
                    :reported_avg_mph      (:envlog/reported-avg-mph envlog)
                    :reported_outside_temp (:envlog/reported-outside-temp envlog)
                    :odometer              (:envlog/odometer envlog)
                    :dte                   (:envlog/dte envlog)
                    :created_at            created-at
                    :updated_at            created-at
                    :updated_count         1})))))

(defn save-envlog
  [db-spec envlog-id envlog]
  (j/update! db-spec
             :envlog
             (-> envlog
                 (dissoc :updated_count)
                 (dissoc :envlog/updated-count)
                 (ucore/replace-if-contains :envlog/user-id               :user_id)
                 (ucore/replace-if-contains :envlog/vehicle-id            :vehicle_id)
                 (ucore/replace-if-contains :envlog/logged-at             :logged_at c/to-timestamp)
                 (ucore/replace-if-contains :envlog/reported-avg-mpg      :reported_avg_mpg)
                 (ucore/replace-if-contains :envlog/reported-avg-mph      :reported_avg_mph)
                 (ucore/replace-if-contains :envlog/reported-outside-temp :reported_outside_temp)
                 (ucore/replace-if-contains :envlog/odometer              :odometer)
                 (ucore/replace-if-contains :envlog/dte                   :dte)
                 (ucore/replace-if-contains :envlog/updated-at            :updated_at c/to-timestamp)
                 (ucore/replace-if-contains :envlog/deleted-at            :deleted_at c/to-timestamp))
             ["id = ?" envlog-id]))

(defn envlogs-for-user
  [db-spec user-id]
  (j/query db-spec
           [(format "select * from %s where user_id = ? order by logged_at desc"
                    fpddl/tbl-envlog)
            user-id]
           :row-fn rs->envlog))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuelstation-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn save-new-fuelstation
  [db-spec user-id new-fuelstation-id fuelstation]
  (let [validation-mask (val/save-fuelstation-validation-mask fuelstation)]
    (if (pos? (bit-and validation-mask val/sfs-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (let [created-at (c/to-timestamp (:fpfuelstation/created-at fuelstation))]
        (j/insert! db-spec
                   :fuelstation
                   {:user_id       user-id
                    :id            new-fuelstation-id
                    :name          (:fpfuelstation/name fuelstation)
                    :street        (:fpfuelstation/street fuelstation)
                    :city          (:fpfuelstation/city fuelstation)
                    :state         (:fpfuelstation/state fuelstation)
                    :zip           (:fpfuelstation/zip fuelstation)
                    :latitude      (:fpfuelstation/latitude fuelstation)
                    :longitude     (:fpfuelstation/longitude fuelstation)
                    :created_at    created-at
                    :updated_at    created-at
                    :updated_count 1})))))

(defn save-fuelstation
  [db-spec fuelstation-id fuelstation]
  (j/update! db-spec
             :fuelstation
             (-> fuelstation
                 (dissoc :updated_count)
                 (dissoc :fpfuelstation/updated-count)
                 (ucore/replace-if-contains :fpfuelstation/user-id    :user_id)
                 (ucore/replace-if-contains :fpfuelstation/updated-at :updated_at c/to-timestamp)
                 (ucore/replace-if-contains :fpfuelstation/deleted-at :deleted_at c/to-timestamp)
                 (ucore/replace-if-contains :fpfuelstation/name       :name)
                 (ucore/replace-if-contains :fpfuelstation/street     :street)
                 (ucore/replace-if-contains :fpfuelstation/city       :city)
                 (ucore/replace-if-contains :fpfuelstation/state      :state)
                 (ucore/replace-if-contains :fpfuelstation/zip        :zip)
                 (ucore/replace-if-contains :fpfuelstation/latitude   :latitude)
                 (ucore/replace-if-contains :fpfuelstation/longitude  :longitude))
             ["id = ?" fuelstation-id]))

(defn fuelstations-for-user
  [db-spec user-id]
  (j/query db-spec
           [(format "select * from %s where user_id = ? order by updated_at desc"
                    fpddl/tbl-fuelstation)
            user-id]
           :row-fn rs->fuelstation))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn save-new-vehicle
  [db-spec user-id new-vehicle-id vehicle]
  (let [validation-mask (val/save-vehicle-validation-mask vehicle)]
    (if (pos? (bit-and validation-mask val/sv-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (let [created-at (c/to-timestamp (:fpvehicle/created-at vehicle))]
        (try
          (j/insert! db-spec
                     :vehicle
                     {:user_id        user-id
                      :id             new-vehicle-id
                      :name           (:fpvehicle/name vehicle)
                      :default_octane (:fpvehicle/default-octane vehicle)
                      :created_at     created-at
                      :updated_at     created-at
                      :updated_count  1})
          (catch java.sql.SQLException e
            (if (jcore/uniq-constraint-violated? db-spec e)
              (let [ucv (jcore/uniq-constraint-violated db-spec e)]
                (if (= ucv fpddl/constr-vehicle-uniq-name)
                  (throw (IllegalArgumentException. (str (bit-or 0
                                                                 val/sv-vehicle-already-exists
                                                                 val/sv-any-issues))))
                  (throw e)))
              (throw e))))))))

(defn save-vehicle
  [db-spec vehicle-id vehicle]
  (j/update! db-spec
             :vehicle
             (-> vehicle
                 (dissoc :updated_count)
                 (dissoc :vehicle/updated-count)
                 (ucore/replace-if-contains :fpvehicle/user-id    :user_id)
                 (ucore/replace-if-contains :fpvehicle/updated-at :updated_at c/to-timestamp)
                 (ucore/replace-if-contains :fpvehicle/deleted-at :deleted_at c/to-timestamp)
                 (ucore/replace-if-contains :fpvehicle/name       :name))
             ["id = ?" vehicle-id]))

(defn vehicles-for-user
  [db-spec user-id]
  (j/query db-spec
           [(format "select * from %s where user_id = ? order by updated_at desc"
                    fpddl/tbl-vehicle)
            user-id]
           :row-fn rs->vehicle))

(defn vehicles-for-user-by-name
  [db-spec user-id name]
  (j/query db-spec
           [(format "select * from %s where user_id = ? and name ilike ? order by updated_at desc"
                    fpddl/tbl-vehicle)
            user-id
            name]
           :row-fn rs->vehicle))
