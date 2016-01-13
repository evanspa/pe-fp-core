(ns pe-fp-core.core
  (:require [pe-fp-core.validation :as val]
            [clojure.tools.logging :as log]
            [pe-jdbc-utils.core :as jcore]
            [clojure.java.jdbc :as j]
            [pe-user-core.core :as usercore]
            [pe-fp-core.ddl :as fpddl]
            [pe-core-utils.core :as ucore]
            [clj-time.core :as t]
            [clj-time.coerce :as c])
  (:import (org.postgis PGgeometry)))

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
                        (ucore/replace-if-contains :id                       :fpvehicle/id)
                        (ucore/replace-if-contains :user_id                  :fpvehicle/user-id)
                        (ucore/replace-if-contains :name                     :fpvehicle/name)
                        (ucore/replace-if-contains :default_octane           :fpvehicle/default-octane)
                        (ucore/replace-if-contains :is_diesel                :fpvehicle/is-diesel)
                        (ucore/replace-if-contains :has_dte_readout          :fpvehicle/has-dte-readout)
                        (ucore/replace-if-contains :has_mpg_readout          :fpvehicle/has-mpg-readout)
                        (ucore/replace-if-contains :has_mph_readout          :fpvehicle/has-mph-readout)
                        (ucore/replace-if-contains :has_outside_temp_readout :fpvehicle/has-outside-temp-readout)
                        (ucore/replace-if-contains :fuel_capacity            :fpvehicle/fuel-capacity)
                        (ucore/replace-if-contains :vin                      :fpvehicle/vin)
                        (ucore/replace-if-contains :plate                    :fpvehicle/plate)
                        (ucore/replace-if-contains :updated_count            :fpvehicle/updated-count)
                        (ucore/replace-if-contains :updated_at               :fpvehicle/updated-at from-sql-time-fn)
                        (ucore/replace-if-contains :deleted_at               :fpvehicle/deleted-at from-sql-time-fn)
                        (ucore/replace-if-contains :created_at               :fpvehicle/created-at from-sql-time-fn))])

(defn rs->fuelstation
  [fuelstation-rs]
  [(:id fuelstation-rs) (-> fuelstation-rs
                            (ucore/replace-if-contains :id            :fpfuelstation/id)
                            (ucore/replace-if-contains :user_id       :fpfuelstation/user-id)
                            (ucore/replace-if-contains :type_id       :fpfuelstation/type-id)
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
                      (ucore/replace-if-contains :is_diesel                 :fplog/is-diesel)
                      (ucore/replace-if-contains :octane                    :fplog/octane)
                      (ucore/replace-if-contains :odometer                  :fplog/odometer)
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

(defn rs->price-event
  [price-event-rs]
  (-> price-event-rs
      (ucore/replace-if-contains :fs_id        :price-event/fs-id)
      (ucore/replace-if-contains :type_id      :price-event/fs-type-id)
      (ucore/replace-if-contains :street       :price-event/fs-street)
      (ucore/replace-if-contains :city         :price-event/fs-city)
      (ucore/replace-if-contains :state        :price-event/fs-state)
      (ucore/replace-if-contains :zip          :price-event/fs-zip)
      (ucore/replace-if-contains :latitude     :price-event/fs-latitude)
      (ucore/replace-if-contains :longitude    :price-event/fs-longitude)
      (ucore/replace-if-contains :distance     :price-event/fs-distance)
      (ucore/replace-if-contains :gallon_price :price-event/price)
      (ucore/replace-if-contains :octane       :price-event/octane)
      (ucore/replace-if-contains :is_diesel    :price-event/is-diesel)
      (ucore/replace-if-contains :purchased_at :price-event/event-date from-sql-time-fn)))

(declare vehicle-by-id)
(declare fuelstation-by-id)
(declare fplog-by-id)
(declare envlog-by-id)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Price event-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn nearby-price-events
  [db-spec
   latitude
   longitude
   distance-within
   event-date-after
   sort-by
   max-results
   min-distance-diff-fs]
  (let [sort-by-clause (reduce (fn [clause [col dir]] (format "%s%s %s," clause col dir)) "" sort-by)
        sort-by-clause (.substring sort-by-clause 0 (dec (count sort-by-clause)))
        qry (str "select fs.id as fs_id, fs.type_id, f.gallon_price, f.octane, f.is_diesel, "
                 "f.purchased_at, fs.latitude, fs.longitude, fs.street, fs.city, fs.state, fs.zip, "
                 (format "ST_Distance(fs.location, ST_Geomfromtext('POINT(%s %s)', 4326)::geography) as distance" longitude latitude)
                 (format " from %s f, %s fs" fpddl/tbl-fplog fpddl/tbl-fuelstation)
                 (format " where f.fuelstation_id = fs.id and ST_DWithin(fs.location, ST_Geomfromtext('POINT(%s %s)', 4326)::geography, %s)" longitude latitude distance-within)
                 (if (not (nil? event-date-after)) " and f.purchased_at > ?" "")
                 " and f.deleted_at is null and fs.deleted_at is null"
                 (format " order by %s" sort-by-clause)
                 (format " limit %s" max-results))
        args (if (not (nil? event-date-after)) [(c/to-timestamp event-date-after)] [])
        rs (j/query db-spec (vec (concat [qry] args)) :row-fn rs->price-event)]
    (let [filtered-rs (reduce (fn [[fs-ids distances events]
                                   {fs-id :price-event/fs-id
                                    dist :price-event/fs-distance
                                    :as evt}]
                                (let [new-fs-ids (assoc fs-ids fs-id fs-id)
                                      new-distances (assoc distances dist dist)]
                                  (if (nil? (get fs-ids fs-id))
                                    (if (nil? (get distances dist))
                                      [new-fs-ids new-distances (conj events evt)]
                                      [new-fs-ids new-distances events])
                                    [new-fs-ids new-distances events])))
                              [{} {} []]
                              rs)
          events (nth filtered-rs 2)]
      (ucore/remove-matches events
                            #(<= (Math/abs (- (:price-event/fs-distance %1)
                                              (:price-event/fs-distance %2))) min-distance-diff-fs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel purchase log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def fplog-key-pairs
  [[:fplog/user-id                   :user_id]
   [:fplog/vehicle-id                :vehicle_id]
   [:fplog/fuelstation-id            :fuelstation_id]
   [:fplog/purchased-at              :purchased_at c/to-timestamp]
   [:fplog/got-car-wash              :got_car_wash]
   [:fplog/car-wash-per-gal-discount :car_wash_per_gal_discount]
   [:fplog/num-gallons               :num_gallons]
   [:fplog/is-diesel                 :is_diesel]
   [:fplog/octane                    :octane]
   [:fplog/odometer                  :odometer]
   [:fplog/gallon-price              :gallon_price]])

(defn fplog-deps
  ([db-spec]
   (fplog-deps db-spec :fplog/user-id :fplog/vehicle-id :fplog/fuelstation-id))
  ([db-spec user-id-or-key vehicle-id-or-key fuelstation-id-or-key]
   [[#(usercore/load-user-by-id db-spec %) user-id-or-key val/sfplog-user-does-not-exist]
    [#(vehicle-by-id db-spec %) vehicle-id-or-key val/sfplog-vehicle-does-not-exist]
    [#(fuelstation-by-id db-spec %) fuelstation-id-or-key val/sfplog-fuelstation-does-not-exist]]))

(defn save-new-fplog
  [db-spec user-id vehicle-id fuelstation-id new-fplog-id fplog]
  (jcore/save-new-entity db-spec
                         new-fplog-id
                         fplog
                         val/create-fplog-validation-mask
                         val/sfplog-any-issues
                         fplog-by-id
                         :fplog
                         fplog-key-pairs
                         {:user_id user-id
                          :vehicle_id vehicle-id
                          :fuelstation_id fuelstation-id}
                         :fplog/created-at
                         :fplog/updated-at
                         nil
                         (fplog-deps db-spec user-id vehicle-id fuelstation-id)))

(defn save-fplog
  ([db-spec fplog-id fplog]
   (save-fplog db-spec fplog-id fplog nil))
  ([db-spec fplog-id fplog if-unmodified-since]
   (jcore/save-entity db-spec
                      fplog-id
                      fplog
                      val/save-fplog-validation-mask
                      val/sfplog-any-issues
                      fplog-by-id
                      :fplog
                      fplog-key-pairs
                      :fplog/updated-at
                      nil
                      (fplog-deps db-spec)
                      if-unmodified-since)))

(defn fplogs-for-user
  ([db-spec user-id]
   (fplogs-for-user db-spec user-id true))
  ([db-spec user-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-fplog
                               "user_id"
                               "="
                               user-id
                               "purchased_at"
                               "desc"
                               rs->fplog
                               active-only)))

(defn fplogs-modified-since
  [db-spec user-id modified-since]
  (jcore/entities-modified-since db-spec
                                 fpddl/tbl-fplog
                                 "user_id"
                                 "="
                                 user-id
                                 "updated_at"
                                 "deleted_at"
                                 modified-since
                                 :fplog/id
                                 :fplog/deleted-at
                                 :fplog/updated-at
                                 rs->fplog))

(defn fplogs-for-fuelstation
  ([db-spec user-id fuelstation-id]
   (fplogs-for-fuelstation db-spec user-id fuelstation-id true))
  ([db-spec user-id fuelstation-id active-only]
   (j/query db-spec
            [(format "select * from %s where user_id = ? and %s = ?%s order by %s %s"
                     fpddl/tbl-fplog
                     "fuelstation_id"
                     (jcore/active-only-where active-only)
                     "purchased_at"
                     "desc")
             user-id
             fuelstation-id]
            :row-fn rs->fplog)))

(defn fplog-by-id
  ([db-spec fplog-id]
   (fplog-by-id db-spec fplog-id true))
  ([db-spec fplog-id active-only]
   (jcore/load-entity-by-col db-spec fpddl/tbl-fplog "id" "=" fplog-id rs->fplog active-only)))

(defn mark-fplog-as-deleted
  [db-spec fplog-id if-unmodified-since]
  (jcore/mark-entity-as-deleted db-spec
                                fplog-id
                                fplog-by-id
                                :fplog
                                :fplog/updated-at
                                if-unmodified-since))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def envlog-key-pairs
  [[:envlog/vehicle-id            :vehicle_id]
   [:envlog/user-id               :user_id]
   [:envlog/logged-at             :logged_at c/to-timestamp]
   [:envlog/reported-avg-mpg      :reported_avg_mpg]
   [:envlog/reported-avg-mph      :reported_avg_mph]
   [:envlog/reported-outside-temp :reported_outside_temp]
   [:envlog/odometer              :odometer]
   [:envlog/dte                   :dte]])

(defn envlog-deps
  ([db-spec]
   (envlog-deps db-spec :envlog/user-id :envlog/vehicle-id))
  ([db-spec user-id-or-key vehicle-id-or-key]
   [[#(usercore/load-user-by-id db-spec %) user-id-or-key val/senvlog-user-does-not-exist]
    [#(vehicle-by-id db-spec %) vehicle-id-or-key val/senvlog-vehicle-does-not-exist]]))

(defn save-new-envlog
  [db-spec user-id vehicle-id new-envlog-id envlog]
  (jcore/save-new-entity db-spec
                         new-envlog-id
                         envlog
                         val/create-envlog-validation-mask
                         val/senvlog-any-issues
                         envlog-by-id
                         :envlog
                         envlog-key-pairs
                         {:user_id user-id :vehicle_id vehicle-id}
                         :envlog/created-at
                         :envlog/updated-at
                         nil
                         (envlog-deps db-spec user-id vehicle-id)))

(defn save-envlog
  ([db-spec envlog-id envlog]
   (save-envlog db-spec envlog-id envlog nil))
  ([db-spec envlog-id envlog if-unmodified-since]
   (jcore/save-entity db-spec
                      envlog-id
                      envlog
                      val/save-envlog-validation-mask
                      val/senvlog-any-issues
                      envlog-by-id
                      :envlog
                      envlog-key-pairs
                      :envlog/updated-at
                      nil
                      (envlog-deps db-spec)
                      if-unmodified-since)))

(defn envlogs-for-user
  ([db-spec user-id]
   (envlogs-for-user db-spec user-id true))
  ([db-spec user-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-envlog
                               "user_id"
                               "="
                               user-id
                               "logged_at"
                               "desc"
                               rs->envlog
                               active-only)))

(defn envlogs-modified-since
  [db-spec user-id modified-since]
  (jcore/entities-modified-since db-spec
                                 fpddl/tbl-envlog
                                 "user_id"
                                 "="
                                 user-id
                                 "updated_at"
                                 "deleted_at"
                                 modified-since
                                 :envlog/id
                                 :envlog/deleted-at
                                 :envlog/updated-at
                                 rs->envlog))

(defn envlog-by-id
  ([db-spec envlog-id]
   (envlog-by-id db-spec envlog-id true))
  ([db-spec envlog-id active-only]
   (jcore/load-entity-by-col db-spec fpddl/tbl-envlog "id" "=" envlog-id rs->envlog active-only)))

(defn mark-envlog-as-deleted
  [db-spec envlog-id if-unmodified-since]
  (jcore/mark-entity-as-deleted db-spec
                                envlog-id
                                envlog-by-id
                                :envlog
                                :envlog/updated-at
                                if-unmodified-since))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuelstation-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def fuelstation-key-pairs
  [[:fpfuelstation/user-id   :user_id]
   [:fpfuelstation/type-id   :type_id]
   [:fpfuelstation/name      :name]
   [:fpfuelstation/street    :street]
   [:fpfuelstation/city      :city]
   [:fpfuelstation/state     :state]
   [:fpfuelstation/zip       :zip]
   [:fpfuelstation/latitude  :latitude]
   [:fpfuelstation/longitude :longitude]
   [:fpfuelstation/location  :location]])

(defn fuelstation-deps
  ([db-spec]
   (fuelstation-deps db-spec :fpfuelstation/user-id))
  ([db-spec user-id-or-key]
   [[#(usercore/load-user-by-id db-spec %) user-id-or-key val/sfs-user-does-not-exist]]))

(defn- location-pt
  [fuelstation]
  (let [latitude (:fpfuelstation/latitude fuelstation)
        longitude (:fpfuelstation/longitude fuelstation)
        loc-pt (when (and (not (nil? latitude)) (not (nil? longitude)))
                 (PGgeometry/geomFromString (format "POINT(%s %s)" longitude latitude)))]
    (when (not (nil? loc-pt))
      (do
        (.setSrid loc-pt 4326)
        (PGgeometry. loc-pt)))))

(defn save-new-fuelstation
  [db-spec user-id new-fuelstation-id fuelstation]
  (let [type-id (:fpfuelstation/type-id fuelstation)
        type-id (if (nil? type-id) 0 type-id)
        loc-pt (location-pt fuelstation)]
    (jcore/save-new-entity db-spec
                           new-fuelstation-id
                           (-> fuelstation
                               (assoc :fpfuelstation/type-id type-id)
                               (assoc :fpfuelstation/location loc-pt))
                           val/create-fuelstation-validation-mask
                           val/sfs-any-issues
                           fuelstation-by-id
                           :fuelstation
                           fuelstation-key-pairs
                           {:user_id user-id}
                           :fpfuelstation/created-at
                           :fpfuelstation/updated-at
                           nil
                           (fuelstation-deps db-spec user-id))))

(defn save-fuelstation
  ([db-spec fuelstation-id fuelstation]
   (save-fuelstation db-spec fuelstation-id fuelstation nil))
  ([db-spec fuelstation-id fuelstation if-unmodified-since]
   (jcore/save-entity db-spec
                      fuelstation-id
                      (assoc fuelstation :fpfuelstation/location (location-pt fuelstation))
                      val/save-fuelstation-validation-mask
                      val/sfs-any-issues
                      fuelstation-by-id
                      :fuelstation
                      fuelstation-key-pairs
                      :fpfuelstation/updated-at
                      nil
                      (fuelstation-deps db-spec)
                      if-unmodified-since)))

(defn fuelstations-for-user
  ([db-spec user-id]
   (fuelstations-for-user db-spec user-id true))
  ([db-spec user-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-fuelstation
                               "user_id"
                               "="
                               user-id
                               "updated_at"
                               "desc"
                               rs->fuelstation
                               active-only)))

(defn fuelstations-modified-since
  [db-spec user-id modified-since]
  (jcore/entities-modified-since db-spec
                                 fpddl/tbl-fuelstation
                                 "user_id"
                                 "="
                                 user-id
                                 "updated_at"
                                 "deleted_at"
                                 modified-since
                                 :fpfuelstation/id
                                 :fpfuelstation/deleted-at
                                 :fpfuelstation/updated-at
                                 rs->fuelstation))

(defn fuelstation-by-id
  ([db-spec fuelstation-id]
   (fuelstation-by-id db-spec fuelstation-id true))
  ([db-spec fuelstation-id active-only]
   (jcore/load-entity-by-col db-spec fpddl/tbl-fuelstation "id" "=" fuelstation-id rs->fuelstation active-only)))

(defn fplogs-for-fuelstation
  ([db-spec fuelstation-id]
   (fplogs-for-fuelstation db-spec fuelstation-id true))
  ([db-spec fuelstation-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-fplog
                               "fuelstation_id"
                               "="
                               fuelstation-id
                               "purchased_at"
                               "desc"
                               rs->fplog
                               active-only)))

(defn mark-fuelstation-as-deleted
  [db-spec fuelstation-id if-unmodified-since]
  (let [deleted-fuelstation-result
        (jcore/mark-entity-as-deleted db-spec
                                      fuelstation-id
                                      fuelstation-by-id
                                      :fuelstation
                                      :fpfuelstation/updated-at
                                      if-unmodified-since)]
    (let [fplogs-to-delete (fplogs-for-fuelstation db-spec fuelstation-id)]
      (doseq [[fplog-id fplog] fplogs-to-delete]
        (mark-fplog-as-deleted db-spec fplog-id (:fplog/updated-at fplog))))
    deleted-fuelstation-result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def vehicle-key-pairs
  [[:fpvehicle/user-id                  :user_id]
   [:fpvehicle/default-octane           :default_octane]
   [:fpvehicle/is-diesel                :is_diesel]
   [:fpvehicle/has-dte-readout          :has_dte_readout]
   [:fpvehicle/has-mpg-readout          :has_mpg_readout]
   [:fpvehicle/has-mph-readout          :has_mph_readout]
   [:fpvehicle/has-outside-temp-readout :has_outside_temp_readout]
   [:fpvehicle/fuel-capacity            :fuel_capacity]
   [:fpvehicle/vin                      :vin]
   [:fpvehicle/plate                    :plate]
   [:fpvehicle/name                     :name]])

(defn vehicle-deps
  ([db-spec]
   (vehicle-deps db-spec :fpvehicle/user-id))
  ([db-spec user-id-or-key]
   [[#(usercore/load-user-by-id db-spec %) user-id-or-key val/sv-user-does-not-exist]]))

(defn vehicle-by-id
  ([db-spec vehicle-id]
   (vehicle-by-id db-spec vehicle-id true))
  ([db-spec vehicle-id active-only]
   (jcore/load-entity-by-col db-spec fpddl/tbl-vehicle "id" "=" vehicle-id rs->vehicle active-only)))

(def vehicle-uniq-constraints
  [[fpddl/constr-vehicle-uniq-name val/sv-vehicle-already-exists]])

(defn save-new-vehicle
  [db-spec user-id new-vehicle-id vehicle]
  (jcore/save-new-entity db-spec
                         new-vehicle-id
                         vehicle
                         val/create-vehicle-validation-mask
                         val/sv-any-issues
                         vehicle-by-id
                         :vehicle
                         vehicle-key-pairs
                         {:user_id user-id}
                         :fpvehicle/created-at
                         :fpvehicle/updated-at
                         vehicle-uniq-constraints
                         (vehicle-deps db-spec user-id)))

(defn save-vehicle
  ([db-spec vehicle-id vehicle]
   (save-vehicle db-spec vehicle-id vehicle nil))
  ([db-spec vehicle-id vehicle if-unmodified-since]
   (jcore/save-entity db-spec
                      vehicle-id
                      vehicle
                      val/save-vehicle-validation-mask
                      val/sv-any-issues
                      vehicle-by-id
                      :vehicle
                      vehicle-key-pairs
                      :fpvehicle/updated-at
                      vehicle-uniq-constraints
                      (vehicle-deps db-spec)
                      if-unmodified-since)))

(defn vehicles-for-user
  ([db-spec user-id]
   (vehicles-for-user db-spec user-id true))
  ([db-spec user-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-vehicle
                               "user_id"
                               "="
                               user-id
                               "updated_at"
                               "desc"
                               rs->vehicle
                               active-only)))

(defn vehicles-modified-since
  [db-spec user-id modified-since]
  (jcore/entities-modified-since db-spec
                                 fpddl/tbl-vehicle
                                 "user_id"
                                 "="
                                 user-id
                                 "updated_at"
                                 "deleted_at"
                                 modified-since
                                 :fpvehicle/id
                                 :fpvehicle/deleted-at
                                 :fpvehicle/updated-at
                                 rs->vehicle))

(defn fplogs-for-vehicle
  ([db-spec vehicle-id]
   (fplogs-for-vehicle db-spec vehicle-id true))
  ([db-spec vehicle-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-fplog
                               "vehicle_id"
                               "="
                               vehicle-id
                               "purchased_at"
                               "desc"
                               rs->fplog
                               active-only)))

(defn envlogs-for-vehicle
  ([db-spec vehicle-id]
   (envlogs-for-vehicle db-spec vehicle-id true))
  ([db-spec vehicle-id active-only]
   (jcore/load-entities-by-col db-spec
                               fpddl/tbl-envlog
                               "vehicle_id"
                               "="
                               vehicle-id
                               "logged_at"
                               "desc"
                               rs->envlog
                               active-only)))

(defn mark-vehicle-as-deleted
  [db-spec vehicle-id if-unmodified-since]
  (let [deleted-vehicle-result
        (jcore/mark-entity-as-deleted db-spec
                                      vehicle-id
                                      vehicle-by-id
                                      :vehicle
                                      :fpvehicle/updated-at
                                      if-unmodified-since)]
    (let [fplogs-to-delete (fplogs-for-vehicle db-spec vehicle-id)]
      (doseq [[fplog-id fplog] fplogs-to-delete]
        (mark-fplog-as-deleted db-spec fplog-id (:fplog/updated-at fplog))))
    (let [envlogs-to-delete (envlogs-for-vehicle db-spec vehicle-id)]
      (doseq [[envlog-id envlog] envlogs-to-delete]
        (mark-envlog-as-deleted db-spec envlog-id (:envlog/updated-at envlog))))
    deleted-vehicle-result))
