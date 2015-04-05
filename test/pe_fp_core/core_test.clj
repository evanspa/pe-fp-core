(ns pe-fp-core.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :refer [resource]]
            [datomic.api :as d]
            [pe-fp-core.core :as core]
            [pe-datomic-utils.core :as ducore]
            [pe-datomic-testutils.core :as dtucore]
            [pe-user-core.core :as usercore]
            [pe-fp-core.test-utils :refer [user-schema-files
                                           fp-schema-files
                                           db-uri
                                           fp-partition]]
            [pe-core-utils.core :as ucore]))

(def conn (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (dtucore/make-db-refresher-fixture-fn db-uri
                                                          conn
                                                          fp-partition
                                                          (concat user-schema-files
                                                                  fp-schema-files)))

(defn- save-new-user
  [conn user]
  (ducore/save-new-entity conn (usercore/save-new-user-txnmap fp-partition user)))

(defn- save-new-vehicle
  [conn user-entid vehicle]
  (ducore/save-new-entity conn (core/save-new-vehicle-txnmap fp-partition user-entid vehicle)))

(defn- save-new-fuelstation
  [conn user-entid fuelstation]
  (ducore/save-new-entity conn (core/save-new-fuelstation-txnmap fp-partition user-entid fuelstation)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest FuelPurchaseLogs
  (testing "Saving (and then loading) fuel purchase logs"
    (let [u-entid (save-new-user @conn {:user/username "smithj"
                                        :user/password "insecure"})
          v-entid (save-new-vehicle @conn u-entid {:fpvehicle/name "300ZX"})
          fs-entid (save-new-fuelstation @conn u-entid {:fpfuelstation/name "Sunoco"})]
      (is (= (count (core/fplogs-for-user @conn u-entid)) 0))
      @(d/transact @conn
                   [(core/save-new-fplog-txnmap fp-partition
                                                u-entid
                                                {:fpfuelpurchaselog/vehicle v-entid
                                                 :fpfuelpurchaselog/fuelstation fs-entid
                                                 :fpfuelpurchaselog/purchase-date (ucore/rfc7231str->instant "Mon, 01 Sep 2014 11:25:57 GMT")
                                                 :fpfuelpurchaselog/num-gallons 12.4
                                                 :fpfuelpurchaselog/gallon-price 3.69
                                                 :fpfuelpurchaselog/carwash-per-gal-discount 0.08
                                                 :fpfuelpurchaselog/got-car-wash false
                                                 :fpfuelpurchaselog/octane 87})])
      (let [fplogs (core/fplogs-for-user @conn u-entid)]
        (is (= (count fplogs) 1))
        (let [[fplog-entid fplog] (first fplogs)]
          (is (= "Mon, 01 Sep 2014 11:25:57 GMT" (ucore/instant->rfc7231str (:fpfuelpurchaselog/purchase-date fplog))))
          @(d/transact @conn
                       [(core/save-fplog-txnmap u-entid
                                                fplog-entid
                                                {:fpfuelpurchaselog/vehicle v-entid
                                                 :fpfuelpurchaselog/fuelstation fs-entid
                                                 :fpfuelpurchaselog/purchase-date (ucore/rfc7231str->instant "Fri, 31 Oct 2014 09:25:57 GMT")
                                                 :fpfuelpurchaselog/num-gallons 12.2
                                                 :fpfuelpurchaselog/gallon-price 3.71
                                                 :fpfuelpurchaselog/carwash-per-gal-discount 0.09
                                                 :fpfuelpurchaselog/got-car-wash true
                                                 :fpfuelpurchaselog/octane 91})])
          (let [fplogs (core/fplogs-for-user @conn u-entid)]
            (is (= (count fplogs) 1))
            (let [[fplog-entid fplog] (first fplogs)]
              (is (= "Fri, 31 Oct 2014 09:25:57 GMT" (ucore/instant->rfc7231str (:fpfuelpurchaselog/purchase-date fplog))))
              ))
          @(d/transact @conn
                       [(core/save-new-fplog-txnmap fp-partition
                                                    u-entid
                                                    {:fpfuelpurchaselog/vehicle v-entid
                                                     :fpfuelpurchaselog/fuelstation fs-entid
                                                     :fpfuelpurchaselog/purchase-date (ucore/rfc7231str->instant "Mon, 01 Sep 2014 11:25:57 GMT")
                                                     :fpfuelpurchaselog/num-gallons 12.8
                                                     :fpfuelpurchaselog/gallon-price 3.74
                                                     :fpfuelpurchaselog/octane 93})])
          @(d/transact @conn
                       [(core/save-new-fplog-txnmap fp-partition
                                                    u-entid
                                                    {:fpfuelpurchaselog/vehicle v-entid
                                                     :fpfuelpurchaselog/fuelstation fs-entid
                                                     :fpfuelpurchaselog/purchase-date (ucore/rfc7231str->instant "Mon, 01 Sep 2014 11:25:57 GMT")
                                                     :fpfuelpurchaselog/num-gallons 12.9
                                                     :fpfuelpurchaselog/gallon-price 3.84
                                                     :fpfuelpurchaselog/octane 94})])
          (is (= (count (core/fplogs-for-user @conn u-entid)) 3)))))))

(deftest EnvironmentLogs
  (testing "Saving (and then loading) environment logs"
    (let [u-entid (save-new-user @conn {:user/username "smithj"
                                        :user/password "insecure"})
          v-entid (save-new-vehicle @conn u-entid {:fpvehicle/name "300ZX"})]
      (is (= (count (core/envlogs-for-user @conn u-entid)) 0))
      @(d/transact @conn
                   [(core/save-new-envlog-txnmap fp-partition
                                                 u-entid
                                                 {:fpenvironmentlog/vehicle v-entid
                                                  :fpenvironmentlog/log-date (ucore/rfc7231str->instant "Tue, 02 Sep 2014 08:03:12 GMT")
                                                  :fpenvironmentlog/odometer 54836.0
                                                  :fpenvironmentlog/outside-temp 67.0})])
      (let [envlogs (core/envlogs-for-user @conn u-entid)]
        (is (= (count envlogs) 1))
        (let [[envlog-entid envlog] (first envlogs)]
          (is (= "Tue, 02 Sep 2014 08:03:12 GMT" (ucore/instant->rfc7231str (:fpenvironmentlog/log-date envlog))))
          (is (= (float 54836.0) (:fpenvironmentlog/odometer envlog)))
          (is (= (float 67.0) (:fpenvironmentlog/outside-temp envlog)))
          @(d/transact @conn
                       [(core/save-envlog-txnmap u-entid
                                                 envlog-entid
                                                 {:fpenvironmentlog/vehicle v-entid
                                                  :fpenvironmentlog/log-date (ucore/rfc7231str->instant "Tue, 02 Sep 2014 08:04:13 GMT")
                                                  :fpenvironmentlog/odometer 54837.0
                                                  :fpenvironmentlog/outside-temp 68.0
                                                  :fpenvironmentlog/reported-avg-mpg 24.3
                                                  :fpenvironmentlog/reported-avg-mph 25.7
                                                  :fpenvironmentlog/dte 137})])
          (let [envlogs (core/envlogs-for-user @conn u-entid)]
            (is (= (count envlogs) 1))
            (let [[envlog-entid envlog] (first envlogs)]
              (is (= "Tue, 02 Sep 2014 08:04:13 GMT" (ucore/instant->rfc7231str (:fpenvironmentlog/log-date envlog))))
              (is (= 54837.0 (:fpenvironmentlog/odometer envlog)))
              (is (= (float 68.0) (:fpenvironmentlog/outside-temp envlog)))
              (is (= 24.3 (:fpenvironmentlog/reported-avg-mpg envlog)))
              (is (= 25.7 (:fpenvironmentlog/reported-avg-mph envlog)))
              (is (= 137 (:fpenvironmentlog/dte envlog)))))))
      @(d/transact @conn
                   [(core/save-new-envlog-txnmap fp-partition
                                                 u-entid
                                                 {:fpenvironmentlog/vehicle v-entid
                                                  :fpenvironmentlog/log-date (ucore/rfc7231str->instant "Wed, 03 Sep 2014 08:03:12 GMT")
                                                  :fpenvironmentlog/odometer 54846.0
                                                  :fpenvironmentlog/outside-temp 77.0})])
      @(d/transact @conn
                   [(core/save-new-envlog-txnmap fp-partition
                                                 u-entid
                                                 {:fpenvironmentlog/vehicle v-entid
                                                  :fpenvironmentlog/log-date (ucore/rfc7231str->instant "Thu, 04 Sep 2014 08:03:12 GMT")
                                                  :fpenvironmentlog/odometer 64846.0
                                                  :fpenvironmentlog/outside-temp 87.0})])
      (is (= (count (core/envlogs-for-user @conn u-entid)) 3)))))

(deftest FuelStations
  (testing "Saving (and then loading) fuel stations"
    (let [u-entid (save-new-user @conn {:user/username "smithj"
                                        :user/password "insecure"})]
      (is (= (count (core/fuelstations-for-user @conn u-entid)) 0))
      @(d/transact @conn
                   [(core/save-new-fuelstation-txnmap fp-partition
                                                      u-entid
                                                      {:fpfuelstation/name "Seven Eleven"})])
      (let [fuelstations (core/fuelstations-for-user @conn u-entid)]
        (is (= (count fuelstations) 1))
        (let [[fuelstation-entid fuelstation] (first fuelstations)]
          (is (= "Seven Eleven" (:fpfuelstation/name fuelstation)))
          @(d/transact @conn
                       [(core/save-fuelstation-txnmap u-entid
                                                      fuelstation-entid
                                                      {:fpfuelstation/name "7-Eleven"
                                                       :fpfuelstation/street "Main Street"
                                                       :fpfuelstation/city "Charlotte"
                                                       :fpfuelstation/state "NC"
                                                       :fpfuelstation/zip "28277"
                                                       :fpfuelstation/latitude 35.050825
                                                       :fpfuelstation/longitude -80.819054})])
          (let [fuelstations (core/fuelstations-for-user @conn u-entid)]
            (is (= (count fuelstations) 1))
            (let [[fuelstation-entid fuelstation] (first fuelstations)]
              (is (= "7-Eleven" (:fpfuelstation/name fuelstation)))
              (is (= "Main Street" (:fpfuelstation/street fuelstation)))
              (is (= "Charlotte" (:fpfuelstation/city fuelstation)))
              (is (= "NC" (:fpfuelstation/state fuelstation)))
              (is (= "28277" (:fpfuelstation/zip fuelstation)))
              (is (= 35.050825 (:fpfuelstation/latitude fuelstation)))
              (is (= -80.819054 (:fpfuelstation/longitude fuelstation)))))))
      @(d/transact @conn
                   [(core/save-new-fuelstation-txnmap fp-partition
                                                      u-entid
                                                      {:fpfuelstation/name "Stewart's"})])
      @(d/transact @conn
                   [(core/save-new-fuelstation-txnmap fp-partition
                                                      u-entid
                                                      {:fpfuelstation/name "Cumby Farms"})])
      (is (= (count (core/fuelstations-for-user @conn u-entid)) 3))
      (let [u2-entid (save-new-user @conn {:user/username "cutterm"
                                           :user/password "abc123"})]
        (is (= (count (core/fuelstations-for-user @conn u2-entid)) 0))))))

(deftest Vehicles
  (testing "Saving (and then loading) vehicles"
    (let [u-entid (save-new-user @conn {:user/username "smithj"
                                        :user/password "insecure"})]
      (is (= (count (core/vehicles-for-user @conn u-entid)) 0))
      @(d/transact @conn
                   [(core/save-new-vehicle-txnmap fp-partition
                                                  u-entid
                                                  {:fpvehicle/name "Volkswagen"
                                                   :fpvehicle/fuel-capacity 19.2
                                                   :fpvehicle/min-reqd-octane 91})])
      (let [vehicles (core/vehicles-for-user @conn u-entid)]
        (is (= (count vehicles) 1))
        (let [[vehicle-entid vehicle] (first vehicles)]
          (is (= "Volkswagen" (:fpvehicle/name vehicle)))
          (is (= 19.2 (:fpvehicle/fuel-capacity vehicle)))
          (is (= 91 (:fpvehicle/min-reqd-octane vehicle)))
          @(d/transact @conn
                       [(core/save-vehicle-txnmap u-entid
                                                  vehicle-entid
                                                  {:fpvehicle/name "VW"
                                                   :fpvehicle/fuel-capacity 19.3
                                                   :fpvehicle/min-reqd-octane 93})])
          (let [vehicles (core/vehicles-for-user @conn u-entid)]
            (is (= (count vehicles) 1))
            (let [[vehicle-entid vehicle] (first vehicles)]
              (is (= "VW" (:fpvehicle/name vehicle)))
              (is (= 19.3 (:fpvehicle/fuel-capacity vehicle)))
              (is (= 93 (:fpvehicle/min-reqd-octane vehicle)))))
          (let [vehicles (core/vehicles-for-user-by-name @conn u-entid "foo")]
            (is (= (count vehicles) 0))
            (let [vehicles (core/vehicles-for-user-by-name @conn u-entid "VW")]
              (is (= (count vehicles) 1))))
          (let [[vehicle-entid vehicle] (core/vehicle-for-user-by-id @conn u-entid vehicle-entid)]
            (is (not (nil? vehicle-entid)))
            (is (not (nil? vehicle)))
            (is (= "VW" (:fpvehicle/name vehicle)))
            (is (= 19.3 (:fpvehicle/fuel-capacity vehicle)))
            (is (= 93 (:fpvehicle/min-reqd-octane vehicle))))
          (let [junk-veh-entid 920201935]
            (is (nil? (core/vehicle-for-user-by-id @conn u-entid junk-veh-entid))))
          (let [junk-veh-entid 18]
            (is (nil? (core/vehicle-for-user-by-id @conn u-entid junk-veh-entid))))))
      @(d/transact @conn
                   [(core/save-new-vehicle-txnmap fp-partition
                                                  u-entid
                                                  {:fpvehicle/name "BMW 328i"
                                                   :fpvehicle/fuel-capacity 20.4
                                                   :fpvehicle/min-reqd-octane 93})])
      @(d/transact @conn
                   [(core/save-new-vehicle-txnmap fp-partition
                                                  u-entid
                                                  {:fpvehicle/name "300ZX"
                                                   :fpvehicle/fuel-capacity 21.7
                                                   :fpvehicle/min-reqd-octane 94})])
      (is (= (count (core/vehicles-for-user @conn u-entid)) 3))
      (let [u2-entid (save-new-user @conn {:user/username "cutterm"
                                           :user/password "abc123"})]
        (is (= (count (core/vehicles-for-user @conn u2-entid)) 0))))))
