(ns pe-fp-core.core
  (:require [datomic.api :refer [q db] :as d]
            [pe-fp-core.validation :as val]
            [clojure.tools.logging :as log]
            [pe-datomic-utils.core :as ducore]
            [clj-time.core :as t]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Purchase log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- fplog-txnmap
  [user-entid fplog-entid fplog]
  (merge
   {:fpfuelpurchaselog/user user-entid
    :db/id fplog-entid}
   fplog))

(defn save-fplog-txnmap
  [user-entid fplog-entid fplog]
  (let [validation-mask (val/save-fuelpurchaselog-validation-mask fplog)]
    (if (pos? (bit-and validation-mask val/savefuelpurchaselog-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (fplog-txnmap user-entid
                    fplog-entid
                    fplog))))

(defn save-new-fplog-txnmap
  [partition user-entid fplog]
  (save-fplog-txnmap user-entid
                     (d/tempid partition)
                     fplog))

(defn fplogs-for-user
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

(defn fplog-for-user-by-id
  [conn user-entid fplog-entid]
  (let [fplog (ducore/entity-for-parent-by-id conn user-entid fplog-entid :fpfuelpurchaselog/user)]
    fplog))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- envlog-txnmap
  [user-entid envlog-entid envlog]
  (merge
   {:fpenvironmentlog/user user-entid
    :db/id envlog-entid}
   envlog))

(defn save-envlog-txnmap
  [user-entid envlog-entid envlog]
  (let [validation-mask (val/save-environmentlog-validation-mask envlog)]
    (if (pos? (bit-and validation-mask val/saveenvironmentlog-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (envlog-txnmap user-entid envlog-entid envlog))))

(defn save-new-envlog-txnmap
  [partition user-entid envlog]
  (save-envlog-txnmap user-entid (d/tempid partition) envlog))

(defn envlogs-for-user
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

(defn envlog-for-user-by-id
  [conn user-entid envlog-entid]
  (ducore/entity-for-parent-by-id conn user-entid envlog-entid :fpenvironmentlog/user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Station-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- fuelstation-txnmap
  [user-entid fuelstation-entid fuelstation]
  (merge
   {:fpfuelstation/user user-entid
    :db/id fuelstation-entid}
   fuelstation))

(defn save-fuelstation-txnmap
  [user-entid fuelstation-entid fuelstation]
  (let [validation-mask (val/save-fuelstation-validation-mask fuelstation)]
    (if (pos? (bit-and validation-mask val/savefuelstation-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (fuelstation-txnmap user-entid fuelstation-entid fuelstation))))

(defn save-new-fuelstation-txnmap
  [partition user-entid fuelstation]
  (save-fuelstation-txnmap user-entid (d/tempid partition) fuelstation))

(defn fuelstations-for-user
  [conn user-entid]
  {:pre [(not (nil? user-entid))]}
  (let [db (d/db conn)
        fuelstations (q '[:find ?v
                          :in $ ?user-entid
                          :where [$ ?v :fpfuelstation/user ?user-entid]]
                        db
                        user-entid)]
    (map #(let [fuelpurchase-entid (first %)]
            [fuelpurchase-entid (-> {}
                                    (into (d/entity db (first %))))]) fuelstations)))

(defn fuelstation-for-user-by-id
  [conn user-entid fs-entid]
  (ducore/entity-for-parent-by-id conn user-entid fs-entid :fpfuelstation/user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle-related definitions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- vehicle-txnmap
  [user-entid vehicle-entid vehicle]
  (merge {:db/id vehicle-entid
          :fpvehicle/user user-entid}
         vehicle))

(defn save-vehicle-txnmap
  [user-entid vehicle-entid vehicle]
  (let [validation-mask (val/save-vehicle-validation-mask vehicle)]
    (if (pos? (bit-and validation-mask val/savevehicle-any-issues))
      (throw (IllegalArgumentException. (str validation-mask)))
      (vehicle-txnmap user-entid vehicle-entid vehicle))))

(defn save-new-vehicle-txnmap
  [partition user-entid vehicle]
  (save-vehicle-txnmap user-entid (d/tempid partition) vehicle))

(defn vehicles-for-user-by-name
  [conn user-entid name]
  {:pre [(not (nil? user-entid))]}
  (let [db (d/db conn)
        vehicles (q '[:find ?v
                      :in $ ?user-entid ?vehicle-name
                      :where [$ ?v :fpvehicle/user ?user-entid]
                      [$ ?v :fpvehicle/name ?vehicle-name]]
                    db
                    user-entid
                    name)]
    (map #(let [vehicle-entid (first %)]
            [vehicle-entid (-> {}
                               (into (d/entity db (first %))))]) vehicles)))

(defn vehicle-for-user-by-id
  [conn user-entid vehicle-entid]
  (ducore/entity-for-parent-by-id conn user-entid vehicle-entid :fpvehicle/user))

(defn vehicles-for-user
  [conn user-entid]
  {:pre [(not (nil? user-entid))]}
  (let [db (d/db conn)
        vehicles (q '[:find ?v
                      :in $ ?user-entid
                      :where [$ ?v :fpvehicle/user ?user-entid]]
                    db
                    user-entid)]
    (map #(let [vehicle-entid (first %)]
            [vehicle-entid (-> {}
                               (into (d/entity db (first %))))]) vehicles)))
