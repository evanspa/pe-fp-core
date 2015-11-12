(ns pe-fp-core.admin
  (:require [clojure.tools.logging :as log]
            [pe-fp-core.core :as core]
            [clojure.java.jdbc :as j]
            [pe-user-core.core :as usercore]
            [pe-core-utils.core :as ucore]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(declare copy-user)

(defn copy-user-by-id
  [db-spec user-id new-email new-name new-password]
  (let [[_ user] (usercore/load-user-by-id db-spec user-id)]
    (copy-user db-spec user new-email new-name new-password)))

(defn copy-user-by-email
  [db-spec email new-email new-name new-password]
  (let [[_ user] (usercore/load-user-by-email db-spec email)]
    (copy-user db-spec user new-email new-name new-password)))

(defn copy-user
  [db-spec user new-email new-name new-password]
  (j/with-db-transaction [conn db-spec]
    (let [user-id (:user/id user)
          new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user conn
                              new-user-id
                              {:user/name new-name
                               :user/email new-email
                               :user/password new-password})
      (let [gas-stations (core/fuelstations-for-user conn user-id)
            gas-station-id-map (reduce (fn [g-s-m [id _]]
                                         (assoc g-s-m id (core/next-fuelstation-id conn)))
                                       {}
                                       gas-stations)]
        (do
          (doseq [[gas-station-id gas-station] gas-stations]
            (let [new-gas-station-id (get gas-station-id-map gas-station-id)]
              (core/save-new-fuelstation conn
                                         new-user-id
                                         new-gas-station-id
                                         (-> gas-station
                                             (dissoc :fpfuelstation/id)
                                             (dissoc :fpfuelstation/user-id)))))
          (let [vehicles (core/vehicles-for-user db-spec user-id)]
            (doseq [[vehicle-id vehicle] vehicles]
              (let [new-vehicle-id (core/next-vehicle-id db-spec)]
                (do
                  (core/save-new-vehicle conn
                                         new-user-id
                                         new-vehicle-id
                                         (-> vehicle
                                             (dissoc :fpvehicle/id)
                                             (dissoc :fpvehicle/user-id)))
                  (let [fplogs (core/fplogs-for-vehicle conn vehicle-id)]
                    (doseq [[fplog-id fplog] fplogs]
                      (let [fs-id (:fplog/fuelstation-id fplog)
                            new-fs-id (get gas-station-id-map fs-id)
                            new-fplog-id (core/next-fplog-id conn)
                            num-gallons (:fplog/num-gallons fplog)
                            num-gallons (if (nil? num-gallons) 19 num-gallons)
                            octane (:fplog/octane fplog)
                            octane (if (nil? octane) 87 octane)
                            gallon-price (:fplog/gallon-price fplog)
                            gallon-price (if (nil? gallon-price) 3.99 gallon-price)]
                        (core/save-new-fplog conn
                                             new-user-id
                                             new-vehicle-id
                                             new-fs-id
                                             new-fplog-id
                                             (-> fplog
                                                 (assoc :fplog/num-gallons num-gallons)
                                                 (assoc :fplog/octane octane)
                                                 (assoc :fplog/gallon-price gallon-price)
                                                 (dissoc :fplog/id)
                                                 (dissoc :fplog/vehicle-id)
                                                 (dissoc :fplog/fuelstation-id)
                                                 (dissoc :fplog/user-id))))))
                  (let [envlogs (core/envlogs-for-vehicle conn vehicle-id)]
                    (doseq [[envlog-id envlog] envlogs]
                      (let [new-envlog-id (core/next-envlog-id conn)
                            odometer (:envlog/odometer envlog)
                            odometer (if (nil? odometer) 10000 odometer)]
                        (core/save-new-envlog conn
                                              new-user-id
                                              new-vehicle-id
                                              new-envlog-id
                                              (-> envlog
                                                  (dissoc :envlog/id)
                                                  (dissoc :envlog/vehicle-id)
                                                  (dissoc :envlog/user-id)
                                                  (assoc :envlog/odometer odometer)))))))))))))))
