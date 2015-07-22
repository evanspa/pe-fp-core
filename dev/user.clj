(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.java.jdbc :as j]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.test :as test]
            [clojure.stacktrace :refer (e)]
            [pe-core-utils.core :as ucore]
            [pe-fp-core.validation :as val]
            [pe-user-core.core :as usercore]
            [pe-fp-core.core :as core]
            [pe-jdbc-utils.core :as jcore]
            [pe-fp-core.test-utils :refer [db-spec-without-db
                                           db-spec-fn]]
            [pe-user-core.ddl :as uddl]
            [pe-fp-core.ddl :as fpddl]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]))

(def dev-db-name "fp")

(def db-spec-dev (db-spec-fn dev-db-name))

(defn refresh-dev-db
  []
  ;; Database setup
  (jcore/drop-database db-spec-without-db dev-db-name)
  (jcore/create-database db-spec-without-db dev-db-name)

  ;; User / auth-token setup
  (j/db-do-commands db-spec-dev
                    true
                    uddl/schema-version-ddl
                    uddl/v0-create-user-account-ddl
                    uddl/v0-add-unique-constraint-user-account-email
                    uddl/v0-add-unique-constraint-user-account-username
                    uddl/v0-create-authentication-token-ddl
                    uddl/v1-user-add-deleted-reason-col
                    uddl/v1-user-add-suspended-at-col
                    uddl/v1-user-add-suspended-reason-col
                    uddl/v1-user-add-suspended-count-col)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (uddl/v0-create-updated-count-inc-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (uddl/v0-create-user-account-updated-count-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (uddl/v1-create-suspended-count-inc-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (uddl/v1-create-user-account-suspended-count-trigger-fn db-spec-dev))

  ;; Vehicle setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-vehicle-ddl
                    fpddl/v0-add-unique-constraint-vehicle-name)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-vehicle-updated-count-inc-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-vehicle-updated-count-trigger-fn db-spec-dev))

  ;; Fuelstation setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-fuelstation-ddl
                    fpddl/v0-create-index-on-fuelstation-name)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fuelstation-updated-count-inc-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fuelstation-updated-count-trigger-fn db-spec-dev))

  ;; Fuel purchase log setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-fplog-ddl)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fplog-updated-count-inc-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-fplog-updated-count-trigger-fn db-spec-dev))

  ;; Environment log setup
  (j/db-do-commands db-spec-dev
                    true
                    fpddl/v0-create-envlog-ddl)
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-envlog-updated-count-inc-trigger-fn db-spec-dev))
  (jcore/with-try-catch-exec-as-query db-spec-dev
    (fpddl/v0-create-envlog-updated-count-trigger-fn db-spec-dev)))


(defn ent-ids
  [location-str]
  (let [post-str (.substring location-str 39)
        user-id (.substring location-str
                            39
                            (+ 39 (.indexOf post-str "/")))
        ent-id (.substring post-str
                           (inc (.lastIndexOf post-str "/")))]
    [user-id ent-id]))

(defn any-entities
  [payloads]
  (map (fn [payload]
         (merge {:id (:location payload)}
                (:payload payload)))
       (filter (fn [{entity :payload}] (contains? entity :fpfuelstation/name))
               payloads)))

(defn any-entid-to-id-map
  [fuelstations]
  (dissoc  (let [count 0]
             (reduce (fn [m fs]
                       (let [entid (:id fs)]
                         (merge m
                                {entid (:count m)}
                                {:count (inc (:count m))})
                         ))
                     {:count count}
                     fuelstations)) :count))

(def user-id 0)

(def vehicles [{:fpvehicle/name "Jeep Grand Cherokee",
                :entid "277076930207018"
                :id 0}
               {:fpvehicle/name "Volkswagen CC\n (R-line)",
                :entid "277076930200604"
                :id 1}
               {:fpvehicle/name "Mazda CX-9",
                :entid "277076930200584"
                :id 2}
               {:fpvehicle/name "BMW 328i",
                :entid "277076930200798"
                :id 3}
               {:fpvehicle/name "Nissan Altima",
                :entid "277076930200614"
                :id 4}])

(def fuelstations [{:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Market Express",
                    :fpfuelstation/street "10636 Providence\n Road",
                    :fpfuelstation/latitude 35.0651414,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.7704794,
                    :fpfuelstation/date-added #inst "2015-02-04T01:34:41.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930203303"}
                   {:fpfuelstation/city "Indian Land",
                    :fpfuelstation/name "Sam's Mart (#81 - SC Walmart)",
                    :fpfuelstation/street "10130\n Charlotte Hwy",
                    :fpfuelstation/latitude 35.0125209215553,
                    :fpfuelstation/zip "29720",
                    :fpfuelstation/longitude -80.8517863328359,
                    :fpfuelstation/date-added #inst "2015-02-22T08:07:38.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930206492"}
                   {:fpfuelstation/city "Troy",
                    :fpfuelstation/name "Stewarts (Northern Dr)",
                    :fpfuelstation/street "100 Northern Drive",
                    :fpfuelstation/latitude 42.784783,
                    :fpfuelstation/zip "12182",
                    :fpfuelstation/longitude -73.654744,
                    :fpfuelstation/date-added #inst "2015-02-12T15:41:17.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930205402"}
                   {:fpfuelstation/city "Hillsville",
                    :fpfuelstation/name "Cockerham Fuel Center",
                    :fpfuelstation/street "59 Farmer's Market\n Drive",
                    :fpfuelstation/latitude 36.744506,
                    :fpfuelstation/zip "24343",
                    :fpfuelstation/longitude -80.773803,
                    :fpfuelstation/date-added #inst "2015-02-03T01:57:43.000-00:00",
                    :fpfuelstation/state "VA",
                    :id "fuelstations/277076930202955"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Kangaroo Exp (#3944)",
                    :fpfuelstation/street "11640 Providence\n Road",
                    :fpfuelstation/latitude 35.051488,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.770468,
                    :fpfuelstation/date-added #inst "2015-01-18T03:38:21.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930201047"}
                   {:fpfuelstation/city "Troy",
                    :fpfuelstation/name "Sam's Food Store",
                    :fpfuelstation/street "884 Second Avenue",
                    :fpfuelstation/latitude 42.7869624,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -73.6711897,
                    :fpfuelstation/date-added #inst "2015-02-01T21:13:39.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930201993"}
                   {:fpfuelstation/city "Yulee",
                    :fpfuelstation/name "Flash Foods (#186)",
                    :fpfuelstation/street "104 East State\n Road 200",
                    :fpfuelstation/latitude 30.6201360070209,
                    :fpfuelstation/zip "32097",
                    :fpfuelstation/longitude -81.5244733746564,
                    :fpfuelstation/date-added #inst "2015-02-18T14:23:27.000-00:00",
                    :fpfuelstation/state "FL",
                    :id "fuelstations/277076930206233"}
                   {:fpfuelstation/city "Indian Land",
                    :fpfuelstation/name "7-Eleven (#39634)",
                    :fpfuelstation/street "10130\n Charlotte Hwy",
                    :fpfuelstation/latitude 35.0125209215553,
                    :fpfuelstation/zip "29707",
                    :fpfuelstation/longitude -80.8517863328359,
                    :fpfuelstation/date-added #inst "2015-02-01T21:31:02.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930202225"}
                   {:fpfuelstation/city "Hamburg",
                    :fpfuelstation/name "Loves (#358)",
                    :fpfuelstation/street "3700 Mountain\n Road",
                    :fpfuelstation/latitude 40.5169090140384,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -76.111061321943,
                    :fpfuelstation/date-added #inst "2015-02-01T21:20:45.000-00:00",
                    :fpfuelstation/state "PA",
                    :id "fuelstations/277076930202111"}
                   {:fpfuelstation/city "Colonie",
                    :fpfuelstation/name "Hess (#32334)",
                    :fpfuelstation/street "156 Wolf Road",
                    :fpfuelstation/latitude 42.721147,
                    :fpfuelstation/zip "12205",
                    :fpfuelstation/longitude -73.803628,
                    :fpfuelstation/date-added #inst "2015-02-03T06:11:30.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930203188"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Sam's Mart (#202)",
                    :fpfuelstation/street "10806 Providence\n Road",
                    :fpfuelstation/latitude 35.062864,
                    :fpfuelstation/zip "28277",
                    :fpfuelstation/longitude -80.770689,
                    :fpfuelstation/date-added #inst "2015-02-06T16:27:30.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930204288"}
                   {:fpfuelstation/city "Falling\n Water",
                    :fpfuelstation/name "Shell (I-81 Exit 21)",
                    :fpfuelstation/street "I-81, Exit 21",
                    :fpfuelstation/latitude 39.579149,
                    :fpfuelstation/zip "25419",
                    :fpfuelstation/longitude -77.878357,
                    :fpfuelstation/date-added #inst "2015-02-06T02:27:59.000-00:00",
                    :fpfuelstation/state "WV",
                    :id "fuelstations/277076930203793"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "7-Eleven (#35568)",
                    :fpfuelstation/street "5115 Old Dowd Road",
                    :fpfuelstation/latitude 35.230041,
                    :fpfuelstation/zip "28208",
                    :fpfuelstation/longitude -80.925464,
                    :fpfuelstation/date-added #inst "2015-02-12T15:37:01.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930205372"}
                   {:fpfuelstation/city "Palm\n Bay",
                    :fpfuelstation/name "Hess (Palm Bay, FL)",
                    :fpfuelstation/street "1090 Malabar Road\n SE",
                    :fpfuelstation/latitude 27.998287,
                    :fpfuelstation/zip "32907",
                    :fpfuelstation/longitude -80.640138,
                    :fpfuelstation/date-added #inst "2015-02-07T02:38:26.000-00:00",
                    :fpfuelstation/state "FL",
                    :id "fuelstations/277076930204268"}
                   {:fpfuelstation/city "Summerfield",
                    :fpfuelstation/name "Murphy USA (#5819)",
                    :fpfuelstation/street "17790 SE 109th\n Terra",
                    :fpfuelstation/latitude 28.9630426821216,
                    :fpfuelstation/zip "34491",
                    :fpfuelstation/longitude -81.970138986296,
                    :fpfuelstation/date-added #inst "2015-02-06T02:41:15.000-00:00",
                    :fpfuelstation/state "FL",
                    :id "fuelstations/277076930203924"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Piper Glen 7-Eleven (#35578)",
                    :fpfuelstation/street "5200 Piper Station\n Drive",
                    :fpfuelstation/latitude 35.061213,
                    :fpfuelstation/zip "28277",
                    :fpfuelstation/longitude -80.81271,
                    :fpfuelstation/date-added #inst "2015-01-17T12:44:58.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930200624"}
                   {:fpfuelstation/city "Williams",
                    :fpfuelstation/name "Express Pay (#4767604)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 39.294366,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -76.409193,
                    :fpfuelstation/date-added #inst "2015-02-01T21:23:10.000-00:00",
                    :fpfuelstation/state "MD",
                    :id "fuelstations/277076930202140"}
                   {:fpfuelstation/city "Sloatsburg",
                    :fpfuelstation/name "Sunoco A-Plus",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 41.16295,
                    :fpfuelstation/zip "10974",
                    :fpfuelstation/longitude -74.1901515,
                    :fpfuelstation/date-added #inst "2015-02-01T21:46:43.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930202501"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "7-Eleven (Promenade)",
                    :fpfuelstation/street "10806 Providence\n Road",
                    :fpfuelstation/latitude 35.062864,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.770689,
                    :fpfuelstation/date-added #inst "2015-02-02T13:42:26.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930202758"}
                   {:fpfuelstation/city "Lansingburg",
                    :fpfuelstation/name "Stewarts Shop (#212)",
                    :fpfuelstation/street "764 5th Avenue",
                    :fpfuelstation/latitude 42.781413,
                    :fpfuelstation/zip "12182",
                    :fpfuelstation/longitude -73.669979,
                    :fpfuelstation/date-added #inst "2015-02-06T02:33:01.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930203836"}
                   {:fpfuelstation/city "Marathon",
                    :fpfuelstation/name "Tom Thumb (#230)",
                    :fpfuelstation/street "5515 Overseas Hwy",
                    :fpfuelstation/latitude 24.71595,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -81.075243,
                    :fpfuelstation/date-added #inst "2015-02-07T02:30:01.000-00:00",
                    :fpfuelstation/state "FL",
                    :id "fuelstations/277076930204248"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Express Pay (#4789467)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 35.2272008,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.8430624,
                    :fpfuelstation/date-added #inst "2015-02-01T21:52:44.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930202559"}
                   {:fpfuelstation/city "Jacksonville",
                    :fpfuelstation/name "Flash Foods (#125)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 30.332184,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -81.6556510000001,
                    :fpfuelstation/date-added #inst "2015-02-19T13:25:53.000-00:00",
                    :fpfuelstation/state "FL",
                    :id "fuelstations/277076930206285"}
                   {:fpfuelstation/city "Strasburg",
                    :fpfuelstation/name "Holtzman Express",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 38.9887199,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -78.358616,
                    :fpfuelstation/date-added #inst "2015-02-01T21:10:51.000-00:00",
                    :fpfuelstation/state "VA",
                    :id "fuelstations/277076930201945"}
                   {:fpfuelstation/city "Halfmoon",
                    :fpfuelstation/name "Halfmoon XtraMart",
                    :fpfuelstation/street "1588 Route 9",
                    :fpfuelstation/latitude 42.850262,
                    :fpfuelstation/zip "12065",
                    :fpfuelstation/longitude -73.756021,
                    :fpfuelstation/date-added #inst "2015-02-01T21:42:07.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930202431"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Ballantyne BP",
                    :fpfuelstation/street "Ballantyne\n Commons Parkway",
                    :fpfuelstation/latitude 35.0552794550313,
                    :fpfuelstation/zip "28277",
                    :fpfuelstation/longitude -80.8433277987077,
                    :fpfuelstation/date-added #inst "2015-02-19T13:39:11.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930206288"}
                   {:fpfuelstation/city "Ridgeland",
                    :fpfuelstation/name "Taylor's BP",
                    :fpfuelstation/street "80 Blue Heron\n Drive",
                    :fpfuelstation/latitude 32.4784694780001,
                    :fpfuelstation/zip "29936",
                    :fpfuelstation/longitude -80.972420918,
                    :fpfuelstation/date-added #inst "2015-02-19T13:34:48.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930206282"}
                   {:fpfuelstation/city "Troy",
                    :fpfuelstation/name "Stewarts Shop (#131)",
                    :fpfuelstation/street "9 112th Street",
                    :fpfuelstation/latitude 42.771254,
                    :fpfuelstation/zip "12182",
                    :fpfuelstation/longitude -73.679242,
                    :fpfuelstation/date-added #inst "2015-02-01T21:18:14.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930202060"}
                   {:fpfuelstation/city nil,
                    :fpfuelstation/name "Friendly Express 77",
                    :fpfuelstation/street nil,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/date-added #inst "2015-02-03T02:03:23.000-00:00",
                    :fpfuelstation/state nil,
                    :id "fuelstations/277076930203024"}
                   {:fpfuelstation/city "Mullins",
                    :fpfuelstation/name "Kangaroo Express (#3217)",
                    :fpfuelstation/street "200 E. McIntyre\n Street",
                    :fpfuelstation/latitude 34.200229,
                    :fpfuelstation/zip "29574",
                    :fpfuelstation/longitude -79.251009,
                    :fpfuelstation/date-added #inst "2015-02-09T13:30:38.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930204783"}
                   {:fpfuelstation/city "Fort Mill",
                    :fpfuelstation/name "The Panhandle",
                    :fpfuelstation/street "9775 Charlotte\n Hwy",
                    :fpfuelstation/latitude 35.0031953288922,
                    :fpfuelstation/zip "29715",
                    :fpfuelstation/longitude -80.8557400234271,
                    :fpfuelstation/date-added #inst "2015-02-12T14:39:43.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930205187"}
                   {:fpfuelstation/city "Concord",
                    :fpfuelstation/name "Circle K (#5103)",
                    :fpfuelstation/street "4930 Davidson Hwy",
                    :fpfuelstation/latitude 35.436376,
                    :fpfuelstation/zip "28027",
                    :fpfuelstation/longitude -80.660877,
                    :fpfuelstation/date-added #inst "2015-02-12T15:30:56.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930205323"}
                   {:fpfuelstation/city "Sloatsburg",
                    :fpfuelstation/name "Sunoco (milepost 33-S)",
                    :fpfuelstation/street "Milepost 33-S",
                    :fpfuelstation/latitude 41.15446,
                    :fpfuelstation/zip "10974",
                    :fpfuelstation/longitude -74.196279,
                    :fpfuelstation/date-added #inst "2015-02-06T02:30:31.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930203816"}
                   {:fpfuelstation/city "Verona",
                    :fpfuelstation/name "Stop In Food Store",
                    :fpfuelstation/street "210 Laural Hill\n Road",
                    :fpfuelstation/latitude 38.195412,
                    :fpfuelstation/zip "24482",
                    :fpfuelstation/longitude -79.002655,
                    :fpfuelstation/date-added #inst "2015-02-19T13:47:59.000-00:00",
                    :fpfuelstation/state "VA",
                    :id "fuelstations/277076930206297"}
                   {:fpfuelstation/city "Staunton",
                    :fpfuelstation/name "Express Pay (#4203873)",
                    :fpfuelstation/street "Greenville\n Avenue",
                    :fpfuelstation/latitude 38.1372713057066,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -79.0677644107743,
                    :fpfuelstation/date-added #inst "2015-02-01T21:50:05.000-00:00",
                    :fpfuelstation/state "VA",
                    :id "fuelstations/277076930202530"}
                   {:fpfuelstation/city nil,
                    :fpfuelstation/name "shell",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 34.3453713180817,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -83.3175257687301,
                    :fpfuelstation/date-added #inst "2015-03-13T09:35:26.000-00:00",
                    :fpfuelstation/state nil,
                    :id "fuelstations/277076930206841"}
                   {:fpfuelstation/city "Savannah",
                    :fpfuelstation/name "Enmark (#830)",
                    :fpfuelstation/street "907 East\n Highway 80",
                    :fpfuelstation/latitude 32.0913902161665,
                    :fpfuelstation/zip "31408",
                    :fpfuelstation/longitude -81.1515090983841,
                    :fpfuelstation/date-added #inst "2015-02-06T02:44:53.000-00:00",
                    :fpfuelstation/state "GA",
                    :id "fuelstations/277076930203972"}
                   {:fpfuelstation/city "Watervliet",
                    :fpfuelstation/name "XtraMart (#1459)",
                    :fpfuelstation/street "616 Broadway",
                    :fpfuelstation/latitude 42.707534,
                    :fpfuelstation/zip "12189",
                    :fpfuelstation/longitude -73.715307,
                    :fpfuelstation/date-added #inst "2015-02-01T21:44:19.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930202463"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Quik Shoppe (Elm Ln)",
                    :fpfuelstation/street "11924 Elm Lane",
                    :fpfuelstation/latitude 35.047776,
                    :fpfuelstation/zip "28277",
                    :fpfuelstation/longitude -80.816418,
                    :fpfuelstation/date-added #inst "2015-02-01T09:35:37.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930202310"}
                   {:fpfuelstation/city "Pineville",
                    :fpfuelstation/name "Shell (Lancaster Ave)",
                    :fpfuelstation/street "12740 Lancaster Ave",
                    :fpfuelstation/latitude 35.068983,
                    :fpfuelstation/zip "28134",
                    :fpfuelstation/longitude -80.878456,
                    :fpfuelstation/date-added #inst "2015-02-19T13:18:27.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930206253"}
                   {:fpfuelstation/city "East\n Greenbush",
                    :fpfuelstation/name "Stewarts Shop (#352)",
                    :fpfuelstation/street "95 Troy Road",
                    :fpfuelstation/latitude 42.6204109,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -73.7021314,
                    :fpfuelstation/date-added #inst "2015-02-01T21:08:12.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930201916"}
                   {:fpfuelstation/city "W. Melbourne",
                    :fpfuelstation/name "Hammock Landing",
                    :fpfuelstation/street "195 Palm Bay Road",
                    :fpfuelstation/latitude 28.036258,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.6653669,
                    :fpfuelstation/date-added #inst "2015-02-07T02:24:50.000-00:00",
                    :fpfuelstation/state "FL",
                    :id "fuelstations/277076930204219"}
                   {:fpfuelstation/city "St. Matthews",
                    :fpfuelstation/name "Quick Trip #5",
                    :fpfuelstation/street "388 Caw Caw Hwy",
                    :fpfuelstation/latitude 33.671868,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.894212,
                    :fpfuelstation/date-added #inst "2015-02-07T02:22:57.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930204199"}
                   {:fpfuelstation/city nil,
                    :fpfuelstation/name "Shell (by the Y - Bryant Farms Rd)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 35.0618148202495,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.8013483994684,
                    :fpfuelstation/date-added #inst "2015-03-18T11:14:31.000-00:00",
                    :fpfuelstation/state nil,
                    :id "fuelstations/277076930206870"}
                   {:fpfuelstation/city "Halfmoon",
                    :fpfuelstation/name "Soni's Brothers",
                    :fpfuelstation/street "1500 Route 9",
                    :fpfuelstation/latitude 42.837367,
                    :fpfuelstation/zip "12065",
                    :fpfuelstation/longitude -73.742334,
                    :fpfuelstation/date-added #inst "2015-02-03T02:01:28.000-00:00",
                    :fpfuelstation/state "NY",
                    :id "fuelstations/277076930202984"}
                   {:fpfuelstation/city nil,
                    :fpfuelstation/name "Kangaroo Express (#3068)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/date-added #inst "2015-02-19T13:44:38.000-00:00",
                    :fpfuelstation/state nil,
                    :id "fuelstations/277076930206259"}
                   {:fpfuelstation/city "Myrtle Beach",
                    :fpfuelstation/name "Wilco Hess (#939)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 33.68906,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -78.886694,
                    :fpfuelstation/date-added #inst "2015-02-06T02:35:03.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930203856"}
                   {:fpfuelstation/city "Concord",
                    :fpfuelstation/name "Kangaroo Exp (#3951)",
                    :fpfuelstation/street "8501 Concord Mills",
                    :fpfuelstation/latitude 35.371919,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.721932,
                    :fpfuelstation/date-added #inst "2015-02-07T02:10:12.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930204115"}
                   {:fpfuelstation/city "Charlotte",
                    :fpfuelstation/name "Kangaroo Express (#3979)",
                    :fpfuelstation/street "9620 Rea Road",
                    :fpfuelstation/latitude 35.0385479,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -80.8064795,
                    :fpfuelstation/date-added #inst "2015-02-01T21:57:52.000-00:00",
                    :fpfuelstation/state "NC",
                    :id "fuelstations/277076930202597"}
                   {:fpfuelstation/city "Harrisburg",
                    :fpfuelstation/name "Wilco Hess (#7001)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 40.2737,
                    :fpfuelstation/zip nil,
                    :fpfuelstation/longitude -76.884418,
                    :fpfuelstation/date-added #inst "2015-02-12T15:45:23.000-00:00",
                    :fpfuelstation/state "PA",
                    :id "fuelstations/277076930205432"}
                   {:fpfuelstation/city nil,
                    :fpfuelstation/name "Express Mart (#10)",
                    :fpfuelstation/street nil,
                    :fpfuelstation/latitude 35.13103495,
                    :fpfuelstation/zip "28210",
                    :fpfuelstation/longitude -80.8484582392778,
                    :fpfuelstation/date-added #inst "2015-02-12T15:21:38.000-00:00",
                    :fpfuelstation/state nil,
                    :id "fuelstations/277076930205293"}
                   {:fpfuelstation/city "Columbia",
                    :fpfuelstation/name "Pitt Stop #35",
                    :fpfuelstation/street "2020 Bluff Road",
                    :fpfuelstation/latitude 33.9544374,
                    :fpfuelstation/zip "29201",
                    :fpfuelstation/longitude -80.9914569,
                    :fpfuelstation/date-added #inst "2015-02-19T13:41:17.000-00:00",
                    :fpfuelstation/state "SC",
                    :id "fuelstations/277076930206265"}])

(def fuelstation-entid-map {"fuelstations/277076930202597" 48,
                            "fuelstations/277076930204268" 13,
                            "fuelstations/277076930203816" 32,
                            "fuelstations/277076930206253" 39,
                            "fuelstations/277076930202530" 34,
                            "fuelstations/277076930201916" 40,
                            "fuelstations/277076930202955" 3,
                            "fuelstations/277076930205402" 2,
                            "fuelstations/277076930203188" 9,
                            "fuelstations/277076930202463" 37,
                            "fuelstations/277076930204288" 10,
                            "fuelstations/277076930202559" 21,
                            "fuelstations/277076930206297" 33,
                            "fuelstations/277076930206841" 35,
                            "fuelstations/277076930204199" 42,
                            "fuelstations/277076930201993" 5,
                            "fuelstations/277076930202140" 16,
                            "fuelstations/277076930203836" 19,
                            "fuelstations/277076930206492" 1,
                            "fuelstations/277076930206288" 25,
                            "fuelstations/277076930202060" 27,
                            "fuelstations/277076930202111" 8,
                            "fuelstations/277076930201047" 4,
                            "fuelstations/277076930204248" 20,
                            "fuelstations/277076930205323" 31,
                            "fuelstations/277076930205187" 30,
                            "fuelstations/277076930203303" 0,
                            "fuelstations/277076930204783" 29,
                            "fuelstations/277076930202984" 44,
                            "fuelstations/277076930203856" 46,
                            "fuelstations/277076930203924" 14,
                            "fuelstations/277076930206282" 26,
                            "fuelstations/277076930202758" 18,
                            "fuelstations/277076930204115" 47,
                            "fuelstations/277076930201945" 23,
                            "fuelstations/277076930202310" 38,
                            "fuelstations/277076930206233" 6,
                            "fuelstations/277076930202431" 24,
                            "fuelstations/277076930202501" 17,
                            "fuelstations/277076930204219" 41,
                            "fuelstations/277076930203972" 36,
                            "fuelstations/277076930206265" 51,
                            "fuelstations/277076930205432" 49,
                            "fuelstations/277076930200624" 15,
                            "fuelstations/277076930205372" 12,
                            "fuelstations/277076930206285" 22,
                            "fuelstations/277076930203024" 28,
                            "fuelstations/277076930206870" 43,
                            "fuelstations/277076930202225" 7,
                            "fuelstations/277076930203793" 11,
                            "fuelstations/277076930205293" 50,
                            "fuelstations/277076930206259" 45})

(def fplogs [{:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.831,
  :id "fplogs/277076930203876",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-02-16T02:36:21.000-00:00",
  :fplog/gallon-price 3.779,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.804,
  :id "fplogs/277076930201735",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-12-05T17:28:53.000-00:00",
  :fplog/gallon-price 2.699,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202758",
  :fplog/octane 87,
  :fplog/num-gallons 38.36,
  :id "fplogs/277076930207165",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-03T14:21:12.000-00:00",
  :fplog/gallon-price 2.449,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202225",
  :fplog/octane 93,
  :fplog/num-gallons 11.705,
  :id "fplogs/277076930202235",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-09-05T20:29:45.000-00:00",
  :fplog/gallon-price 3.509,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 15.797,
  :id "fplogs/277076930206064",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-06-01T13:09:53.000-00:00",
  :fplog/gallon-price 3.589,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203303",
  :fplog/octane 87,
  :fplog/num-gallons 14.254,
  :id "fplogs/277076930203313",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2011-10-17T12:33:41.000-00:00",
  :fplog/gallon-price 3.469,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 12.639,
  :id "fplogs/277076930205528",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-11-16T15:56:55.000-00:00",
  :fplog/gallon-price 3.249,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 11.341,
  :id "fplogs/277076930203904",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-05-31T01:37:38.000-00:00",
  :fplog/gallon-price 3.469,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 15.781,
  :id "fplogs/277076930202843",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-09-29T00:48:49.000-00:00",
  :fplog/gallon-price 3.899,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.718,
  :id "fplogs/277076930204982",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-02-17T13:54:14.000-00:00",
  :fplog/gallon-price 3.759,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.594,
  :id "fplogs/277076930200648",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-11-25T12:44:38.000-00:00",
  :fplog/gallon-price 2.729,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.998,
  :id "fplogs/277076930201703",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-01-31T13:23:05.000-00:00",
  :fplog/gallon-price 1.999,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 18.122,
  :id "fplogs/277076930202796",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-10-28T00:44:15.000-00:00",
  :fplog/gallon-price 3.539,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 18.596,
  :id "fplogs/277076930203914",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-12-19T02:38:48.000-00:00",
  :fplog/gallon-price 3.389,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.53,
  :id "fplogs/277076930206092",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-07-01T13:11:59.000-00:00",
  :fplog/gallon-price 3.259,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 15.135,
  :id "fplogs/277076930205500",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-11-27T15:54:21.000-00:00",
  :fplog/gallon-price 3.439,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203924",
  :fplog/octane 87,
  :fplog/num-gallons 17.876,
  :id "fplogs/277076930203934",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-01-01T02:40:10.000-00:00",
  :fplog/gallon-price 3.399,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 93,
  :fplog/num-gallons 13.74,
  :id "fplogs/277076930205010",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-01-02T13:56:41.000-00:00",
  :fplog/gallon-price 3.889,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930206870",
  :id "fplogs/277076930207184",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-04T12:10:54.000-00:00",
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 89,
  :fplog/num-gallons 13.39,
  :id "fplogs/277076930202815",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-10-09T00:45:29.000-00:00",
  :fplog/gallon-price 3.729,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.106,
  :id "fplogs/277076930202263",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-09-15T20:32:19.000-00:00",
  :fplog/gallon-price 3.859,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202758",
  :fplog/octane 87,
  :fplog/num-gallons 22.514,
  :id "fplogs/277076930207093",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-04-24T11:28:26.000-00:00",
  :fplog/gallon-price 2.349,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202310",
  :fplog/octane 87,
  :fplog/num-gallons 15.983,
  :id "fplogs/277076930205452",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-02-19T15:47:08.000-00:00",
  :fplog/gallon-price 3.749,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203188",
  :fplog/octane 87,
  :fplog/num-gallons 18.412,
  :id "fplogs/277076930203237",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-07T00:20:50.000-00:00",
  :fplog/gallon-price 3.769,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.57,
  :id "fplogs/277076930203944",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-01-10T02:41:55.000-00:00",
  :fplog/gallon-price 3.889,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 15.34,
  :id "fplogs/277076930201794",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-01-05T17:42:23.000-00:00",
  :fplog/gallon-price 2.399,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202758",
  :fplog/octane 93,
  :fplog/num-gallons 13.459,
  :id "fplogs/277076930202768",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-11-08T01:40:32.000-00:00",
  :fplog/gallon-price 3.929,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205432",
  :fplog/octane 87,
  :fplog/num-gallons 13.457,
  :id "fplogs/277076930205442",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-19T14:45:31.000-00:00",
  :fplog/gallon-price 3.599,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 87,
  :fplog/num-gallons 15.249,
  :id "fplogs/277076930203247",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-04-24T00:22:40.000-00:00",
  :fplog/gallon-price 3.519,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 5.428,
  :id "fplogs/277076930202291",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-10-24T20:33:29.000-00:00",
  :fplog/gallon-price 3.039,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202310",
  :fplog/octane 93,
  :fplog/num-gallons 13.392,
  :id "fplogs/277076930204916",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-03-17T12:47:46.000-00:00",
  :fplog/gallon-price 3.749,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.871,
  :id "fplogs/277076930206120",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-06-25T13:13:59.000-00:00",
  :fplog/gallon-price 3.399,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 5.032,
  :id "fplogs/277076930204944",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-06-24T12:49:34.000-00:00",
  :fplog/gallon-price 3.769,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202310",
  :fplog/octane 93,
  :fplog/num-gallons 14.627,
  :id "fplogs/277076930202320",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-10-04T20:34:53.000-00:00",
  :fplog/gallon-price 3.729,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 15.787,
  :id "fplogs/277076930201775",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-12-16T17:41:04.000-00:00",
  :fplog/gallon-price 2.599,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202060",
  :fplog/octane 87,
  :fplog/num-gallons 12.567,
  :id "fplogs/277076930205422",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-18T14:42:16.000-00:00",
  :fplog/gallon-price 3.839,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 14.734,
  :id "fplogs/277076930203275",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-04-07T00:31:44.000-00:00",
  :fplog/gallon-price 3.72,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205402",
  :fplog/octane 87,
  :fplog/num-gallons 18.515,
  :id "fplogs/277076930205412",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-15T14:41:25.000-00:00",
  :fplog/gallon-price 3.839,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 12.621,
  :id "fplogs/277076930206167",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2011-11-09T14:16:39.000-00:00",
  :fplog/gallon-price 3.419,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203972",
  :fplog/octane 87,
  :fplog/num-gallons 12.236,
  :id "fplogs/277076930203982",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-01-03T02:43:51.000-00:00",
  :fplog/gallon-price 3.349,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 87,
  :fplog/num-gallons 17.732,
  :id "fplogs/277076930202607",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-06-06T20:56:26.000-00:00",
  :fplog/gallon-price 3.349,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 17.91,
  :id "fplogs/277076930202739",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-11-14T01:30:53.000-00:00",
  :fplog/gallon-price 3.389,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 93,
  :fplog/num-gallons 14.55,
  :id "fplogs/277076930204954",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-03-09T12:51:40.000-00:00",
  :fplog/gallon-price 3.779,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 12.087,
  :id "fplogs/277076930206148",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2011-11-22T14:15:13.000-00:00",
  :fplog/gallon-price 3.399,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 13.641,
  :id "fplogs/277076930201092",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-12-12T03:37:32.000-00:00",
  :fplog/gallon-price 3.279,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.831,
  :id "fplogs/277076930205094",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2011-12-22T14:14:04.000-00:00",
  :fplog/gallon-price 3.329,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202060",
  :fplog/octane 87,
  :fplog/num-gallons 11.16,
  :id "fplogs/277076930202092",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-16T20:18:58.000-00:00",
  :fplog/gallon-price 3.499,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 21.559,
  :id "fplogs/277076930207028",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-04-14T13:45:35.000-00:00",
  :fplog/gallon-price 2.299,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.193,
  :id "fplogs/277076930205952",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-04-22T12:52:37.000-00:00",
  :fplog/gallon-price 3.889,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.772,
  :id "fplogs/277076930202692",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-11-25T01:19:57.000-00:00",
  :fplog/gallon-price 3.829,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.68,
  :id "fplogs/277076930205653",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-01-27T12:41:22.000-00:00",
  :fplog/gallon-price 3.439,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.07,
  :id "fplogs/277076930205924",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-08-22T12:51:17.000-00:00",
  :fplog/gallon-price 3.679,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 17.654,
  :id "fplogs/277076930202720",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-07-30T00:29:22.000-00:00",
  :fplog/gallon-price 3.599,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206492",
  :fplog/car-wash-per-gal-discount 0.08,
  :fplog/octane 87,
  :fplog/num-gallons 5.608,
  :id "fplogs/277076930207556",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-23T15:27:50.000-00:00",
  :fplog/gallon-price 2.399,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 12.44,
  :id "fplogs/277076930205131",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-03-27T13:20:27.000-00:00",
  :fplog/gallon-price 3.759,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 17.81,
  :id "fplogs/277076930203208",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-02-03T13:34:43.000-00:00",
  :fplog/gallon-price 1.999,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.772,
  :id "fplogs/277076930205980",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-05-19T12:54:18.000-00:00",
  :fplog/gallon-price 3.73,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202111",
  :fplog/octane 87,
  :fplog/num-gallons 11.363,
  :id "fplogs/277076930202127",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-16T20:20:50.000-00:00",
  :fplog/gallon-price 3.459,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206259",
  :fplog/octane 87,
  :fplog/num-gallons 12.26,
  :id "fplogs/277076930206435",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-09-03T12:44:49.000-00:00",
  :fplog/gallon-price 3.679,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206297",
  :fplog/octane 87,
  :fplog/num-gallons 8.611,
  :id "fplogs/277076930206441",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-19T12:48:02.000-00:00",
  :fplog/gallon-price 3.559,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 7.963,
  :id "fplogs/277076930202673",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-11-30T01:18:37.000-00:00",
  :fplog/gallon-price 3.349,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.596,
  :id "fplogs/277076930205625",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-09-10T11:37:34.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 13.641,
  :id "fplogs/277076930203643",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-12-11T12:55:48.000-00:00",
  :fplog/gallon-price 3.279,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206492",
  :fplog/car-wash-per-gal-discount 0.08,
  :fplog/octane 87,
  :fplog/num-gallons 5.608,
  :id "fplogs/277076930207516",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-23T15:27:50.000-00:00",
  :fplog/gallon-price 2.399,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.549,
  :id "fplogs/277076930206008",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-06-17T12:56:57.000-00:00",
  :fplog/gallon-price 3.65,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202140",
  :fplog/octane 87,
  :fplog/num-gallons 5.607,
  :id "fplogs/277076930202156",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-16T20:23:14.000-00:00",
  :fplog/gallon-price 3.359,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202225",
  :fplog/octane 87,
  :fplog/num-gallons 12.058,
  :id "fplogs/277076930202626",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-10-26T20:58:54.000-00:00",
  :fplog/gallon-price 2.799,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.901,
  :id "fplogs/277076930206963",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-04-01T11:54:17.000-00:00",
  :fplog/gallon-price 2.269,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 14.431,
  :id "fplogs/277076930202654",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-26T00:17:00.000-00:00",
  :fplog/gallon-price 3.629,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 93,
  :fplog/num-gallons 13.733,
  :id "fplogs/277076930203132",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-05-14T05:07:18.000-00:00",
  :fplog/gallon-price 4.069,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 16.846,
  :id "fplogs/277076930205047",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-02-12T13:23:47.000-00:00",
  :fplog/gallon-price 2.139,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.083,
  :id "fplogs/277076930202169",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-08-19T20:27:11.000-00:00",
  :fplog/gallon-price 3.949,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.148,
  :id "fplogs/277076930206036",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-06-18T13:00:05.000-00:00",
  :fplog/gallon-price 3.459,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 17.598,
  :id "fplogs/277076930205542",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-12-29T15:58:19.000-00:00",
  :fplog/gallon-price 3.299,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206492",
  :fplog/octane 87,
  :fplog/num-gallons 14.852,
  :id "fplogs/277076930206527",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-03-01T16:12:01.000-00:00",
  :fplog/gallon-price 2.159,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.237,
  :id "fplogs/277076930205552",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-02-29T16:00:12.000-00:00",
  :fplog/gallon-price 3.679,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 15.012,
  :id "fplogs/277076930204642",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-07-02T12:17:52.000-00:00",
  :fplog/gallon-price 4.069,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.074,
  :id "fplogs/277076930204671",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-07-12T12:22:08.000-00:00",
  :fplog/gallon-price 4.039,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.21,
  :id "fplogs/277076930202197",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-08-27T20:28:34.000-00:00",
  :fplog/gallon-price 3.899,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206492",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 87,
  :fplog/num-gallons 15.785,
  :id "fplogs/277076930206502",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-02-22T08:07:43.000-00:00",
  :fplog/gallon-price 1.999,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204115",
  :fplog/octane 93,
  :fplog/num-gallons 14.634,
  :id "fplogs/277076930204125",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-06-06T01:10:18.000-00:00",
  :fplog/gallon-price 4.079,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 18.289,
  :id "fplogs/277076930204661",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-07-04T12:20:44.000-00:00",
  :fplog/gallon-price 3.609,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.901,
  :id "fplogs/277076930206982",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-04-01T11:54:17.000-00:00",
  :fplog/gallon-price 2.269,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.949,
  :id "fplogs/277076930203160",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-11-13T06:08:45.000-00:00",
  :fplog/gallon-price 3.339,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.558,
  :id "fplogs/277076930204179",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-09T01:18:32.000-00:00",
  :fplog/gallon-price 3.569,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 93,
  :fplog/num-gallons 15.058,
  :id "fplogs/277076930203570",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-05-26T01:14:01.000-00:00",
  :fplog/gallon-price 3.999,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 10.855,
  :id "fplogs/277076930205681",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-10-03T11:42:58.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202463",
  :fplog/octane 87,
  :fplog/num-gallons 17.649,
  :id "fplogs/277076930202482",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-05T20:45:02.000-00:00",
  :fplog/gallon-price 3.839,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 17.87,
  :id "fplogs/277076930206339",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-02-18T13:37:20.000-00:00",
  :fplog/gallon-price 3.749,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 15.139,
  :id "fplogs/277076930203104",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-07-25T05:05:50.000-00:00",
  :fplog/gallon-price 4.019,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 13.678,
  :id "fplogs/277076930206342",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-31T12:45:40.000-00:00",
  :fplog/gallon-price 3.869,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 4.853,
  :id "fplogs/277076930204189",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-22T01:19:59.000-00:00",
  :fplog/gallon-price 3.569,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201993",
  :fplog/octane 87,
  :fplog/num-gallons 11.906,
  :id "fplogs/277076930202003",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-09T20:13:44.000-00:00",
  :fplog/gallon-price 3.579,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.367,
  :id "fplogs/277076930204727",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-04-21T12:25:40.000-00:00",
  :fplog/gallon-price 4.049,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 11.492,
  :id "fplogs/277076930205793",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-01-31T12:53:21.000-00:00",
  :fplog/gallon-price 3.629,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206870",
  :fplog/octane 87,
  :fplog/num-gallons 15.886,
  :id "fplogs/277076930206899",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-03-25T11:21:03.000-00:00",
  :fplog/gallon-price 2.269,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 14.632,
  :id "fplogs/277076930205821",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2011-11-16T12:56:12.000-00:00",
  :fplog/gallon-price 3.349,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 93,
  :fplog/num-gallons 18.366,
  :id "fplogs/277076930205255",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-07-14T14:07:17.000-00:00",
  :fplog/gallon-price 3.559,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 17.128,
  :id "fplogs/277076930203085",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-09-19T05:04:22.000-00:00",
  :fplog/gallon-price 3.499,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 87,
  :fplog/num-gallons 15.179,
  :id "fplogs/277076930203671",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-01-01T12:57:02.000-00:00",
  :fplog/gallon-price 3.339,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202758",
  :fplog/octane 87,
  :fplog/num-gallons 18.311,
  :id "fplogs/277076930205245",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-05-09T14:05:25.000-00:00",
  :fplog/gallon-price 3.499,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 18.529,
  :id "fplogs/277076930206318",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-02-05T13:35:42.000-00:00",
  :fplog/gallon-price 3.699,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 18.591,
  :id "fplogs/277076930201974",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-05-31T20:11:42.000-00:00",
  :fplog/gallon-price 3.569,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 18.109,
  :id "fplogs/277076930206306",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-12-30T13:21:27.000-00:00",
  :fplog/gallon-price 3.349,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 11.319,
  :id "fplogs/277076930204755",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-05-05T12:27:54.000-00:00",
  :fplog/gallon-price 4.119,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203024",
  :fplog/octane 87,
  :fplog/num-gallons 8.108,
  :id "fplogs/277076930203043",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-23T01:03:29.000-00:00",
  :fplog/gallon-price 3.859,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202501",
  :fplog/octane 87,
  :fplog/num-gallons 15.987,
  :id "fplogs/277076930202511",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-01T20:47:24.000-00:00",
  :fplog/gallon-price 4.099,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 14.89,
  :id "fplogs/277076930205849",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-01-17T13:46:47.000-00:00",
  :fplog/gallon-price 3.549,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 87,
  :fplog/num-gallons 18.281,
  :id "fplogs/277076930206333",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-07-28T12:42:32.000-00:00",
  :fplog/gallon-price 3.439,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 20.259,
  :id "fplogs/277076930207410",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-21T12:30:16.000-00:00",
  :fplog/gallon-price 2.519,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 87,
  :fplog/num-gallons 18.61,
  :id "fplogs/277076930206324",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-01-22T13:32:58.000-00:00",
  :fplog/gallon-price 3.569,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205372",
  :fplog/octane 87,
  :fplog/num-gallons 16.835,
  :id "fplogs/277076930206321",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-04-01T12:23:31.000-00:00",
  :fplog/gallon-price 3.889,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201945",
  :fplog/octane 87,
  :fplog/num-gallons 9.831,
  :id "fplogs/277076930201955",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-07-01T20:10:56.000-00:00",
  :fplog/gallon-price 3.359,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.316,
  :id "fplogs/277076930205868",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-07-23T12:48:31.000-00:00",
  :fplog/gallon-price 3.399,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202060",
  :fplog/octane 87,
  :fplog/num-gallons 14.689,
  :id "fplogs/277076930202076",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-14T20:18:17.000-00:00",
  :fplog/gallon-price 3.579,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 12.072,
  :id "fplogs/277076930203699",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-02-04T12:58:37.000-00:00",
  :fplog/gallon-price 3.479,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 17.817,
  :id "fplogs/277076930206822",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-03-11T12:19:14.000-00:00",
  :fplog/gallon-price 2.299,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 93,
  :fplog/num-gallons 14.898,
  :id "fplogs/277076930203513",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-05-26T01:09:33.000-00:00",
  :fplog/gallon-price 3.869,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930204219",
  :fplog/octane 87,
  :fplog/num-gallons 10.092,
  :id "fplogs/277076930204229",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-23T01:23:52.000-00:00",
  :fplog/gallon-price 3.599,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206288",
  :fplog/octane 87,
  :fplog/num-gallons 17.714,
  :id "fplogs/277076930206429",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-02-27T13:39:17.000-00:00",
  :fplog/gallon-price 3.739,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206253",
  :fplog/octane 87,
  :fplog/num-gallons 17.83,
  :id "fplogs/277076930206432",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-02-16T13:18:35.000-00:00",
  :fplog/gallon-price 3.769,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205187",
  :fplog/octane 93,
  :fplog/num-gallons 14.427,
  :id "fplogs/277076930205197",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-07-04T13:37:26.000-00:00",
  :fplog/gallon-price 3.549,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202758",
  :fplog/octane 87,
  :fplog/num-gallons 18.106,
  :id "fplogs/277076930203493",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-04-28T01:07:23.000-00:00",
  :fplog/gallon-price 3.529,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206265",
  :fplog/octane 87,
  :fplog/num-gallons 16.559,
  :id "fplogs/277076930206426",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-04-03T12:41:22.000-00:00",
  :fplog/gallon-price 3.759,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202530",
  :fplog/octane 87,
  :fplog/num-gallons 16.148,
  :id "fplogs/277076930202540",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-01T20:50:15.000-00:00",
  :fplog/gallon-price 3.399,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206285",
  :fplog/octane 87,
  :fplog/num-gallons 16.946,
  :id "fplogs/277076930206423",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-12-18T13:26:04.000-00:00",
  :fplog/gallon-price 3.179,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 21.474,
  :id "fplogs/277076930207389",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-06-03T12:30:16.000-00:00",
  :fplog/gallon-price 2.579,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 12.298,
  :id "fplogs/277076930203503",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-05-18T01:08:20.000-00:00",
  :fplog/gallon-price 3.529,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206282",
  :fplog/octane 87,
  :fplog/num-gallons 17.112,
  :id "fplogs/277076930206420",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-12-22T13:33:57.000-00:00",
  :fplog/gallon-price 3.179,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 20.259,
  :id "fplogs/277076930207392",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-21T12:30:16.000-00:00",
  :fplog/gallon-price 2.519,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 17.384,
  :id "fplogs/277076930202041",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-09-28T20:16:31.000-00:00",
  :fplog/gallon-price 3.309,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202984",
  :fplog/octane 87,
  :fplog/num-gallons 17.568,
  :id "fplogs/277076930203000",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-02T01:00:22.000-00:00",
  :fplog/gallon-price 3.659,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.798,
  :id "fplogs/277076930204699",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-04-30T12:23:54.000-00:00",
  :fplog/gallon-price 4.069,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204199",
  :fplog/octane 87,
  :fplog/num-gallons 5.6,
  :id "fplogs/277076930204209",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-23T01:21:44.000-00:00",
  :fplog/gallon-price 3.799,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206841",
  :fplog/octane 87,
  :fplog/num-gallons 16.724,
  :id "fplogs/277076930206851",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-03-13T09:35:30.000-00:00",
  :fplog/gallon-price 2.199,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 18.356,
  :id "fplogs/277076930203541",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-03-27T01:11:16.000-00:00",
  :fplog/gallon-price 3.729,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 89,
  :fplog/num-gallons 12.856,
  :id "fplogs/277076930203551",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-05-17T01:12:20.000-00:00",
  :fplog/gallon-price 3.639,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 12.333,
  :id "fplogs/277076930205896",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-09-20T12:49:52.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202559",
  :fplog/octane 93,
  :fplog/num-gallons 13.937,
  :id "fplogs/277076930202569",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-07-07T20:51:23.000-00:00",
  :fplog/gallon-price 3.969,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206870",
  :fplog/octane 87,
  :fplog/num-gallons 17.649,
  :id "fplogs/277076930206880",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-03-18T11:14:36.000-00:00",
  :fplog/gallon-price 2.299,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.361,
  :id "fplogs/277076930205159",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-12-05T14:34:13.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.268,
  :id "fplogs/277076930202022",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-05-11T20:15:22.000-00:00",
  :fplog/gallon-price 3.619,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 89,
  :fplog/num-gallons 9.427,
  :id "fplogs/277076930203727",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-06-25T01:15:41.000-00:00",
  :fplog/gallon-price 3.969,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 93,
  :fplog/num-gallons 15.391,
  :id "fplogs/277076930201877",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-09-04T18:26:39.000-00:00",
  :fplog/gallon-price 3.959,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 14.0,
  :id "fplogs/277076930203455",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-09-30T00:54:12.000-00:00",
  :fplog/gallon-price 3.749,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/car-wash-per-gal-discount 0.15,
  :fplog/octane 93,
  :fplog/num-gallons 14.324,
  :id "fplogs/277076930204860",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-03-27T12:39:34.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash true}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.357,
  :id "fplogs/277076930202362",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-09-25T08:36:48.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.669,
  :id "fplogs/277076930203765",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-06-12T01:24:55.000-00:00",
  :fplog/gallon-price 3.719,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202955",
  :fplog/octane 87,
  :fplog/num-gallons 8.994,
  :id "fplogs/277076930202965",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-08-01T00:58:00.000-00:00",
  :fplog/gallon-price 3.259,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202310",
  :fplog/octane 87,
  :fplog/num-gallons 18.085,
  :id "fplogs/277076930206223",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-10-13T13:20:22.000-00:00",
  :fplog/gallon-price 3.799,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205372",
  :fplog/octane 87,
  :fplog/num-gallons 12.223,
  :id "fplogs/277076930205382",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-03T14:37:11.000-00:00",
  :fplog/gallon-price 3.489,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 15.079,
  :id "fplogs/277076930204298",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-02-14T04:27:41.000-00:00",
  :fplog/gallon-price 3.699,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.552,
  :id "fplogs/277076930203755",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-19T01:23:22.000-00:00",
  :fplog/gallon-price 3.559,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202984",
  :fplog/octane 87,
  :fplog/num-gallons 10.511,
  :id "fplogs/277076930205392",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-10T14:38:10.000-00:00",
  :fplog/gallon-price 3.739,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.195,
  :id "fplogs/277076930204841",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-02-03T13:35:45.000-00:00",
  :fplog/gallon-price 3.809,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.007,
  :id "fplogs/277076930203427",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-03-07T13:51:24.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203793",
  :fplog/octane 87,
  :fplog/num-gallons 10.606,
  :id "fplogs/277076930203803",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-18T01:26:16.000-00:00",
  :fplog/gallon-price 3.799,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 12.47,
  :id "fplogs/277076930204888",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-02-26T13:44:20.000-00:00",
  :fplog/gallon-price 3.809,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202984",
  :fplog/octane 87,
  :fplog/num-gallons 17.331,
  :id "fplogs/277076930205362",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-08-08T14:34:26.000-00:00",
  :fplog/gallon-price 3.699,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 14.484,
  :id "fplogs/277076930205709",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-10-31T11:45:24.000-00:00",
  :fplog/gallon-price 3.559,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 15.419,
  :id "fplogs/277076930206186",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2011-10-30T13:18:17.000-00:00",
  :fplog/gallon-price 3.429,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.642,
  :id "fplogs/277076930201849",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-06-16T18:24:23.000-00:00",
  :fplog/gallon-price 4.069,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204268",
  :fplog/octane 87,
  :fplog/num-gallons 12.562,
  :id "fplogs/277076930204278",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-29T01:37:16.000-00:00",
  :fplog/gallon-price 3.559,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 17.49,
  :id "fplogs/277076930201830",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-11-13T19:22:37.000-00:00",
  :fplog/gallon-price 2.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 18.555,
  :id "fplogs/277076930205352",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2012-04-14T14:32:37.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202310",
  :fplog/octane 93,
  :fplog/num-gallons 13.106,
  :id "fplogs/277076930202927",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-09-18T00:54:38.000-00:00",
  :fplog/gallon-price 3.899,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202758",
  :fplog/octane 87,
  :fplog/num-gallons 15.42,
  :id "fplogs/277076930203465",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2013-03-21T00:56:13.000-00:00",
  :fplog/gallon-price 3.719,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204248",
  :fplog/octane 87,
  :fplog/num-gallons 15.956,
  :id "fplogs/277076930204258",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-06-28T01:29:06.000-00:00",
  :fplog/gallon-price 3.579,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.178,
  :id "fplogs/277076930202384",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-10-14T20:38:44.000-00:00",
  :fplog/gallon-price 3.699,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205323",
  :fplog/octane 87,
  :fplog/num-gallons 17.984,
  :id "fplogs/277076930205333",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-12-03T15:31:02.000-00:00",
  :fplog/gallon-price 3.279,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.083,
  :id "fplogs/277076930205737",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-12-23T12:48:34.000-00:00",
  :fplog/gallon-price 3.819,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203816",
  :fplog/octane 87,
  :fplog/num-gallons 6.996,
  :id "fplogs/277076930203826",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-18T01:30:37.000-00:00",
  :fplog/gallon-price 4.029,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.366,
  :id "fplogs/277076930202899",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-10-17T00:52:39.000-00:00",
  :fplog/gallon-price 3.849,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204783",
  :fplog/octane 87,
  :fplog/num-gallons 18.685,
  :id "fplogs/277076930204793",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-04-16T12:29:14.000-00:00",
  :fplog/gallon-price 3.489,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930204288",
  :fplog/octane 87,
  :fplog/num-gallons 17.652,
  :id "fplogs/277076930205313",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-09-05T14:27:48.000-00:00",
  :fplog/gallon-price 3.699,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 15.687,
  :id "fplogs/277076930203371",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-10-15T00:40:00.000-00:00",
  :fplog/gallon-price 3.719,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201916",
  :fplog/octane 87,
  :fplog/num-gallons 12.059,
  :id "fplogs/277076930201926",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-12T20:08:21.000-00:00",
  :fplog/gallon-price 3.739,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/octane 87,
  :fplog/num-gallons 8.589,
  :id "fplogs/277076930206300",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-01-16T13:15:49.000-00:00",
  :fplog/gallon-price 3.449,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.022,
  :id "fplogs/277076930202412",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-10-22T20:40:13.000-00:00",
  :fplog/gallon-price 3.099,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203856",
  :fplog/octane 87,
  :fplog/num-gallons 13.852,
  :id "fplogs/277076930203866",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-06-03T01:34:13.000-00:00",
  :fplog/gallon-price 3.199,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930206233",
  :fplog/octane 87,
  :fplog/num-gallons 18.338,
  :id "fplogs/277076930206243",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-01-21T14:21:50.000-00:00",
  :fplog/gallon-price 3.419,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 13.086,
  :id "fplogs/277076930205765",
  :fplog/vehicle 4,
  :fplog/purchased-at
  #inst "2012-04-15T11:51:42.000-00:00",
  :fplog/gallon-price 3.88,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930205293",
  :fplog/octane 87,
  :fplog/num-gallons 18.152,
  :id "fplogs/277076930205303",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-10-10T14:21:43.000-00:00",
  :fplog/gallon-price 3.379,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/car-wash-per-gal-discount 0.0,
  :fplog/octane 87,
  :fplog/num-gallons 16.471,
  :id "fplogs/277076930201477",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2015-01-20T14:00:45.000-00:00",
  :fplog/gallon-price 2.149,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 20.973,
  :id "fplogs/277076930207204",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-12T12:29:06.000-00:00",
  :fplog/gallon-price 2.459,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 14.768,
  :id "fplogs/277076930202871",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-10-26T00:50:27.000-00:00",
  :fplog/gallon-price 3.969,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 18.357,
  :id "fplogs/277076930205283",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2011-10-02T14:10:43.000-00:00",
  :fplog/gallon-price 3.479,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930201047",
  :fplog/car-wash-per-gal-discount 0.08,
  :fplog/octane 87,
  :fplog/num-gallons 7.086,
  :id "fplogs/277076930207232",
  :fplog/vehicle 0,
  :fplog/purchased-at
  #inst "2015-05-15T09:06:15.000-00:00",
  :fplog/gallon-price 2.539,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 87,
  :fplog/num-gallons 16.871,
  :id "fplogs/277076930204803",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2014-04-12T12:31:56.000-00:00",
  :fplog/gallon-price 3.529,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202431",
  :fplog/octane 87,
  :fplog/num-gallons 15.707,
  :id "fplogs/277076930202447",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-10T20:41:07.000-00:00",
  :fplog/gallon-price 3.799,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930202597",
  :fplog/octane 93,
  :fplog/num-gallons 14.766,
  :id "fplogs/277076930204813",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2014-04-05T12:32:54.000-00:00",
  :fplog/gallon-price 3.889,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930203836",
  :fplog/octane 87,
  :fplog/num-gallons 18.135,
  :id "fplogs/277076930203846",
  :fplog/vehicle 2,
  :fplog/purchased-at
  #inst "2013-08-16T01:31:38.000-00:00",
  :fplog/gallon-price 3.719,
  :fplog/got-car-wash false}
 {:fplog/fuelstation "fuelstations/277076930200624",
  :fplog/octane 93,
  :fplog/num-gallons 13.056,
  :id "fplogs/277076930203399",
  :fplog/vehicle 3,
  :fplog/purchased-at
  #inst "2013-05-17T00:49:11.000-00:00",
  :fplog/gallon-price 3.859,
  :fplog/got-car-wash false}])

(def fplog-entids-map {"fplogs/277076930204229" 118,
 "fplogs/277076930205500" 15,
 "fplogs/277076930205047" 70,
 "fplogs/277076930204860" 145,
 "fplogs/277076930205528" 6,
 "fplogs/277076930206420" 128,
 "fplogs/277076930205245" 100,
 "fplogs/277076930202076" 114,
 "fplogs/277076930203934" 16,
 "fplogs/277076930202263" 20,
 "fplogs/277076930203237" 23,
 "fplogs/277076930205952" 48,
 "fplogs/277076930206120" 31,
 "fplogs/277076930206036" 72,
 "fplogs/277076930207232" 187,
 "fplogs/277076930206441" 59,
 "fplogs/277076930203513" 117,
 "fplogs/277076930204841" 154,
 "fplogs/277076930202169" 71,
 "fplogs/277076930205896" 137,
 "fplogs/277076930205442" 27,
 "fplogs/277076930205255" 97,
 "fplogs/277076930203570" 85,
 "fplogs/277076930201735" 1,
 "fplogs/277076930205131" 54,
 "fplogs/277076930202127" 57,
 "fplogs/277076930202768" 26,
 "fplogs/277076930204189" 91,
 "fplogs/277076930203727" 142,
 "fplogs/277076930202654" 68,
 "fplogs/277076930204699" 132,
 "fplogs/277076930203104" 89,
 "fplogs/277076930205382" 150,
 "fplogs/277076930202412" 178,
 "fplogs/277076930204258" 167,
 "fplogs/277076930206321" 111,
 "fplogs/277076930204661" 81,
 "fplogs/277076930204916" 30,
 "fplogs/277076930206432" 120,
 "fplogs/277076930205709" 159,
 "fplogs/277076930206963" 67,
 "fplogs/277076930202320" 33,
 "fplogs/277076930204642" 76,
 "fplogs/277076930202092" 46,
 "fplogs/277076930205924" 51,
 "fplogs/277076930206527" 74,
 "fplogs/277076930203551" 136,
 "fplogs/277076930202569" 138,
 "fplogs/277076930207516" 63,
 "fplogs/277076930204671" 77,
 "fplogs/277076930206167" 38,
 "fplogs/277076930206342" 90,
 "fplogs/277076930201974" 102,
 "fplogs/277076930201703" 11,
 "fplogs/277076930203208" 55,
 "fplogs/277076930203000" 131,
 "fplogs/277076930206306" 103,
 "fplogs/277076930201849" 161,
 "fplogs/277076930202626" 66,
 "fplogs/277076930206822" 116,
 "fplogs/277076930205094" 45,
 "fplogs/277076930202003" 92,
 "fplogs/277076930205422" 35,
 "fplogs/277076930203247" 28,
 "fplogs/277076930206064" 4,
 "fplogs/277076930204954" 42,
 "fplogs/277076930202362" 146,
 "fplogs/277076930205352" 164,
 "fplogs/277076930202720" 52,
 "fplogs/277076930203765" 147,
 "fplogs/277076930207556" 53,
 "fplogs/277076930202692" 49,
 "fplogs/277076930203465" 166,
 "fplogs/277076930202291" 29,
 "fplogs/277076930202197" 78,
 "fplogs/277076930202965" 148,
 "fplogs/277076930206300" 177,
 "fplogs/277076930203846" 191,
 "fplogs/277076930205392" 153,
 "fplogs/277076930206435" 58,
 "fplogs/277076930203699" 115,
 "fplogs/277076930202511" 106,
 "fplogs/277076930204944" 32,
 "fplogs/277076930201092" 44,
 "fplogs/277076930205980" 56,
 "fplogs/277076930205868" 113,
 "fplogs/277076930207392" 129,
 "fplogs/277076930203043" 105,
 "fplogs/277076930204727" 93,
 "fplogs/277076930205452" 22,
 "fplogs/277076930202540" 124,
 "fplogs/277076930202739" 41,
 "fplogs/277076930202796" 12,
 "fplogs/277076930204125" 80,
 "fplogs/277076930201477" 183,
 "fplogs/277076930204793" 173,
 "fplogs/277076930204803" 188,
 "fplogs/277076930201877" 143,
 "fplogs/277076930203371" 175,
 "fplogs/277076930203160" 83,
 "fplogs/277076930207184" 18,
 "fplogs/277076930205333" 169,
 "fplogs/277076930206880" 139,
 "fplogs/277076930205159" 140,
 "fplogs/277076930202871" 185,
 "fplogs/277076930206426" 123,
 "fplogs/277076930204888" 157,
 "fplogs/277076930206092" 14,
 "fplogs/277076930203914" 13,
 "fplogs/277076930204179" 84,
 "fplogs/277076930203085" 98,
 "fplogs/277076930200648" 10,
 "fplogs/277076930201775" 34,
 "fplogs/277076930206502" 79,
 "fplogs/277076930206223" 149,
 "fplogs/277076930201955" 112,
 "fplogs/277076930206148" 43,
 "fplogs/277076930207389" 126,
 "fplogs/277076930205765" 181,
 "fplogs/277076930202607" 40,
 "fplogs/277076930203427" 155,
 "fplogs/277076930202041" 130,
 "fplogs/277076930205821" 96,
 "fplogs/277076930206423" 125,
 "fplogs/277076930206339" 88,
 "fplogs/277076930203643" 62,
 "fplogs/277076930203503" 127,
 "fplogs/277076930207204" 184,
 "fplogs/277076930206333" 108,
 "fplogs/277076930203755" 152,
 "fplogs/277076930205542" 73,
 "fplogs/277076930205552" 75,
 "fplogs/277076930206982" 82,
 "fplogs/277076930202447" 189,
 "fplogs/277076930205283" 186,
 "fplogs/277076930206008" 64,
 "fplogs/277076930203876" 0,
 "fplogs/277076930203826" 171,
 "fplogs/277076930205849" 107,
 "fplogs/277076930202927" 165,
 "fplogs/277076930203803" 156,
 "fplogs/277076930205197" 121,
 "fplogs/277076930205737" 170,
 "fplogs/277076930207410" 109,
 "fplogs/277076930203944" 24,
 "fplogs/277076930205313" 174,
 "fplogs/277076930203399" 192,
 "fplogs/277076930206899" 95,
 "fplogs/277076930206851" 134,
 "fplogs/277076930207093" 21,
 "fplogs/277076930203455" 144,
 "fplogs/277076930205412" 37,
 "fplogs/277076930206429" 119,
 "fplogs/277076930203132" 69,
 "fplogs/277076930202022" 141,
 "fplogs/277076930206318" 101,
 "fplogs/277076930205681" 86,
 "fplogs/277076930202235" 3,
 "fplogs/277076930203493" 122,
 "fplogs/277076930203313" 5,
 "fplogs/277076930202815" 19,
 "fplogs/277076930205362" 158,
 "fplogs/277076930202482" 87,
 "fplogs/277076930207165" 2,
 "fplogs/277076930202899" 172,
 "fplogs/277076930201926" 176,
 "fplogs/277076930207028" 47,
 "fplogs/277076930205653" 50,
 "fplogs/277076930204298" 151,
 "fplogs/277076930203866" 179,
 "fplogs/277076930205303" 182,
 "fplogs/277076930203982" 39,
 "fplogs/277076930202156" 65,
 "fplogs/277076930203671" 99,
 "fplogs/277076930204813" 190,
 "fplogs/277076930204209" 133,
 "fplogs/277076930206324" 110,
 "fplogs/277076930205793" 94,
 "fplogs/277076930202673" 60,
 "fplogs/277076930203541" 135,
 "fplogs/277076930202843" 8,
 "fplogs/277076930205010" 17,
 "fplogs/277076930206186" 160,
 "fplogs/277076930204278" 162,
 "fplogs/277076930204982" 9,
 "fplogs/277076930203904" 7,
 "fplogs/277076930204755" 104,
 "fplogs/277076930202384" 168,
 "fplogs/277076930205625" 61,
 "fplogs/277076930201830" 163,
 "fplogs/277076930206243" 180,
 "fplogs/277076930201794" 25,
 "fplogs/277076930203275" 36})

(def envlogs [{:envlog/logged-at #inst "2015-05-02T14:31:27.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 224,
               :envlog/reported-outside-temp 67.0,
               :envlog/odometer 1288.0,
               :envlog/reported-avg-mpg 20.1,
               :id
               "envlogs/277076930207155"}
              {:envlog/logged-at #inst "2012-06-01T13:09:53.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 408,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 91765.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930206073"}
              {:envlog/logged-at #inst "2013-10-09T00:45:29.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 422,
               :envlog/reported-outside-temp 61.0,
               :envlog/odometer 24850.0,
               :envlog/reported-avg-mph 20.8,
               :envlog/reported-avg-mpg 20.8,
               :id
               "envlogs/277076930202824"}
              {:envlog/logged-at #inst "2012-06-01T13:09:53.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 32,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 91765.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930206076"}
              {:envlog/logged-at #inst "2013-02-16T02:36:21.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 404,
               :envlog/reported-outside-temp 53.0,
               :envlog/odometer 26684.0,
               :envlog/reported-avg-mph 21.9,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930203885"}
              {:envlog/logged-at #inst "2012-11-27T15:54:21.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 387,
               :envlog/reported-outside-temp 51.0,
               :envlog/odometer 95633.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 24.2,
               :id
               "envlogs/277076930205512"}
              {:envlog/logged-at #inst "2013-10-09T00:45:29.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 75,
               :envlog/reported-outside-temp 61.0,
               :envlog/odometer 24850.0,
               :envlog/reported-avg-mph 20.8,
               :envlog/reported-avg-mpg 20.8,
               :id
               "envlogs/277076930202827"}
              {:envlog/logged-at #inst "2014-08-27T20:28:34.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 334,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 33379.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930202209"}
              {:envlog/logged-at #inst "2013-02-16T02:36:21.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 39,
               :envlog/reported-outside-temp 53.0,
               :envlog/odometer 26684.0,
               :envlog/reported-avg-mph 21.9,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930203888"}
              {:envlog/logged-at #inst "2014-03-09T12:51:40.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 40,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 28821.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204966"}
              {:envlog/logged-at #inst "2014-12-05T17:28:53.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 53.0,
               :envlog/odometer 102927.0,
               :id
               "envlogs/277076930201732"}
              {:envlog/logged-at #inst "2012-11-27T15:54:21.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 56,
               :envlog/reported-outside-temp 51.0,
               :envlog/odometer 95633.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 24.2,
               :id
               "envlogs/277076930205506"}
              {:envlog/logged-at #inst "2014-02-17T13:54:14.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 413,
               :envlog/reported-outside-temp 50.0,
               :envlog/odometer 28234.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204991"}
              {:envlog/logged-at #inst "2012-12-12T03:40:07.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 65,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 95984.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.9,
               :id
               "envlogs/277076930201128"}
              {:envlog/logged-at #inst "2011-10-18T00:33:41.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 71,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 86520.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930203325"}
              {:envlog/logged-at #inst "2011-10-18T00:33:41.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 391,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 86520.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930203322"}
              {:envlog/logged-at #inst "2015-01-31T13:23:05.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 30.0,
               :envlog/odometer 104089.0,
               :id
               "envlogs/277076930201709"}
              {:envlog/logged-at #inst "2014-09-05T20:29:45.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 359,
               :envlog/reported-outside-temp 88.0,
               :envlog/odometer 33599.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930202241"}
              {:envlog/logged-at #inst "2012-07-01T13:11:59.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 420,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 92765.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930206104"}
              {:envlog/logged-at #inst "2014-02-17T13:54:14.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 66,
               :envlog/reported-outside-temp 50.0,
               :envlog/odometer 28234.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204994"}
              {:envlog/logged-at #inst "2015-05-10T06:32:56.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 214,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 1604.0,
               :envlog/reported-avg-mpg 20.9,
               :id
               "envlogs/277076930207194"}
              {:envlog/logged-at #inst "2014-09-05T20:29:45.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 110,
               :envlog/reported-outside-temp 88.0,
               :envlog/odometer 33599.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930202247"}
              {:envlog/logged-at #inst "2012-07-01T13:11:59.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 57,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 92765.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930206098"}
              {:envlog/logged-at #inst "2015-05-03T14:21:12.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 177,
               :envlog/reported-outside-temp 72.0,
               :envlog/odometer 1315.0,
               :envlog/reported-avg-mpg 19.4,
               :id
               "envlogs/277076930207171"}
              {:envlog/logged-at #inst "2013-10-28T00:44:15.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202802"}
              {:envlog/logged-at #inst "2014-09-15T20:32:19.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 400,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 33890.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.0,
               :id
               "envlogs/277076930202269"}
              {:envlog/logged-at #inst "2014-01-02T13:56:41.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 462,
               :envlog/reported-outside-temp 24.0,
               :envlog/odometer 27627.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930205022"}
              {:envlog/logged-at #inst "2015-01-31T23:55:26.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 47.0,
               :envlog/odometer 104355.0,
               :id
               "envlogs/277076930201722"}
              {:envlog/logged-at #inst "2014-01-02T13:56:41.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 76,
               :envlog/reported-outside-temp 24.0,
               :envlog/odometer 27627.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930205016"}
              {:envlog/logged-at #inst "2012-06-25T13:13:59.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 420,
               :envlog/reported-outside-temp 82.0,
               :envlog/odometer 92430.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930206129"}
              {:envlog/logged-at #inst "2012-06-25T13:13:59.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 58,
               :envlog/reported-outside-temp 82.0,
               :envlog/odometer 92430.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930206132"}
              {:envlog/logged-at #inst "2014-02-26T13:44:20.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 369,
               :envlog/reported-outside-temp 41.0,
               :envlog/odometer 28504.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204897"}
              {:envlog/logged-at #inst "2014-09-15T20:32:19.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 52,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 33890.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.0,
               :id
               "envlogs/277076930202275"}
              {:envlog/logged-at #inst "2014-01-10T02:41:55.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 41,
               :envlog/reported-outside-temp 24.0,
               :envlog/odometer 27304.0,
               :envlog/reported-avg-mph 20.3,
               :envlog/reported-avg-mpg 21.3,
               :id
               "envlogs/277076930203950"}
              {:envlog/logged-at #inst "2015-04-24T11:28:26.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 20,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 1028.0,
               :envlog/reported-avg-mpg 20.2,
               :id
               "envlogs/277076930207099"}
              {:envlog/logged-at #inst "2015-01-05T17:42:23.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 103482.0,
               :id
               "envlogs/277076930201800"}
              {:envlog/logged-at #inst "2014-02-26T13:44:20.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 89,
               :envlog/reported-outside-temp 41.0,
               :envlog/odometer 28504.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204900"}
              {:envlog/logged-at #inst "2014-03-17T12:47:46.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 441,
               :envlog/reported-outside-temp 36.0,
               :envlog/odometer 29125.0,
               :envlog/reported-avg-mph 24.8,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204928"}
              {:envlog/logged-at #inst "2014-01-10T02:41:55.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 380,
               :envlog/reported-outside-temp 24.0,
               :envlog/odometer 27304.0,
               :envlog/reported-avg-mph 20.3,
               :envlog/reported-avg-mpg 21.3,
               :id
               "envlogs/277076930203956"}
              {:envlog/logged-at #inst "2014-03-17T12:47:46.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 76,
               :envlog/reported-outside-temp 36.0,
               :envlog/odometer 29125.0,
               :envlog/reported-avg-mph 24.8,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204922"}
              {:envlog/logged-at #inst "2013-04-24T00:22:40.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 48,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 98572.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.5,
               :id
               "envlogs/277076930203256"}
              {:envlog/logged-at #inst "2014-10-24T20:33:29.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 69.0,
               :envlog/odometer 101789.0,
               :id
               "envlogs/277076930202297"}
              {:envlog/logged-at #inst "2013-04-24T00:22:40.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 388,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 98572.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.5,
               :id
               "envlogs/277076930203259"}
              {:envlog/logged-at #inst "2013-02-19T15:47:08.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 53,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 97222.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930205464"}
              {:envlog/logged-at #inst "2013-11-08T01:40:32.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 75,
               :envlog/reported-outside-temp 93.0,
               :envlog/odometer 25758.0,
               :envlog/reported-avg-mph 21.6,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930202777"}
              {:envlog/logged-at #inst "2013-11-08T01:40:32.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 426,
               :envlog/reported-outside-temp 93.0,
               :envlog/odometer 25758.0,
               :envlog/reported-avg-mph 21.6,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930202780"}
              {:envlog/logged-at #inst "2013-02-19T15:47:08.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 390,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 97222.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930205461"}
              {:envlog/logged-at #inst "2013-07-30T00:29:22.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202726"}
              {:envlog/logged-at #inst "2011-11-09T14:16:39.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 430,
               :envlog/reported-outside-temp 45.0,
               :envlog/odometer 87191.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930206173"}
              {:envlog/logged-at #inst "2013-06-06T20:56:26.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202613"}
              {:envlog/logged-at #inst "2014-03-09T12:51:40.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 462,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 28821.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204960"}
              {:envlog/logged-at #inst "2011-11-22T14:15:13.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 110,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 87481.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930206154"}
              {:envlog/logged-at #inst "2013-04-07T00:31:44.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 81,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 98233.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203284"}
              {:envlog/logged-at #inst "2014-10-04T20:34:53.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 45,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 34512.0,
               :envlog/reported-avg-mph 26.3,
               :envlog/reported-avg-mpg 22.4,
               :id
               "envlogs/277076930202332"}
              {:envlog/logged-at #inst "2015-06-03T12:02:59.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 463,
               :envlog/reported-outside-temp 66.0,
               :envlog/odometer 2840.0,
               :envlog/reported-avg-mpg 19.3,
               :id
               "envlogs/277076930207591"}
              {:envlog/logged-at #inst "2013-04-07T00:31:44.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 484,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 98233.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203287"}
              {:envlog/logged-at #inst "2015-04-24T11:28:26.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 482,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 1028.0,
               :envlog/reported-avg-mpg 20.2,
               :id
               "envlogs/277076930207105"}
              {:envlog/logged-at #inst "2014-10-04T20:34:53.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 467,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 34512.0,
               :envlog/reported-avg-mph 26.3,
               :envlog/reported-avg-mpg 22.4,
               :id
               "envlogs/277076930202326"}
              {:envlog/logged-at #inst "2014-12-16T17:41:04.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 49.0,
               :envlog/odometer 103217.0,
               :id
               "envlogs/277076930201781"}
              {:envlog/logged-at #inst "2013-11-14T01:30:53.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202745"}
              {:envlog/logged-at #inst "2015-06-03T12:02:37.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 67,
               :envlog/reported-outside-temp 66.0,
               :envlog/odometer 2840.0,
               :envlog/reported-avg-mpg 19.3,
               :id
               "envlogs/277076930207581"}
              {:envlog/logged-at #inst "2012-09-10T11:37:34.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 57,
               :envlog/reported-outside-temp 62.0,
               :envlog/odometer 93779.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205637"}
              {:envlog/logged-at #inst "2013-11-25T01:19:57.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 40,
               :envlog/reported-outside-temp 32.0,
               :envlog/odometer 26070.0,
               :envlog/reported-avg-mph 21.7,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930202701"}
              {:envlog/logged-at #inst "2015-04-14T13:45:35.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 53,
               :envlog/reported-outside-temp 67.0,
               :envlog/odometer 610.0,
               :envlog/reported-avg-mpg 21.8,
               :id
               "envlogs/277076930207037"}
              {:envlog/logged-at #inst "2015-04-14T13:45:35.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 519,
               :envlog/reported-outside-temp 67.0,
               :envlog/odometer 610.0,
               :envlog/reported-avg-mpg 21.8,
               :id
               "envlogs/277076930207040"}
              {:envlog/logged-at #inst "2013-11-25T01:19:57.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 417,
               :envlog/reported-outside-temp 32.0,
               :envlog/odometer 26070.0,
               :envlog/reported-avg-mph 21.7,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930202704"}
              {:envlog/logged-at #inst "2012-11-13T06:08:45.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 65,
               :envlog/reported-outside-temp 50.0,
               :envlog/odometer 95288.0,
               :envlog/reported-avg-mph 22.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203172"}
              {:envlog/logged-at #inst "2011-12-22T14:14:04.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 385,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 88151.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930205103"}
              {:envlog/logged-at #inst "2012-11-13T06:08:45.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 433,
               :envlog/reported-outside-temp 50.0,
               :envlog/odometer 95288.0,
               :envlog/reported-avg-mph 22.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203169"}
              {:envlog/logged-at #inst "2015-01-26T22:18:00.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 52.0,
               :envlog/odometer 103969.0,
               :id
               "envlogs/277076930201621"}
              {:envlog/logged-at #inst "2011-12-22T14:14:04.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 54,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 88151.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930205106"}
              {:envlog/logged-at #inst "2014-08-16T20:18:58.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202098"}
              {:envlog/logged-at #inst "2014-08-06T05:11:36.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930203198"}
              {:envlog/logged-at #inst "2015-02-20T13:26:21.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 15.0,
               :envlog/odometer 104967.0,
               :id
               "envlogs/277076930206473"}
              {:envlog/logged-at #inst "2013-01-27T12:41:22.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 83,
               :envlog/reported-outside-temp 34.0,
               :envlog/odometer 96617.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930205662"}
              {:envlog/logged-at #inst "2012-08-22T12:51:17.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 459,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 93435.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205936"}
              {:envlog/logged-at #inst "2012-08-22T12:51:17.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 70,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 93435.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205930"}
              {:envlog/logged-at #inst "2015-05-23T15:27:50.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 483,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 2429.0,
               :envlog/reported-avg-mpg 23.3,
               :id
               "envlogs/277076930207550"}
              {:envlog/logged-at #inst "2015-02-03T13:34:43.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 30.0,
               :envlog/odometer 104460.0,
               :id
               "envlogs/277076930203214"}
              {:envlog/logged-at #inst "2015-05-23T15:27:50.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 559,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 2429.0,
               :envlog/reported-avg-mpg 23.3,
               :id
               "envlogs/277076930207547"}
              {:envlog/logged-at #inst "2013-08-26T00:17:00.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202660"}
              {:envlog/logged-at #inst "2014-08-16T20:20:50.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202121"}
              {:envlog/logged-at #inst "2012-03-27T13:20:27.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 432,
               :envlog/reported-outside-temp 51.0,
               :envlog/odometer 90138.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205137"}
              {:envlog/logged-at #inst "2012-03-27T13:20:27.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 109,
               :envlog/reported-outside-temp 51.0,
               :envlog/odometer 90138.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205143"}
              {:envlog/logged-at #inst "2015-05-23T15:27:50.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 483,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 2429.0,
               :envlog/reported-avg-mpg 23.3,
               :id
               "envlogs/277076930207525"}
              {:envlog/logged-at #inst "2012-09-10T11:37:34.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 412,
               :envlog/reported-outside-temp 62.0,
               :envlog/odometer 93779.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205631"}
              {:envlog/logged-at #inst "2015-05-23T15:27:50.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 559,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 2429.0,
               :envlog/reported-avg-mpg 23.3,
               :id
               "envlogs/277076930207528"}
              {:envlog/logged-at #inst "2013-11-30T01:18:37.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202679"}
              {:envlog/logged-at #inst "2012-04-22T12:52:37.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 68,
               :envlog/reported-outside-temp 57.0,
               :envlog/odometer 90799.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205964"}
              {:envlog/logged-at #inst "2012-04-22T12:52:37.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 422,
               :envlog/reported-outside-temp 57.0,
               :envlog/odometer 90799.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205961"}
              {:envlog/logged-at #inst "2014-07-25T05:05:50.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 24,
               :envlog/reported-outside-temp 69.0,
               :envlog/odometer 32786.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930203116"}
              {:envlog/logged-at #inst "2015-02-09T10:44:00.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 57.0,
               :envlog/odometer 104653.0,
               :id
               "envlogs/277076930204623"}
              {:envlog/logged-at #inst "2014-08-16T20:23:14.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202150"}
              {:envlog/logged-at #inst "2014-07-25T05:05:50.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 392,
               :envlog/reported-outside-temp 69.0,
               :envlog/odometer 32786.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930203113"}
              {:envlog/logged-at #inst "2015-04-01T11:54:17.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 61.0,
               :envlog/odometer 106940.0,
               :id
               "envlogs/277076930206969"}
              {:envlog/logged-at #inst "2014-10-26T20:58:54.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 83.0,
               :envlog/odometer 102057.0,
               :id
               "envlogs/277076930202632"}
              {:envlog/logged-at #inst "2012-05-19T12:54:18.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 67,
               :envlog/reported-outside-temp 67.0,
               :envlog/odometer 91098.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205986"}
              {:envlog/logged-at #inst "2012-05-19T12:54:18.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 391,
               :envlog/reported-outside-temp 67.0,
               :envlog/odometer 91098.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205992"}
              {:envlog/logged-at #inst "2015-02-12T13:23:47.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 40.0,
               :envlog/odometer 104754.0,
               :id
               "envlogs/277076930205053"}
              {:envlog/logged-at #inst "2014-08-19T08:27:11.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 53,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 33086.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930202181"}
              {:envlog/logged-at #inst "2014-11-25T12:46:29.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 63.0,
               :envlog/odometer 102631.0,
               :id
               "envlogs/277076930200693"}
              {:envlog/logged-at #inst "2014-08-19T20:27:11.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 373,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 33086.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930202178"}
              {:envlog/logged-at #inst "2014-05-14T05:07:18.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 56,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 30941.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930203138"}
              {:envlog/logged-at #inst "2014-07-02T12:17:52.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 441,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 32165.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204648"}
              {:envlog/logged-at #inst "2012-06-18T13:00:05.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 71,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 92105.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930206042"}
              {:envlog/logged-at #inst "2012-06-18T13:00:05.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 443,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 92105.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930206048"}
              {:envlog/logged-at #inst "2014-05-14T05:07:18.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 396,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 30941.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930203144"}
              {:envlog/logged-at #inst "2015-03-01T16:12:01.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 45.0,
               :envlog/odometer 105316.0,
               :id
               "envlogs/277076930206524"}
              {:envlog/logged-at #inst "2012-06-17T12:56:57.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 419,
               :envlog/reported-outside-temp 69.0,
               :envlog/odometer 91419.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930206020"}
              {:envlog/logged-at #inst "2012-06-17T12:56:57.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 81,
               :envlog/reported-outside-temp 69.0,
               :envlog/odometer 91419.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930206017"}
              {:envlog/logged-at #inst "2015-04-01T11:54:17.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 61.0,
               :envlog/odometer 106940.0,
               :id
               "envlogs/277076930206988"}
              {:envlog/logged-at #inst "2012-02-29T16:00:12.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 402,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 89443.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205561"}
              {:envlog/logged-at #inst "2014-08-27T20:28:34.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 42,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 33379.0,
               :envlog/reported-avg-mph 25.1,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930202206"}
              {:envlog/logged-at #inst "2012-02-29T16:00:12.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 100,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 89443.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205564"}
              {:envlog/logged-at #inst "2015-02-22T08:07:43.000-00:00",
               :envlog/vehicle
               2,
               :envlog/odometer 105035.0,
               :id
               "envlogs/277076930206508"}
              {:envlog/logged-at #inst "2012-10-03T11:42:58.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 152,
               :envlog/reported-outside-temp 72.0,
               :envlog/odometer 94299.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205687"}
              {:envlog/logged-at #inst "2013-09-19T05:04:22.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930203091"}
              {:envlog/logged-at #inst "2014-05-26T01:14:01.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 30,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 31251.0,
               :envlog/reported-avg-mph 24.8,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930203576"}
              {:envlog/logged-at #inst "2012-01-31T12:53:21.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 435,
               :envlog/reported-outside-temp 39.0,
               :envlog/odometer 88810.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205805"}
              {:envlog/logged-at #inst "2014-04-21T12:25:40.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 413,
               :envlog/reported-outside-temp 64.0,
               :envlog/odometer 30083.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204736"}
              {:envlog/logged-at #inst "2015-05-21T12:30:16.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 561,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 2315.0,
               :envlog/reported-avg-mpg 23.4,
               :id
               "envlogs/277076930207425"}
              {:envlog/logged-at #inst "2015-03-18T11:14:36.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 53.0,
               :envlog/odometer 106390.0,
               :id
               "envlogs/277076930206886"}
              {:envlog/logged-at #inst "2012-01-31T12:53:21.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 61,
               :envlog/reported-outside-temp 39.0,
               :envlog/odometer 88810.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205802"}
              {:envlog/logged-at #inst "2014-08-09T20:13:44.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202009"}
              {:envlog/logged-at #inst "2014-05-26T01:14:01.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 422,
               :envlog/reported-outside-temp 68.0,
               :envlog/odometer 31251.0,
               :envlog/reported-avg-mph 24.8,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930203582"}
              {:envlog/logged-at #inst "2012-10-03T11:42:58.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 449,
               :envlog/reported-outside-temp 72.0,
               :envlog/odometer 94299.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205693"}
              {:envlog/logged-at #inst "2013-08-05T20:45:02.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202488"}
              {:envlog/logged-at #inst "2013-07-14T14:07:17.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 18,
               :envlog/reported-outside-temp 81.0,
               :envlog/odometer 23335.0,
               :envlog/reported-avg-mph 23.4,
               :envlog/reported-avg-mpg 20.1,
               :id
               "envlogs/277076930205267"}
              {:envlog/logged-at #inst "2013-05-17T01:12:20.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 369,
               :envlog/odometer 21611.0,
               :id
               "envlogs/277076930203557"}
              {:envlog/logged-at #inst "2013-07-14T14:07:17.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 408,
               :envlog/reported-outside-temp 81.0,
               :envlog/odometer 23335.0,
               :envlog/reported-avg-mph 23.4,
               :envlog/reported-avg-mpg 20.1,
               :id
               "envlogs/277076930205264"}
              {:envlog/logged-at #inst "2015-01-17T23:44:57.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 45.0,
               :envlog/odometer 103763.0,
               :id
               "envlogs/277076930200927"}
              {:envlog/logged-at #inst "2013-01-27T12:41:22.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 457,
               :envlog/reported-outside-temp 34.0,
               :envlog/odometer 96617.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930205665"}
              {:envlog/logged-at #inst "2014-04-30T12:23:54.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 380,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 30374.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204705"}
              {:envlog/logged-at #inst "2015-03-25T11:21:03.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 55.0,
               :envlog/odometer 106671.0,
               :id
               "envlogs/277076930206905"}
              {:envlog/logged-at #inst "2014-04-30T12:23:54.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 35,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 30374.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204711"}
              {:envlog/logged-at #inst "2014-05-05T12:27:54.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 173,
               :envlog/reported-outside-temp 66.0,
               :envlog/odometer 30681.0,
               :envlog/reported-avg-mph 25.0,
               :envlog/reported-avg-mpg 21.9,
               :id
               "envlogs/277076930204764"}
              {:envlog/logged-at #inst "2011-11-16T12:56:12.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 49,
               :envlog/reported-outside-temp 62.0,
               :envlog/odometer 87817.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930205833"}
              {:envlog/logged-at #inst "2015-05-21T12:30:16.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 110,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 2315.0,
               :envlog/reported-avg-mpg 23.4,
               :id
               "envlogs/277076930207395"}
              {:envlog/logged-at #inst "2014-05-31T20:11:42.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930201980"}
              {:envlog/logged-at #inst "2014-05-05T12:27:54.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 524,
               :envlog/reported-outside-temp 66.0,
               :envlog/odometer 30681.0,
               :envlog/reported-avg-mph 25.0,
               :envlog/reported-avg-mpg 21.9,
               :id
               "envlogs/277076930204767"}
              {:envlog/logged-at #inst "2011-11-16T12:56:12.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 415,
               :envlog/reported-outside-temp 62.0,
               :envlog/odometer 87817.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930205830"}
              {:envlog/logged-at #inst "2013-01-01T12:57:02.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 60,
               :envlog/reported-outside-temp 49.0,
               :envlog/odometer 96289.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203680"}
              {:envlog/logged-at #inst "2013-08-01T20:47:24.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202517"}
              {:envlog/logged-at #inst "2015-05-21T12:30:16.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 110,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 2315.0,
               :envlog/reported-avg-mpg 23.4,
               :id
               "envlogs/277076930207401"}
              {:envlog/logged-at #inst "2012-12-11T12:55:48.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 65,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 95984.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.9,
               :id
               "envlogs/277076930203655"}
              {:envlog/logged-at #inst "2012-01-17T13:46:47.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 373,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 88465.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 26.1,
               :id
               "envlogs/277076930205855"}
              {:envlog/logged-at #inst "2015-05-21T12:30:16.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 561,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 2315.0,
               :envlog/reported-avg-mpg 23.4,
               :id
               "envlogs/277076930207413"}
              {:envlog/logged-at #inst "2014-07-01T20:10:56.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930201961"}
              {:envlog/logged-at #inst "2014-06-06T01:10:18.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 479,
               :envlog/reported-outside-temp 81.0,
               :envlog/odometer 31561.0,
               :envlog/reported-avg-mph 24.8,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204134"}
              {:envlog/logged-at #inst "2012-12-11T12:55:48.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 314,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 95984.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.9,
               :id
               "envlogs/277076930203652"}
              {:envlog/logged-at #inst "2014-06-06T01:10:18.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 49,
               :envlog/reported-outside-temp 81.0,
               :envlog/odometer 31561.0,
               :envlog/reported-avg-mph 24.8,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204137"}
              {:envlog/logged-at #inst "2014-04-21T12:25:40.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 57,
               :envlog/reported-outside-temp 64.0,
               :envlog/odometer 30083.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204739"}
              {:envlog/logged-at #inst "2015-05-21T12:30:16.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 110,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 2315.0,
               :envlog/reported-avg-mpg 23.4,
               :id
               "envlogs/277076930207419"}
              {:envlog/logged-at #inst "2013-07-04T13:37:26.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 45,
               :envlog/reported-outside-temp 82.0,
               :envlog/odometer 22984.0,
               :envlog/reported-avg-mph 22.4,
               :envlog/reported-avg-mpg 19.4,
               :id
               "envlogs/277076930205209"}
              {:envlog/logged-at #inst "2014-06-23T01:03:29.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930203030"}
              {:envlog/logged-at #inst "2013-02-04T12:58:37.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 412,
               :envlog/reported-outside-temp 32.0,
               :envlog/odometer 96890.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203705"}
              {:envlog/logged-at #inst "2013-07-04T13:37:26.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 413,
               :envlog/reported-outside-temp 82.0,
               :envlog/odometer 22984.0,
               :envlog/reported-avg-mph 22.4,
               :envlog/reported-avg-mpg 19.4,
               :id
               "envlogs/277076930205206"}
              {:envlog/logged-at #inst "2013-08-01T20:50:15.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202546"}
              {:envlog/logged-at #inst "2015-03-11T12:19:14.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 64.0,
               :envlog/odometer 105658.0,
               :id
               "envlogs/277076930206828"}
              {:envlog/logged-at #inst "2013-02-04T12:58:37.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 116,
               :envlog/reported-outside-temp 32.0,
               :envlog/odometer 96890.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203711"}
              {:envlog/logged-at #inst "2014-08-14T20:18:17.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202070"}
              {:envlog/logged-at #inst "2013-01-01T12:57:02.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 417,
               :envlog/reported-outside-temp 49.0,
               :envlog/odometer 96289.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.7,
               :id
               "envlogs/277076930203683"}
              {:envlog/logged-at #inst "2015-05-21T12:30:16.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 561,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 2315.0,
               :envlog/reported-avg-mpg 23.4,
               :id
               "envlogs/277076930207386"}
              {:envlog/logged-at #inst "2012-07-23T12:48:31.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 72,
               :envlog/reported-outside-temp 90.0,
               :envlog/odometer 93097.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205874"}
              {:envlog/logged-at #inst "2012-07-23T12:48:31.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 446,
               :envlog/reported-outside-temp 90.0,
               :envlog/odometer 93097.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205880"}
              {:envlog/logged-at #inst "2012-09-20T12:49:52.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 409,
               :envlog/reported-outside-temp 60.0,
               :envlog/odometer 94056.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205902"}
              {:envlog/logged-at #inst "2014-08-02T01:00:22.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202994"}
              {:envlog/logged-at #inst "2013-06-25T01:15:41.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 396,
               :envlog/reported-outside-temp 79.0,
               :envlog/odometer 22682.0,
               :envlog/reported-avg-mph 21.9,
               :envlog/reported-avg-mpg 19.1,
               :id
               "envlogs/277076930203736"}
              {:envlog/logged-at #inst "2014-09-28T20:16:31.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202047"}
              {:envlog/logged-at #inst "2013-12-05T14:34:13.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 59,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 26390.0,
               :envlog/reported-avg-mph 21.9,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930205171"}
              {:envlog/logged-at #inst "2013-07-07T20:51:23.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 473,
               :envlog/reported-outside-temp 88.0,
               :envlog/odometer 23636.0,
               :envlog/reported-avg-mph 23.8,
               :envlog/reported-avg-mpg 20.2,
               :id
               "envlogs/277076930202581"}
              {:envlog/logged-at #inst "2013-06-25T01:15:41.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 43,
               :envlog/reported-outside-temp 79.0,
               :envlog/odometer 22682.0,
               :envlog/reported-avg-mph 21.9,
               :envlog/reported-avg-mpg 19.1,
               :id
               "envlogs/277076930203739"}
              {:envlog/logged-at #inst "2015-03-13T09:35:30.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 47.0,
               :envlog/odometer 106034.0,
               :id
               "envlogs/277076930206857"}
              {:envlog/logged-at #inst "2013-07-07T20:51:23.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 73,
               :envlog/reported-outside-temp 88.0,
               :envlog/odometer 23636.0,
               :envlog/reported-avg-mph 23.8,
               :envlog/reported-avg-mpg 20.2,
               :id
               "envlogs/277076930202575"}
              {:envlog/logged-at #inst "2013-05-26T01:09:33.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 28,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 22129.0,
               :envlog/reported-avg-mph 21.7,
               :envlog/reported-avg-mpg 19.1,
               :id
               "envlogs/277076930203522"}
              {:envlog/logged-at #inst "2014-05-11T20:15:22.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202028"}
              {:envlog/logged-at #inst "2013-12-05T14:34:13.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 467,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 26390.0,
               :envlog/reported-avg-mph 21.9,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930205165"}
              {:envlog/logged-at #inst "2014-07-12T12:22:08.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 48,
               :envlog/reported-outside-temp 78.0,
               :envlog/odometer 32470.0,
               :envlog/reported-avg-mph 25.0,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204683"}
              {:envlog/logged-at #inst "2013-05-26T01:09:33.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 399,
               :envlog/reported-outside-temp 65.0,
               :envlog/odometer 22129.0,
               :envlog/reported-avg-mph 21.7,
               :envlog/reported-avg-mpg 19.1,
               :id
               "envlogs/277076930203525"}
              {:envlog/logged-at #inst "2012-09-20T12:49:52.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 109,
               :envlog/reported-outside-temp 60.0,
               :envlog/odometer 94056.0,
               :envlog/reported-avg-mph 26.0,
               :envlog/reported-avg-mpg 25.6,
               :id
               "envlogs/277076930205908"}
              {:envlog/logged-at #inst "2014-07-12T12:22:08.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 356,
               :envlog/reported-outside-temp 78.0,
               :envlog/odometer 32470.0,
               :envlog/reported-avg-mph 25.0,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204680"}
              {:envlog/logged-at #inst "2012-12-11T06:49:59.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 314,
               :envlog/reported-outside-temp 56.0,
               :envlog/odometer 95984.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 23.9,
               :id
               "envlogs/277076930201365"}
              {:envlog/logged-at #inst "2014-08-01T00:58:00.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202971"}
              {:envlog/logged-at #inst "2013-06-12T01:24:55.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 411,
               :envlog/reported-outside-temp 81.0,
               :envlog/odometer 22395.0,
               :envlog/reported-avg-mph 21.4,
               :envlog/reported-avg-mpg 18.8,
               :id
               "envlogs/277076930203774"}
              {:envlog/logged-at #inst "2014-09-25T20:36:48.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 78,
               :envlog/reported-outside-temp 63.0,
               :envlog/odometer 34169.0,
               :envlog/reported-avg-mph 24.6,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930202359"}
              {:envlog/logged-at #inst "2012-02-14T04:27:41.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 377,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 89153.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930204307"}
              {:envlog/logged-at #inst "2013-09-04T18:26:39.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 25,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 93.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 20.2,
               :id
               "envlogs/277076930201886"}
              {:envlog/logged-at #inst "2014-09-25T20:36:48.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 408,
               :envlog/reported-outside-temp 63.0,
               :envlog/odometer 34169.0,
               :envlog/reported-avg-mph 24.6,
               :envlog/reported-avg-mpg 21.1,
               :id
               "envlogs/277076930202365"}
              {:envlog/logged-at #inst "2012-02-14T04:27:41.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 53,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 89153.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930204310"}
              {:envlog/logged-at #inst "2014-06-16T18:24:23.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 369,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 31835.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930201861"}
              {:envlog/logged-at #inst "2013-03-08T01:51:24.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 86,
               :envlog/reported-outside-temp 35.0,
               :envlog/odometer 97532.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.5,
               :id
               "envlogs/277076930203439"}
              {:envlog/logged-at #inst "2013-03-08T01:51:24.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 391,
               :envlog/reported-outside-temp 35.0,
               :envlog/odometer 97532.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.5,
               :id
               "envlogs/277076930203433"}
              {:envlog/logged-at #inst "2014-06-16T18:24:23.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 61,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 31835.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930201858"}
              {:envlog/logged-at #inst "2014-02-03T13:35:45.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 392,
               :envlog/reported-outside-temp 59.0,
               :envlog/reported-avg-mph 24.7,
               :envlog/reported-avg-mpg 21.5,
               :id
               "envlogs/277076930204847"}
              {:envlog/logged-at #inst "2013-09-18T00:54:38.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 457,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 24213.0,
               :envlog/reported-avg-mph 23.9,
               :envlog/reported-avg-mpg 20.4,
               :id
               "envlogs/277076930202939"}
              {:envlog/logged-at #inst "2015-01-24T19:44:50.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 52.0,
               :envlog/odometer 103905.0,
               :id
               "envlogs/277076930201539"}
              {:envlog/logged-at #inst "2014-10-14T20:38:44.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 80,
               :envlog/reported-outside-temp 72.0,
               :envlog/odometer 34784.0,
               :envlog/reported-avg-mph 25.5,
               :envlog/reported-avg-mpg 22.1,
               :id
               "envlogs/277076930202390"}
              {:envlog/logged-at #inst "2014-10-14T20:38:44.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 388,
               :envlog/reported-outside-temp 72.0,
               :envlog/odometer 34784.0,
               :envlog/reported-avg-mph 25.5,
               :envlog/reported-avg-mpg 22.1,
               :id
               "envlogs/277076930202396"}
              {:envlog/logged-at #inst "2015-01-25T00:40:09.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 42.0,
               :envlog/odometer 103918.0,
               :id
               "envlogs/277076930201549"}
              {:envlog/logged-at #inst "2013-03-21T00:56:13.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 56,
               :envlog/reported-outside-temp 51.0,
               :envlog/odometer 97881.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.5,
               :id
               "envlogs/277076930203477"}
              {:envlog/logged-at #inst "2015-03-02T13:10:52.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 105370.0,
               :id
               "envlogs/277076930206788"}
              {:envlog/logged-at #inst "2015-05-20T09:19:46.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 121,
               :envlog/reported-outside-temp 88.0,
               :envlog/odometer 2305.0,
               :envlog/reported-avg-mpg 24.1,
               :id
               "envlogs/277076930207267"}
              {:envlog/logged-at #inst "2013-09-18T00:54:38.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 89,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 24213.0,
               :envlog/reported-avg-mph 23.9,
               :envlog/reported-avg-mpg 20.4,
               :id
               "envlogs/277076930202936"}
              {:envlog/logged-at #inst "2012-10-31T11:45:24.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 451,
               :envlog/reported-outside-temp 46.0,
               :envlog/odometer 94954.0,
               :envlog/reported-avg-mph 20.0,
               :envlog/reported-avg-mpg 22.3,
               :id
               "envlogs/277076930205718"}
              {:envlog/logged-at #inst "2014-03-27T12:39:34.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 446,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 29457.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204866"}
              {:envlog/logged-at #inst "2011-10-30T13:18:17.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 46,
               :envlog/reported-outside-temp 42.0,
               :envlog/odometer 86877.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 27.0,
               :id
               "envlogs/277076930206195"}
              {:envlog/logged-at #inst "2013-03-21T00:56:13.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 417,
               :envlog/reported-outside-temp 51.0,
               :envlog/odometer 97881.0,
               :envlog/reported-avg-mph 23.0,
               :envlog/reported-avg-mpg 23.5,
               :id
               "envlogs/277076930203471"}
              {:envlog/logged-at #inst "2011-10-30T13:18:17.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 417,
               :envlog/reported-outside-temp 42.0,
               :envlog/odometer 86877.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 27.0,
               :id
               "envlogs/277076930206198"}
              {:envlog/logged-at #inst "2014-03-27T12:39:34.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 57,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 29457.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204872"}
              {:envlog/logged-at #inst "2015-01-25T16:57:36.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 54.0,
               :envlog/odometer 103922.0,
               :id
               "envlogs/277076930201566"}
              {:envlog/logged-at #inst "2012-10-31T11:45:24.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 64,
               :envlog/reported-outside-temp 46.0,
               :envlog/odometer 94954.0,
               :envlog/reported-avg-mph 20.0,
               :envlog/reported-avg-mpg 22.3,
               :id
               "envlogs/277076930205721"}
              {:envlog/logged-at #inst "2013-06-12T01:24:55.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 38,
               :envlog/reported-outside-temp 81.0,
               :envlog/odometer 22395.0,
               :envlog/reported-avg-mph 21.4,
               :envlog/reported-avg-mpg 18.8,
               :id
               "envlogs/277076930203777"}
              {:envlog/logged-at #inst "2014-11-13T19:22:37.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 62.0,
               :envlog/odometer 102340.0,
               :id
               "envlogs/277076930201836"}
              {:envlog/logged-at #inst "2013-10-17T00:52:39.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 69,
               :envlog/reported-outside-temp 57.0,
               :envlog/odometer 25135.0,
               :envlog/reported-avg-mph 21.1,
               :envlog/reported-avg-mpg 20.8,
               :id
               "envlogs/277076930202911"}
              {:envlog/logged-at #inst "2014-10-22T20:40:13.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 53.0,
               :envlog/odometer 101684.0,
               :id
               "envlogs/277076930202418"}
              {:envlog/logged-at #inst "2013-10-17T00:52:39.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 373,
               :envlog/reported-outside-temp 57.0,
               :envlog/odometer 25135.0,
               :envlog/reported-avg-mph 21.1,
               :envlog/reported-avg-mpg 20.8,
               :id
               "envlogs/277076930202905"}
              {:envlog/logged-at #inst "2012-10-15T00:40:00.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 52,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 94633.0,
               :envlog/reported-avg-mph 21.0,
               :envlog/reported-avg-mpg 21.8,
               :id
               "envlogs/277076930203377"}
              {:envlog/logged-at #inst "2015-05-15T09:06:15.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 407,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 1855.0,
               :envlog/reported-avg-mpg 20.9,
               :id
               "envlogs/277076930207238"}
              {:envlog/logged-at #inst "2012-10-15T00:40:00.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 421,
               :envlog/reported-outside-temp 58.0,
               :envlog/odometer 94633.0,
               :envlog/reported-avg-mph 21.0,
               :envlog/reported-avg-mpg 21.8,
               :id
               "envlogs/277076930203383"}
              {:envlog/logged-at #inst "2013-12-23T12:48:34.000-00:00",
               :envlog/vehicle
               2,
               :envlog/dte 446,
               :envlog/reported-outside-temp 64.0,
               :envlog/odometer 26998.0,
               :envlog/reported-avg-mph 20.0,
               :envlog/reported-avg-mpg 21.3,
               :id
               "envlogs/277076930205746"}
              {:envlog/logged-at #inst "2013-12-23T12:48:34.000-00:00",
               :envlog/vehicle
               2,
               :envlog/dte 88,
               :envlog/reported-outside-temp 64.0,
               :envlog/odometer 26998.0,
               :envlog/reported-avg-mph 20.0,
               :envlog/reported-avg-mpg 21.3,
               :id
               "envlogs/277076930205749"}
              {:envlog/logged-at #inst "2013-08-12T20:08:21.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930201932"}
              {:envlog/logged-at #inst "2015-05-20T09:19:46.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 121,
               :envlog/reported-outside-temp 88.0,
               :envlog/odometer 2305.0,
               :envlog/reported-avg-mpg 24.1,
               :id
               "envlogs/277076930207251"}
              {:envlog/logged-at #inst "2013-10-26T00:50:27.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 384,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 25469.0,
               :envlog/reported-avg-mph 21.4,
               :envlog/reported-avg-mpg 21.0,
               :id
               "envlogs/277076930202883"}
              {:envlog/logged-at #inst "2014-04-05T12:32:54.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 417,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 29775.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204822"}
              {:envlog/logged-at #inst "2013-10-26T00:50:27.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 39,
               :envlog/reported-outside-temp 43.0,
               :envlog/odometer 25469.0,
               :envlog/reported-avg-mph 21.4,
               :envlog/reported-avg-mpg 21.0,
               :id
               "envlogs/277076930202880"}
              {:envlog/logged-at #inst "2015-05-12T12:29:06.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 506,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 1713.0,
               :envlog/reported-avg-mpg 20.4,
               :id
               "envlogs/277076930207216"}
              {:envlog/logged-at #inst "2015-05-12T12:29:06.000-00:00",
               :envlog/vehicle
               0,
               :envlog/dte 75,
               :envlog/reported-outside-temp 75.0,
               :envlog/odometer 1713.0,
               :envlog/reported-avg-mpg 20.4,
               :id
               "envlogs/277076930207213"}
              {:envlog/logged-at #inst "2013-05-17T00:49:11.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 349,
               :envlog/odometer 21864.0,
               :id
               "envlogs/277076930203411"}
              {:envlog/logged-at #inst "2014-04-05T12:32:54.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 34,
               :envlog/reported-outside-temp 71.0,
               :envlog/odometer 29775.0,
               :envlog/reported-avg-mph 24.9,
               :envlog/reported-avg-mpg 21.7,
               :id
               "envlogs/277076930204825"}
              {:envlog/logged-at #inst "2012-04-15T11:51:42.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 90,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 90464.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205774"}
              {:envlog/logged-at #inst "2013-09-04T18:26:39.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 422,
               :envlog/reported-outside-temp 77.0,
               :envlog/odometer 93.0,
               :envlog/reported-avg-mph 24.0,
               :envlog/reported-avg-mpg 20.2,
               :id
               "envlogs/277076930201889"}
              {:envlog/logged-at #inst "2012-04-15T11:51:42.000-00:00",
               :envlog/vehicle
               4,
               :envlog/dte 424,
               :envlog/reported-outside-temp 70.0,
               :envlog/odometer 90464.0,
               :envlog/reported-avg-mph 27.0,
               :envlog/reported-avg-mpg 25.8,
               :id
               "envlogs/277076930205777"}
              {:envlog/logged-at #inst "2013-05-17T00:49:11.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 71,
               :envlog/odometer 21864.0,
               :id
               "envlogs/277076930203408"}
              {:envlog/logged-at #inst "2015-01-21T13:34:06.000-00:00",
               :envlog/vehicle
               2,
               :envlog/reported-outside-temp 64.0,
               :envlog/odometer 103819.0,
               :id
               "envlogs/277076930201496"}
              {:envlog/logged-at #inst "2013-09-29T00:48:49.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 15,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 24560.0,
               :envlog/reported-avg-mph 20.3,
               :envlog/reported-avg-mpg 20.6,
               :id
               "envlogs/277076930202855"}
              {:envlog/logged-at #inst "2013-09-29T00:48:49.000-00:00",
               :envlog/vehicle
               3,
               :envlog/dte 408,
               :envlog/reported-outside-temp 73.0,
               :envlog/odometer 24560.0,
               :envlog/reported-avg-mph 20.3,
               :envlog/reported-avg-mpg 20.6,
               :id
               "envlogs/277076930202849"}
              {:envlog/logged-at #inst "2013-08-10T20:41:07.000-00:00",
               :envlog/vehicle
               2,
               :id
               "envlogs/277076930202441"}])

(def envlog-entid-map {"envlogs/277076930207525" 84,
                       "envlogs/277076930203383" 218,
                       "envlogs/277076930202121" 81,
                       "envlogs/277076930203172" 66,
                       "envlogs/277076930203683" 161,
                       "envlogs/277076930207425" 120,
                       "envlogs/277076930204711" 134,
                       "envlogs/277076930203736" 167,
                       "envlogs/277076930205461" 46,
                       "envlogs/277076930205746" 219,
                       "envlogs/277076930207401" 143,
                       "envlogs/277076930205665" 131,
                       "envlogs/277076930207267" 201,
                       "envlogs/277076930203259" 42,
                       "envlogs/277076930207251" 222,
                       "envlogs/277076930205902" 165,
                       "envlogs/277076930202613" 49,
                       "envlogs/277076930203576" 117,
                       "envlogs/277076930202971" 182,
                       "envlogs/277076930206195" 205,
                       "envlogs/277076930204705" 132,
                       "envlogs/277076930204847" 193,
                       "envlogs/277076930203525" 178,
                       "envlogs/277076930202181" 99,
                       "envlogs/277076930202780" 45,
                       "envlogs/277076930205880" 164,
                       "envlogs/277076930203652" 149,
                       "envlogs/277076930202418" 214,
                       "envlogs/277076930203582" 124,
                       "envlogs/277076930206508" 114,
                       "envlogs/277076930207528" 86,
                       "envlogs/277076930204994" 19,
                       "envlogs/277076930201932" 221,
                       "envlogs/277076930206042" 104,
                       "envlogs/277076930202070" 160,
                       "envlogs/277076930207238" 217,
                       "envlogs/277076930205561" 111,
                       "envlogs/277076930205143" 83,
                       "envlogs/277076930202581" 170,
                       "envlogs/277076930203411" 228,
                       "envlogs/277076930201781" 58,
                       "envlogs/277076930202441" 237,
                       "envlogs/277076930203377" 216,
                       "envlogs/277076930207419" 152,
                       "envlogs/277076930203144" 106,
                       "envlogs/277076930202332" 53,
                       "envlogs/277076930207395" 137,
                       "envlogs/277076930207213" 227,
                       "envlogs/277076930205721" 210,
                       "envlogs/277076930202905" 215,
                       "envlogs/277076930202704" 65,
                       "envlogs/277076930204310" 188,
                       "envlogs/277076930202390" 196,
                       "envlogs/277076930206098" 22,
                       "envlogs/277076930206524" 107,
                       "envlogs/277076930205637" 61,
                       "envlogs/277076930202701" 62,
                       "envlogs/277076930203113" 93,
                       "envlogs/277076930203705" 155,
                       "envlogs/277076930205718" 203,
                       "envlogs/277076930201886" 186,
                       "envlogs/277076930202802" 24,
                       "envlogs/277076930203325" 14,
                       "envlogs/277076930203888" 8,
                       "envlogs/277076930202883" 223,
                       "envlogs/277076930205106" 70,
                       "envlogs/277076930206154" 51,
                       "envlogs/277076930202206" 112,
                       "envlogs/277076930203284" 52,
                       "envlogs/277076930205992" 97,
                       "envlogs/277076930207386" 162,
                       "envlogs/277076930205662" 74,
                       "envlogs/277076930204739" 151,
                       "envlogs/277076930202855" 235,
                       "envlogs/277076930201621" 69,
                       "envlogs/277076930202575" 173,
                       "envlogs/277076930202745" 59,
                       "envlogs/277076930204134" 148,
                       "envlogs/277076930207155" 0,
                       "envlogs/277076930202488" 126,
                       "envlogs/277076930205464" 43,
                       "envlogs/277076930204764" 135,
                       "envlogs/277076930203138" 102,
                       "envlogs/277076930201961" 147,
                       "envlogs/277076930203477" 199,
                       "envlogs/277076930202660" 80,
                       "envlogs/277076930201800" 35,
                       "envlogs/277076930204928" 37,
                       "envlogs/277076930202241" 17,
                       "envlogs/277076930202209" 7,
                       "envlogs/277076930204825" 229,
                       "envlogs/277076930204960" 50,
                       "envlogs/277076930207105" 56,
                       "envlogs/277076930207581" 60,
                       "envlogs/277076930201722" 27,
                       "envlogs/277076930201566" 209,
                       "envlogs/277076930203774" 183,
                       "envlogs/277076930201539" 195,
                       "envlogs/277076930205053" 98,
                       "envlogs/277076930203557" 128,
                       "envlogs/277076930203522" 174,
                       "envlogs/277076930202178" 101,
                       "envlogs/277076930202880" 225,
                       "envlogs/277076930204866" 204,
                       "envlogs/277076930202359" 184,
                       "envlogs/277076930204900" 36,
                       "envlogs/277076930205749" 220,
                       "envlogs/277076930203030" 154,
                       "envlogs/277076930202365" 187,
                       "envlogs/277076930200693" 100,
                       "envlogs/277076930205016" 28,
                       "envlogs/277076930207040" 64,
                       "envlogs/277076930204872" 208,
                       "envlogs/277076930202150" 92,
                       "envlogs/277076930205936" 75,
                       "envlogs/277076930206828" 158,
                       "envlogs/277076930204648" 103,
                       "envlogs/277076930207216" 226,
                       "envlogs/277076930203950" 33,
                       "envlogs/277076930204307" 185,
                       "envlogs/277076930202297" 41,
                       "envlogs/277076930206857" 172,
                       "envlogs/277076930204966" 9,
                       "envlogs/277076930206988" 110,
                       "envlogs/277076930201889" 231,
                       "envlogs/277076930204736" 119,
                       "envlogs/277076930201836" 212,
                       "envlogs/277076930205267" 127,
                       "envlogs/277076930204137" 150,
                       "envlogs/277076930203322" 15,
                       "envlogs/277076930205631" 85,
                       "envlogs/277076930205986" 96,
                       "envlogs/277076930202275" 32,
                       "envlogs/277076930201709" 16,
                       "envlogs/277076930203433" 191,
                       "envlogs/277076930205693" 125,
                       "envlogs/277076930203091" 116,
                       "envlogs/277076930203680" 141,
                       "envlogs/277076930203739" 171,
                       "envlogs/277076930204991" 12,
                       "envlogs/277076930206473" 73,
                       "envlogs/277076930206020" 108,
                       "envlogs/277076930202911" 213,
                       "envlogs/277076930206073" 1,
                       "envlogs/277076930205687" 115,
                       "envlogs/277076930207547" 79,
                       "envlogs/277076930203956" 38,
                       "envlogs/277076930202247" 21,
                       "envlogs/277076930203777" 211,
                       "envlogs/277076930201549" 198,
                       "envlogs/277076930203214" 78,
                       "envlogs/277076930206788" 200,
                       "envlogs/277076930207099" 34,
                       "envlogs/277076930202824" 2,
                       "envlogs/277076930201365" 181,
                       "envlogs/277076930205774" 230,
                       "envlogs/277076930205908" 179,
                       "envlogs/277076930202546" 157,
                       "envlogs/277076930205961" 89,
                       "envlogs/277076930207413" 146,
                       "envlogs/277076930206905" 133,
                       "envlogs/277076930202777" 44,
                       "envlogs/277076930204822" 224,
                       "envlogs/277076930203711" 159,
                       "envlogs/277076930204680" 180,
                       "envlogs/277076930207591" 54,
                       "envlogs/277076930205805" 118,
                       "envlogs/277076930203116" 90,
                       "envlogs/277076930202827" 6,
                       "envlogs/277076930202269" 25,
                       "envlogs/277076930206969" 94,
                       "envlogs/277076930203885" 4,
                       "envlogs/277076930201858" 192,
                       "envlogs/277076930207550" 77,
                       "envlogs/277076930206129" 29,
                       "envlogs/277076930203169" 68,
                       "envlogs/277076930202047" 168,
                       "envlogs/277076930205264" 129,
                       "envlogs/277076930204623" 91,
                       "envlogs/277076930205512" 5,
                       "envlogs/277076930206048" 105,
                       "envlogs/277076930203287" 55,
                       "envlogs/277076930205802" 122,
                       "envlogs/277076930201496" 234,
                       "envlogs/277076930201732" 10,
                       "envlogs/277076930205564" 113,
                       "envlogs/277076930204922" 39,
                       "envlogs/277076930204683" 177,
                       "envlogs/277076930202028" 175,
                       "envlogs/277076930205874" 163,
                       "envlogs/277076930202936" 202,
                       "envlogs/277076930205171" 169,
                       "envlogs/277076930202396" 197,
                       "envlogs/277076930201980" 138,
                       "envlogs/277076930207171" 23,
                       "envlogs/277076930205930" 76,
                       "envlogs/277076930202939" 194,
                       "envlogs/277076930202679" 87,
                       "envlogs/277076930205103" 67,
                       "envlogs/277076930202726" 47,
                       "envlogs/277076930202098" 71,
                       "envlogs/277076930207037" 63,
                       "envlogs/277076930203655" 144,
                       "envlogs/277076930206198" 207,
                       "envlogs/277076930205209" 153,
                       "envlogs/277076930205506" 11,
                       "envlogs/277076930205964" 88,
                       "envlogs/277076930202517" 142,
                       "envlogs/277076930206886" 121,
                       "envlogs/277076930201861" 189,
                       "envlogs/277076930207194" 20,
                       "envlogs/277076930205855" 145,
                       "envlogs/277076930205137" 82,
                       "envlogs/277076930205022" 26,
                       "envlogs/277076930202632" 95,
                       "envlogs/277076930202326" 57,
                       "envlogs/277076930200927" 130,
                       "envlogs/277076930202849" 236,
                       "envlogs/277076930205830" 140,
                       "envlogs/277076930203256" 40,
                       "envlogs/277076930203198" 72,
                       "envlogs/277076930203408" 233,
                       "envlogs/277076930203439" 190,
                       "envlogs/277076930206104" 18,
                       "envlogs/277076930205165" 176,
                       "envlogs/277076930202009" 123,
                       "envlogs/277076930206132" 30,
                       "envlogs/277076930204767" 139,
                       "envlogs/277076930206173" 48,
                       "envlogs/277076930206076" 3,
                       "envlogs/277076930201128" 13,
                       "envlogs/277076930204897" 31,
                       "envlogs/277076930205777" 232,
                       "envlogs/277076930203471" 206,
                       "envlogs/277076930205206" 156,
                       "envlogs/277076930202994" 166,
                       "envlogs/277076930205833" 136,
                       "envlogs/277076930206017" 109})

(defn migrate-save-user
  []
  (usercore/save-new-user db-spec-dev
                          0
                          {:user/name "Paul Evans"
                           :user/email "evansp2@gmail.com"
                           :user/username "evanspa"
                           :user/password "aard8ark"}))

(defn migrate-vehicles
  []
  (doseq [vehicle vehicles]
    (core/save-new-vehicle db-spec-dev
                           user-id
                           (:id vehicle)
                           vehicle)))

(defn migrate-fuelstations
  []
  (doseq [fs fuelstations]
    (let [str-id (:id fs)
          new-fs-id (get fuelstation-entid-map str-id)]
      (core/save-new-fuelstation db-spec-dev
                                 user-id
                                 new-fs-id
                                 fs))))

(defn migrate-fplogs
  []
  (doseq [fplog fplogs]
    (let [str-id (:id fplog)
          new-fplog-id (get fplog-entids-map str-id)
          vehicle-id (:fplog/vehicle fplog)
          fs-id (get fuelstation-entid-map (:fplog/fuelstation fplog))]
      (core/save-new-fplog db-spec-dev
                           user-id
                           vehicle-id
                           fs-id
                           new-fplog-id
                           fplog))))

(defn migrate-envlogs
  []
  (doseq [envlog envlogs]
    (let [str-id (:id envlog)
          new-envlog-id (get envlog-entid-map str-id)
          vehicle-id (:envlog/vehicle envlog)]
      (core/save-new-envlog db-spec-dev
                            user-id
                            vehicle-id
                            new-envlog-id
                            envlog))))
