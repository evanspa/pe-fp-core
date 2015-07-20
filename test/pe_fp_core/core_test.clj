(ns pe-fp-core.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as j]
            [clojure.pprint :refer (pprint)]
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
                                        fpddl/v0-add-unique-constraint-vehicle-name
                                        fpddl/v1-vehicle-add-fuel-capacity-col
                                        fpddl/v2-vehicle-drop-erroneous-unique-name-constraint
                                        fpddl/v2-vehicle-add-proper-unique-name-constraint)
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

                      ;; Fuel purchase log setup
                      (j/db-do-commands db-spec
                                        true
                                        fpddl/v0-create-fplog-ddl)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-fplog-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-fplog-updated-count-trigger-fn db-spec))

                      ;; Environment log setup
                      (j/db-do-commands db-spec
                                        true
                                        fpddl/v0-create-envlog-ddl)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-envlog-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (fpddl/v0-create-envlog-updated-count-trigger-fn db-spec))
                      (f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest FuelPurchaseLogs
  (testing "Saving (and then loading) fuel purchase logs"
    (j/with-db-transaction [conn db-spec]
      (let [new-user-id-1 (usercore/next-user-account-id conn)
            new-user-id-2 (usercore/next-user-account-id conn)
            new-fuelstation-id-1 (core/next-fuelstation-id conn)
            new-fuelstation-id-2 (core/next-fuelstation-id conn)
            new-vehicle-id-1 (core/next-vehicle-id conn)
            new-vehicle-id-2 (core/next-vehicle-id conn)
            new-fplog-id-1 (core/next-fplog-id conn)
            t1 (t/now)
            t2 (t/now)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/password "insecure2"})
        (core/save-new-fuelstation conn
                                   new-user-id-1
                                   new-fuelstation-id-1
                                   {:fpfuelstation/name "7-Eleven"
                                    :fpfuelstation/street "110 Maple Street"
                                    :fpfuelstation/city "Mayberry"
                                    :fpfuelstation/state "SC"
                                    :fpfuelstation/zip "28277"
                                    :fpfuelstation/latitude 35.050825
                                    :fpfuelstation/longitude -80.819054})
        (core/save-new-fuelstation conn
                                   new-user-id-1
                                   new-fuelstation-id-2
                                   {:fpfuelstation/name "Quick Mart"
                                    :fpfuelstation/street "112 Broad Street"
                                    :fpfuelstation/city "Charlotte"
                                    :fpfuelstation/state "NC"
                                    :fpfuelstation/zip "28272"
                                    :fpfuelstation/latitude 33.050825
                                    :fpfuelstation/longitude -79.819054})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               {:fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87
                                :fpvehicle/fuel-capacity 24.2})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-2
                               {:fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})

        (is (empty? (core/fplogs-for-user conn new-user-id-1)))
        (is (empty? (core/fplogs-for-user conn new-user-id-2)))
        (core/save-new-fplog conn new-user-id-1 new-vehicle-id-1 new-fuelstation-id-1 new-fplog-id-1
                             {:fplog/purchased-at t1
                              :fplog/got-car-wash true
                              :fplog/car-wash-per-gal-discount 0.08
                              :fplog/num-gallons 14.7
                              :fplog/gallon-price 2.39
                              :fplog/octane 87})
        (let [fplogs (core/fplogs-for-user conn new-user-id-1)]
          (is (= 1 (count fplogs)))
          (let [[fplog-id fplog] (first fplogs)]
            (is (= fplog-id new-fplog-id-1))
            (is (= fplog-id (:fplog/id fplog)))
            (is (= new-user-id-1 (:fplog/user-id fplog)))
            (is (= new-vehicle-id-1 (:fplog/vehicle-id fplog)))
            (is (= new-fuelstation-id-1 (:fplog/fuelstation-id fplog)))
            (is (not (nil? (:fplog/created-at fplog))))
            (is (not (nil? (:fplog/updated-at fplog))))
            (is (= t1 (:fplog/purchased-at fplog)))
            (is (= true (:fplog/got-car-wash fplog)))
            (is (= 14.7M (:fplog/num-gallons fplog)))
            (is (= 0.08M (:fplog/car-wash-per-gal-discount fplog)))
            (is (= 2.39M (:fplog/gallon-price fplog)))
            (is (= 87 (:fplog/octane fplog)))))
        (core/save-fplog conn new-fplog-id-1 {:fplog/user-id new-user-id-2
                                              :fplog/vehicle-id new-vehicle-id-2
                                              :fplog/fuelstation-id new-fuelstation-id-2
                                              :fplog/purchased-at t2
                                              :fplog/got-car-wash false
                                              :fplog/car-wash-per-gal-discount 0.09
                                              :fplog/num-gallons 14.8
                                              :fplog/gallon-price 2.41
                                              :fplog/octane 89})
        (is (empty? (core/fplogs-for-user conn new-user-id-1)))
        (let [fplogs (core/fplogs-for-user conn new-user-id-2)]
          (is (= 1 (count fplogs)))
          (let [[fplog-id fplog] (first fplogs)]
            (is (= fplog-id new-fplog-id-1))
            (is (= fplog-id (:fplog/id fplog)))
            (is (= new-user-id-2 (:fplog/user-id fplog)))
            (is (= new-vehicle-id-2 (:fplog/vehicle-id fplog)))
            (is (= new-fuelstation-id-2 (:fplog/fuelstation-id fplog)))
            (is (not (nil? (:fplog/updated-at fplog))))
            (is (not (nil? (:fplog/created-at fplog))))
            (is (= t2 (:fplog/purchased-at fplog)))
            (is (= false (:fplog/got-car-wash fplog)))
            (is (= 14.8M (:fplog/num-gallons fplog)))
            (is (= 0.09M (:fplog/car-wash-per-gal-discount fplog)))
            (is (= 2.41M (:fplog/gallon-price fplog)))
            (is (= 89 (:fplog/octane fplog)))))))))

(deftest EnvironmentLogs
  (testing "Saving (and then loading) environment logs"
    (j/with-db-transaction [conn db-spec]
      (let [new-user-id-1 (usercore/next-user-account-id conn)
            new-user-id-2 (usercore/next-user-account-id conn)
            new-vehicle-id-1 (core/next-vehicle-id conn)
            new-vehicle-id-2 (core/next-vehicle-id conn)
            new-envlog-id-1 (core/next-envlog-id conn)
            t1 (t/now)
            t2 (t/now)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/password "insecure2"})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               { :fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-2
                               {:fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})

        (is (empty? (core/envlogs-for-user conn new-user-id-1)))
        (is (empty? (core/envlogs-for-user conn new-user-id-2)))
        (core/save-new-envlog conn new-user-id-1 new-vehicle-id-1 new-envlog-id-1
                              {:envlog/logged-at t2
                               :envlog/reported-avg-mpg 22.4
                               :envlog/reported-avg-mph 21.1
                               :envlog/reported-outside-temp 74
                               :envlog/odometer 23650
                               :envlog/dte 531.2})
        (let [envlogs (core/envlogs-for-user conn new-user-id-1)]
          (is (= 1 (count envlogs)))
          (let [[envlog-id envlog] (first envlogs)]
            (is (= envlog-id new-envlog-id-1))
            (is (= envlog-id (:envlog/id envlog)))
            (is (= new-user-id-1 (:envlog/user-id envlog)))
            (is (= new-vehicle-id-1 (:envlog/vehicle-id envlog)))
            (is (not (nil? (:envlog/created-at envlog))))
            (is (not (nil? (:envlog/updated-at envlog))))
            (is (= t2 (:envlog/logged-at envlog)))
            (is (= 22.4M (:envlog/reported-avg-mpg envlog)))
            (is (= 21.1M (:envlog/reported-avg-mph envlog)))
            (is (= 74M (:envlog/reported-outside-temp envlog)))
            (is (= 23650M (:envlog/odometer envlog)))
            (is (= 531.2M (:envlog/dte envlog)))))
        (core/save-envlog conn new-envlog-id-1 {:envlog/user-id new-user-id-2
                                                :envlog/vehicle-id new-vehicle-id-2
                                                :envlog/logged-at t1
                                                :envlog/reported-avg-mpg 23.5
                                                :envlog/reported-avg-mph 22.3
                                                :envlog/reported-outside-temp 75.1
                                                :envlog/odometer 21999
                                                :envlog/dte 532.4})
        (is (empty? (core/envlogs-for-user conn new-user-id-1)))
        (let [envlogs (core/envlogs-for-user conn new-user-id-2)]
          (is (= 1 (count envlogs)))
          (let [[envlog-id envlog] (first envlogs)]
            (is (= envlog-id new-envlog-id-1))
            (is (= envlog-id (:envlog/id envlog)))
            (is (= new-user-id-2 (:envlog/user-id envlog)))
            (is (= new-vehicle-id-2 (:envlog/vehicle-id envlog)))
            (is (= t1 (:envlog/logged-at envlog)))
            (is (= 23.5M (:envlog/reported-avg-mpg envlog)))
            (is (= 22.3M (:envlog/reported-avg-mph envlog)))
            (is (= 75.1M (:envlog/reported-outside-temp envlog)))
            (is (= 21999M (:envlog/odometer envlog)))
            (is (= 532.4M (:envlog/dte envlog)))))))))

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
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/password "insecure2"})
        (is (empty? (core/fuelstations-for-user conn new-user-id-1)))
        (is (empty? (core/fuelstations-for-user conn new-user-id-2)))
        (core/save-new-fuelstation conn
                                   new-user-id-1
                                   new-fuelstation-id-1
                                   {:fpfuelstation/name "7-Eleven"
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
                                   {:fpfuelstation/name "Quick Mart"
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
                                   {:fpfuelstation/name "Stewart's"
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
          (is (not (nil? (:fpfuelstation/created-at fuelstation))))
          (is (not (nil? (:fpfuelstation/updated-at fuelstation))))
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
            new-vehicle-id-4 (core/next-vehicle-id conn)
            t1 (t/now)
            t2 (t/now)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/password "insecure2"})
        (is (empty? (core/vehicles-for-user conn new-user-id-1)))
        (is (empty? (core/vehicles-for-user conn new-user-id-2)))
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               {:fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87
                                :fpvehicle/fuel-capacity 24.3})
        (is (= 1 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (empty? (core/vehicles-for-user conn new-user-id-2)))
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-2
                               {:fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})
        (is (= 2 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (empty? (core/vehicles-for-user conn new-user-id-2)))
        (is (= 1 (count (core/vehicles-for-user-by-name conn new-user-id-1 "Jeep"))))
        (is (= 1 (count (core/vehicles-for-user-by-name conn new-user-id-1 "JEEp"))))
        (core/save-new-vehicle conn
                               new-user-id-2
                               new-vehicle-id-3
                               {:fpvehicle/name "Honda Accord"
                                :fpvehicle/fuel-capacity 19.8
                                :fpvehicle/default-octane 89})
        (is (= 2 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (= 1 (count (core/vehicles-for-user conn new-user-id-2))))
        (let [[vehicle-id vehicle] (first (core/vehicles-for-user conn new-user-id-2))]
          (is (= vehicle-id new-vehicle-id-3))
          (is (= vehicle-id (:fpvehicle/id vehicle)))
          (is (= new-user-id-2 (:fpvehicle/user-id vehicle)))
          (is (not (nil? (:fpvehicle/created-at vehicle))))
          (is (not (nil? (:fpvehicle/updated-at vehicle))))
          (is (= 89 (:fpvehicle/default-octane vehicle)))
          (is (= 19.8M (:fpvehicle/fuel-capacity vehicle)))
          (is (= 1 (:fpvehicle/updated-count vehicle)))
          (is (= "Honda Accord" (:fpvehicle/name vehicle))))
        (core/save-vehicle conn new-vehicle-id-3 {:fpvehicle/user-id new-user-id-1})
        (is (= 3 (count (core/vehicles-for-user conn new-user-id-1))))
        (is (= 0 (count (core/vehicles-for-user conn new-user-id-2))))
        ; let's give user id-2 replacement Honda Accord
        (core/save-new-vehicle conn
                               new-user-id-2
                               new-vehicle-id-4
                               {:fpvehicle/name "Honda Accord"
                                :fpvehicle/fuel-capacity 19.9
                                :fpvehicle/default-octane 90})
        ; it's okay to use this name because it belongs to user id-1
        (core/save-vehicle conn new-vehicle-id-3 {:fpvehicle/name "Honda Accord"})
        (let [[vehicle-id vehicle] (core/vehicle-by-id conn new-vehicle-id-3)]
          (is (not (nil? vehicle)))
          (is (= new-vehicle-id-3 (:fpvehicle/id vehicle)))
          (is (= "Honda Accord" (:fpvehicle/name vehicle)))
          (is (= new-user-id-1 (:fpvehicle/user-id vehicle)))
          (is (= 19.8M (:fpvehicle/fuel-capacity vehicle)))
          (is (= 89 (:fpvehicle/default-octane vehicle))))))))

(deftest Vehicles-Err-Handling-1
  (testing "Error handling with vehicles, part 1"
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
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/password "insecure2"})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               {:fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87
                                :fpvehicle/fuel-capacity 24.3})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-2
                               {:fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})
        (core/save-new-vehicle conn
                               new-user-id-2
                               new-vehicle-id-3
                               {:fpvehicle/name "Honda Accord"
                                :fpvehicle/fuel-capacity 19.8
                                :fpvehicle/default-octane 89})
        (testing "Try to save new vehicle with duplicate name for user id-2"
          (let [new-vehicle-id-4 (core/next-vehicle-id conn)]
            (try
              (core/save-new-vehicle conn
                                     new-user-id-2
                                     new-vehicle-id-4
                                     {:fpvehicle/name "Honda Accord"
                                      :fpvehicle/fuel-capacity 19.7
                                      :fpvehicle/default-octane 91})
              (is false "Should not have reached this")
              (catch IllegalArgumentException e
                (let [msg-mask (Long/parseLong (.getMessage e))]
                  (is (pos? (bit-and msg-mask val/sv-any-issues)))
                  (is (zero? (bit-and msg-mask val/sv-name-not-provided)))
                  (is (pos? (bit-and msg-mask val/sv-vehicle-already-exists))))))))))))

(deftest Vehicles-Err-Handling-2
  (testing "Error handling with vehicles, part 2"
    (j/with-db-transaction [conn db-spec]
      (let [new-user-id-1 (usercore/next-user-account-id conn)
            new-user-id-2 (usercore/next-user-account-id conn)
            new-vehicle-id-1 (core/next-vehicle-id conn)
            new-vehicle-id-2 (core/next-vehicle-id conn)
            new-vehicle-id-3 (core/next-vehicle-id conn)
            new-vehicle-id-4 (core/next-vehicle-id conn)
            t1 (t/now)
            t2 (t/now)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/password "insecure"})
        (usercore/save-new-user conn
                                new-user-id-2
                                {:user/username "evansp"
                                 :user/email "paul@test.com"
                                 :user/name "Paul Evans"
                                 :user/password "insecure2"})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               {:fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87
                                :fpvehicle/fuel-capacity 24.3})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-2
                               {:fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})
        (core/save-new-vehicle conn
                               new-user-id-2
                               new-vehicle-id-3
                               {:fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87
                                :fpvehicle/fuel-capacity 24.3})
        (core/save-new-vehicle conn
                               new-user-id-2
                               new-vehicle-id-4
                               {:fpvehicle/name "300Z"
                                :fpvehicle/default-octane 93})
        (testing "Try to update an existing vehicle by giving it a name that is already taken"
          (try
            (core/save-vehicle conn
                               new-vehicle-id-1 ; is currently "Jeep"
                               {:fpvehicle/name "300Z"})
            (is false "Should not have reached this")
            (catch IllegalArgumentException e
              (let [msg-mask (Long/parseLong (.getMessage e))]
                (is (pos? (bit-and msg-mask val/sv-any-issues)))
                (is (zero? (bit-and msg-mask val/sv-name-not-provided)))
                (is (pos? (bit-and msg-mask val/sv-vehicle-already-exists)))))))))))

(deftest Vehicles-Err-Handling-3
  (testing "Error handling with vehicles, part 3"
    (j/with-db-transaction [conn db-spec]
      (let [new-user-id-1 (usercore/next-user-account-id conn)
            new-vehicle-id-1 (core/next-vehicle-id conn)]
        (usercore/save-new-user conn
                                new-user-id-1
                                {:user/username "smithj"
                                 :user/email "smithj@test.com"
                                 :user/name "John Smith"
                                 :user/password "insecure"})
        (core/save-new-vehicle conn
                               new-user-id-1
                               new-vehicle-id-1
                               {:fpvehicle/name "Jeep"
                                :fpvehicle/default-octane 87
                                :fpvehicle/fuel-capacity 24.3})
        (testing "Try to update an existing vehicle that has been modified since"
          (try
            (core/save-vehicle conn
                               new-vehicle-id-1
                               {:fpvehicle/name "300Z"}
                               (t/minus (t/now) (t/weeks 1)))
            (is false "Should not have reached this")
            (catch clojure.lang.ExceptionInfo e
              (let [type (-> e ex-data :type)
                    cause (-> e ex-data :cause)]
                (is (= type :precondition-failed))
                (is (= cause :unmodified-since-check-failed))))))))))
