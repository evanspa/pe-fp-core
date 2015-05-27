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

(defn rs->vehicle
  [vehicle-rs]
  (let [from-sql-time-fn #(c/from-sql-time %)]
    [(:id vehicle-rs) (-> vehicle-rs
                       (ucore/replace-if-contains :name :fpvehicle/name)
                       (ucore/replace-if-contains :default_octane :fpvehicle/default-octane)
                       (ucore/replace-if-contains :updated_count :fpvehicle/updated-count)
                       (ucore/replace-if-contains :user_id :fpvehicle/user-id)
                       (ucore/replace-if-contains :id :fpvehicle/id)
                       (ucore/replace-if-contains :updated_at :fpvehicle/updated-at from-sql-time-fn)
                       (ucore/replace-if-contains :deleted_at :fpvehicle/deleted-at from-sql-time-fn)
                       (ucore/replace-if-contains :created_at :fpvehicle/created-at from-sql-time-fn))]))

(defn rs->fuelstation
  [fuelstation-rs]
  (let [from-sql-time-fn #(c/from-sql-time %)]
    [(:id fuelstation-rs) (-> fuelstation-rs
                              (ucore/replace-if-contains :name :fpfuelstation/name)
                              (ucore/replace-if-contains :street :fpfuelstation/street)
                              (ucore/replace-if-contains :city :fpfuelstation/city)
                              (ucore/replace-if-contains :state :fpfuelstation/state)
                              (ucore/replace-if-contains :zip :fpfuelstation/zip)
                              (ucore/replace-if-contains :latitude :fpfuelstation/latitude)
                              (ucore/replace-if-contains :longitude :fpfuelstation/longitude)
                              (ucore/replace-if-contains :updated_count :fpfuelstation/updated-count)
                              (ucore/replace-if-contains :user_id :fpfuelstation/user-id)
                              (ucore/replace-if-contains :id :fpfuelstation/id)
                              (ucore/replace-if-contains :updated_at :fpfuelstation/updated-at from-sql-time-fn)
                              (ucore/replace-if-contains :deleted_at :fpfuelstation/deleted-at from-sql-time-fn)
                              (ucore/replace-if-contains :created_at :fpfuelstation/created-at from-sql-time-fn))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Purchase log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(defn- fplog-txnmap
  [user-entid fplog-entid fplog]
  (merge
   {:fpfuelpurchaselog/user user-entid
    :db/id fplog-entid}
   fplog))

#_(defn save-fplog-txnmap
  [user-entid fplog-entid fplog]
  (let [validation-mask (val/save-fuelpurchaselog-validation-mask fplog)]
    (if (pos? (bit-and validation-mask val/savefuelpurchaselog-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (fplog-txnmap user-entid
                    fplog-entid
                    fplog))))

#_(defn save-new-fplog-txnmap
  [partition user-entid fplog]
  (save-fplog-txnmap user-entid
                     (d/tempid partition)
                     fplog))

#_(defn fplogs-for-user
  [conn user-entid]
  {:pre [(not (nil? user-entid))]}
  (let [db (d/db conn)
        fplogs (q '[:find ?v
                    :in $ ?user-entid
                    :where [$ ?v :fpfuelpurchaselog/user ?user-entid]]
                  db
                  user-entid)]
    (map #(let [fplog-entid (first %)]
            [fplog-entid (-> {}
                             (into (d/entity db (first %))))])
         fplogs)))

#_(defn fplog-for-user-by-id
  [conn user-entid fplog-entid]
  (let [fplog (ducore/entity-for-parent-by-id conn user-entid fplog-entid :fpfuelpurchaselog/user)]
    fplog))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(defn- envlog-txnmap
  [user-entid envlog-entid envlog]
  (merge
   {:fpenvironmentlog/user user-entid
    :db/id envlog-entid}
   envlog))

#_(defn save-envlog-txnmap
  [user-entid envlog-entid envlog]
  (let [validation-mask (val/save-environmentlog-validation-mask envlog)]
    (if (pos? (bit-and validation-mask val/saveenvironmentlog-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (envlog-txnmap user-entid envlog-entid envlog))))

#_(defn save-new-envlog-txnmap
  [partition user-entid envlog]
  (save-envlog-txnmap user-entid (d/tempid partition) envlog))

#_(defn envlogs-for-user
  [conn user-entid]
  {:pre [(not (nil? user-entid))]}
  (let [db (d/db conn)
        envlogs (q '[:find ?v
                     :in $ ?user-entid
                     :where [$ ?v :fpenvironmentlog/user ?user-entid]]
                   db
                   user-entid)]
    (map #(let [envlog-entid (first %)]
            [envlog-entid (-> {}
                              (into (d/entity db (first %))))])
         envlogs)))

#_(defn envlog-for-user-by-id
  [conn user-entid envlog-entid]
  (ducore/entity-for-parent-by-id conn user-entid envlog-entid :fpenvironmentlog/user))

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
                   {:user_id user-id
                    :id new-fuelstation-id
                    :name (:fpfuelstation/name fuelstation)
                    :street (:fpfuelstation/street fuelstation)
                    :city (:fpfuelstation/city fuelstation)
                    :state (:fpfuelstation/state fuelstation)
                    :zip (:fpfuelstation/zip fuelstation)
                    :latitude (:fpfuelstation/latitude fuelstation)
                    :longitude (:fpfuelstation/longitude fuelstation)
                    :created_at created-at
                    :updated_at created-at
                    :updated_count 1})))))

(defn save-fuelstation
  [db-spec fuelstation-id fuelstation]
  (j/update! db-spec
             :fuelstation
             (-> fuelstation
                 (dissoc :updated_count)
                 (dissoc :fpfuelstation/updated-count)
                 (ucore/replace-if-contains :fpfuelstation/user-id :user_id)
                 (ucore/replace-if-contains :fpfuelstation/updated-at :updated_at c/to-timestamp)
                 (ucore/replace-if-contains :fpfuelstation/deleted-at :deleted_at c/to-timestamp)
                 (ucore/replace-if-contains :fpfuelstation/name :name)
                 (ucore/replace-if-contains :fpfuelstation/street :street)
                 (ucore/replace-if-contains :fpfuelstation/city :city)
                 (ucore/replace-if-contains :fpfuelstation/state :state)
                 (ucore/replace-if-contains :fpfuelstation/zip :zip)
                 (ucore/replace-if-contains :fpfuelstation/latitude :latitude)
                 (ucore/replace-if-contains :fpfuelstation/longitude :longitude))
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
                     {:user_id user-id
                      :id new-vehicle-id
                      :name (:fpvehicle/name vehicle)
                      :default_octane (:fpvehicle/default-octane vehicle)
                      :created_at created-at
                      :updated_at created-at
                      :updated_count 1})
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
                 (ucore/replace-if-contains :fpvehicle/user-id :user_id)
                 (ucore/replace-if-contains :fpvehicle/updated-at :updated_at c/to-timestamp)
                 (ucore/replace-if-contains :fpvehicle/deleted-at :deleted_at c/to-timestamp)
                 (ucore/replace-if-contains :fpvehicle/name :name))
             ["id = ?" vehicle-id]))

(defn vehicles-for-user
  [db-spec user-id]
  (j/query db-spec
           [(format "select * from %s where user_id = ? order by updated_at desc"
                    fpddl/tbl-vehicle)
            user-id]
           :row-fn rs->vehicle))
