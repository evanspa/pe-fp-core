(ns pe-fp-core.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as j]
            [pe-fp-core.core :as core]
            [pe-user-core.ddl :as uddl]
            [pe-fp-core.ddl :as fpddl]
            [pe-jdbc-utils.core :as jcore]
            [pe-user-core.core :as usercore]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [pe-fp-core.validation :as val]
            [clojure.tools.logging :as log]
            [pe-fp-core.test-utils :refer [db-spec-without-db
                                           db-spec
                                           db-name]]
            [pe-core-utils.core :as ucore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (fn [f]
                      ;; Database setup
                      (jcore/drop-database db-spec-without-db db-name)
                      (jcore/create-database db-spec-without-db db-name)

                      ;; User / auth-token setup
                      (j/db-do-commands db-spec
                                        true
                                        uddl/schema-version-ddl
                                        uddl/v0-create-user-account-ddl
                                        uddl/v0-add-unique-constraint-user-account-email
                                        uddl/v0-add-unique-constraint-user-account-username
                                        uddl/v0-create-authentication-token-ddl)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (uddl/v0-create-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (uddl/v0-create-user-account-updated-count-trigger-fn db-spec))

                      ;; Vehicle setup
                      (j/db-do-commands db-spec
                                        true
                                        fpddl/v0-create-vehicle-ddl
                                        fpddl/v0-add-unique-constraint-vehicle-name)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-vehicle-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-vehicle-updated-count-trigger-fn db-spec))

                      ;; Fuelstation setup
                      (j/db-do-commands db-spec
                                        true
                                        fpddl/v0-create-fuelstation-ddl
                                        fpddl/v0-create-index-on-fuelstation-name)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-fuelstation-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-fuelstation-updated-count-trigger-fn db-spec))
                      (f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(deftest FuelPurchaseLogs
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

#_(deftest EnvironmentLogs
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

(deftest Fuelstation
  (testing "Saving (and then loading) fuelstation"
    (j/with-db-transaction [conn db-spec]
      (let [new-user-id-1 (usercore/next-user-account-id conn)
            new-user-id-2 (usercore/next-user-account-id conn)
            new-fuelstation-id-1 (core/next-fuelstation-id conn)
            new-fuelstation-id-2 (core/next-fuelstation-id conn)
            new-fuelstation-id-3 (core/next-fuelstation-id conn)
            t1 (t/now)
            t2 (t/now)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/created-at t1
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/created-at t1
                                 :user/password "insecure2"})
        (is (empty? (core/fuelstations-for-user conn new-user-id-1)))
        (is (empty? (core/fuelstations-for-user conn new-user-id-2)))
        (core/save-new-fuelstation conn
                                   new-user-id-1
                                   new-fuelstation-id-1
                                   {:fpfuelstation/created-at t2
                                    :fpfuelstation/name "7-Eleven"
                                    :fpfuelstation/street "110 Maple Street"
                                    :fpfuelstation/city "Mayberry"
                                    :fpfuelstation/state "SC"
                                    :fpfuelstation/zip "28277"
                                    :fpfuelstation/latitude 35.050825
                                    :fpfuelstation/longitude -80.819054})
        (is (= 1 (count (core/fuelstations-for-user conn new-user-id-1))))
        (is (empty? (core/fuelstations-for-user conn new-user-id-2)))
        (core/save-new-fuelstation conn
                                   new-user-id-1
                                   new-fuelstation-id-2
                                   {:fpfuelstation/created-at t2
                                    :fpfuelstation/name "Quick Mart"
                                    :fpfuelstation/street "112 Broad Street"
                                    :fpfuelstation/city "Charlotte"
                                    :fpfuelstation/state "NC"
                                    :fpfuelstation/zip "28272"
                                    :fpfuelstation/latitude 33.050825
                                    :fpfuelstation/longitude -79.819054})
        (is (= 2 (count (core/fuelstations-for-user conn new-user-id-1))))
        (is (empty? (core/fuelstations-for-user conn new-user-id-2)))
        (core/save-new-fuelstation conn
                                   new-user-id-2
                                   new-fuelstation-id-3
                                   {:fpfuelstation/created-at t2
                                    :fpfuelstation/name "Stewart's"
                                    :fpfuelstation/street "94 Union Street"
                                    :fpfuelstation/city "Schenectady"
                                    :fpfuelstation/state "NY"
                                    :fpfuelstation/zip "12309"
                                    :fpfuelstation/latitude 32.050825
                                    :fpfuelstation/longitude -69.819054})
        (is (= 2 (count (core/fuelstations-for-user conn new-user-id-1))))
        (is (= 1 (count (core/fuelstations-for-user conn new-user-id-2))))
        (let [[fuelstation-id fuelstation] (first (core/fuelstations-for-user conn new-user-id-2))]
          (is (= fuelstation-id new-fuelstation-id-3))
          (is (= fuelstation-id (:fpfuelstation/id fuelstation)))
          (is (= new-user-id-2 (:fpfuelstation/user-id fuelstation)))
          (is (= t2 (:fpfuelstation/created-at fuelstation)))
          (is (= t2 (:fpfuelstation/updated-at fuelstation)))
          (is (= 1 (:fpfuelstation/updated-count fuelstation)))
          (is (= "Stewart's" (:fpfuelstation/name fuelstation)))
          (is (= "94 Union Street" (:fpfuelstation/street fuelstation)))
          (is (= "Schenectady" (:fpfuelstation/city fuelstation)))
          (is (= "NY" (:fpfuelstation/state fuelstation)))
          (is (= "12309" (:fpfuelstation/zip fuelstation)))
          (is (= 32.050825 (:fpfuelstation/latitude fuelstation)))
          (is (= -69.819054 (:fpfuelstation/longitude fuelstation))))
        (core/save-fuelstation conn new-fuelstation-id-3 {:fpfuelstation/user-id new-user-id-1})
        (is (= 3 (count (core/fuelstations-for-user conn new-user-id-1))))
        (is (= 0 (count (core/fuelstations-for-user conn new-user-id-2))))))))

(deftest Vehicles
  (testing "Saving (and then loading) vehicles"
    (j/with-db-transaction [conn db-spec]
      (let [new-user-id-1 (usercore/next-user-account-id conn)
            new-user-id-2 (usercore/next-user-account-id conn)
            new-vehicle-id-1 (core/next-vehicle-id conn)
            new-vehicle-id-2 (core/next-vehicle-id conn)
            new-vehicle-id-3 (core/next-vehicle-id conn)
            t1 (t/now)
            t2 (t/now)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/created-at t1
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/created-at t1
                                 :user/password "insecure2"})
        (is (empty? (core/vehicles-for-user conn new-user-id-1)))
        (is (empty? (core/vehicles-for-user conn new-user-id-2)))
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               {:fpvehicle/created-at t2
                                :fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87})
        (is (= 1 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (empty? (core/vehicles-for-user conn new-user-id-2)))
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-2
                               {:fpvehicle/created-at t2
                                :fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})
        (is (= 2 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (empty? (core/vehicles-for-user conn new-user-id-2)))
        (core/save-new-vehicle conn
                               new-user-id-2
                               new-vehicle-id-3
                               {:fpvehicle/created-at t2
                                :fpvehicle/name "Honda Accord"
                                :fpvehicle/default-octane 89})
        (is (= 2 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (= 1 (count (core/vehicles-for-user conn new-user-id-2))))
        (let [[vehicle-id vehicle] (first (core/vehicles-for-user conn new-user-id-2))]
          (is (= vehicle-id new-vehicle-id-3))
          (is (= vehicle-id (:fpvehicle/id vehicle)))
          (is (= new-user-id-2 (:fpvehicle/user-id vehicle)))
          (is (= t2 (:fpvehicle/created-at vehicle)))
          (is (= t2 (:fpvehicle/updated-at vehicle)))
          (is (= 89 (:fpvehicle/default-octane vehicle)))
          (is (= 1 (:fpvehicle/updated-count vehicle)))
          (is (= "Honda Accord" (:fpvehicle/name vehicle))))
        (core/save-vehicle conn new-vehicle-id-3 {:fpvehicle/user-id new-user-id-1})
        (is (= 3 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (= 0 (count (core/vehicles-for-user conn new-user-id-2))))))))
