(ns pe-fp-core.validation
  (:require [pe-core-utils.core :as ucore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Purchase log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sfplog-any-issues                 (bit-shift-left 1 0))
(def sfplog-purchased-at-not-provided  (bit-shift-left 1 1))
(def sfplog-num-gallons-not-provided   (bit-shift-left 1 2))
(def sfplog-octane-not-provided        (bit-shift-left 1 3))
(def sfplog-gallon-price-not-provided  (bit-shift-left 1 4))
(def sfplog-gallon-price-negative      (bit-shift-left 1 5))
(def sfplog-num-gallons-negative       (bit-shift-left 1 6))
(def sfplog-octane-negative            (bit-shift-left 1 7))
(def sfplog-user-does-not-exist        (bit-shift-left 1 8))
(def sfplog-vehicle-does-not-exist     (bit-shift-left 1 9))
(def sfplog-fuelstation-does-not-exist (bit-shift-left 1 10))
(def sfplog-odometer-not-provided      (bit-shift-left 1 11))
(def sfplog-odometer-negative          (bit-shift-left 1 12))
(def sfplog-odometer-not-numeric       (bit-shift-left 1 13))
(def sfplog-octane-not-numeric         (bit-shift-left 1 14))
(def sfplog-num-gallons-not-numeric    (bit-shift-left 1 15))
(def sfplog-gallon-price-not-numeric   (bit-shift-left 1 16))

(defn- fplog-common-validations
  [mask {odometer :fplog/odometer
         num-gallons :fplog/num-gallons
         octane :fplog/octane
         gallon-price :fplog/gallon-price
         purchased-at :fplog/purchased-at
         :as fplog}]
  (let [contains-odometer (contains? fplog :fplog/odometer)
        contains-num-gallons (contains? fplog :fplog/num-gallons)
        contains-octane (contains? fplog :fplog/octane)
        contains-gallon-price (contains? fplog :fplog/gallon-price)
        contains-purchased-at (contains? fplog :fplog/purchased-at)]
    (-> mask
        (ucore/add-condition #(and contains-odometer (not (number? odometer)))
                             sfplog-odometer-not-numeric
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-odometer (number? odometer) (< odometer 0))
                             sfplog-odometer-negative
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-num-gallons (not (number? num-gallons)))
                             sfplog-num-gallons-not-numeric
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-num-gallons (number? num-gallons) (< num-gallons 0))
                             sfplog-num-gallons-negative
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-octane (not (number? octane)))
                             sfplog-octane-not-numeric
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-octane (number? octane) (< octane 0))
                             sfplog-octane-negative
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-gallon-price (not (number? gallon-price)))
                             sfplog-gallon-price-not-numeric
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-gallon-price (number? gallon-price) (< gallon-price 0))
                             sfplog-gallon-price-negative
                             sfplog-any-issues)
        (ucore/add-condition #(and contains-purchased-at (nil? purchased-at))
                             sfplog-purchased-at-not-provided
                             sfplog-any-issues))))

(defn save-fplog-validation-mask
  [fplog]
  (fplog-common-validations 0 fplog))

(defn create-fplog-validation-mask
  [fplog]
  (letfn [(reqd [mask field err-bit]
            (ucore/add-condition mask
                                 #(and (not (contains? fplog field)))
                                 err-bit
                                 sfplog-any-issues))]
    (-> 0
        (reqd :fplog/purchased-at sfplog-purchased-at-not-provided)
        (reqd :fplog/num-gallons  sfplog-num-gallons-not-provided)
        (reqd :fplog/octane       sfplog-octane-not-provided)
        (reqd :fplog/gallon-price sfplog-gallon-price-not-provided)
        (reqd :fplog/odometer     sfplog-odometer-not-provided)
        (fplog-common-validations fplog))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def senvlog-any-issues               (bit-shift-left 1 0))
(def senvlog-logged-at-not-provided   (bit-shift-left 1 1))
(def senvlog-odometer-not-provided    (bit-shift-left 1 2))
(def senvlog-odometer-negative        (bit-shift-left 1 3))
(def senvlog-user-does-not-exist      (bit-shift-left 1 4))
(def senvlog-vehicle-does-not-exist   (bit-shift-left 1 5))
(def senvlog-odometer-not-numeric     (bit-shift-left 1 6))
(def senvlog-avg-mpg-not-numeric      (bit-shift-left 1 7))
(def senvlog-avg-mpg-negative         (bit-shift-left 1 8))
(def senvlog-avg-mph-not-numeric      (bit-shift-left 1 9))
(def senvlog-avg-mph-negative         (bit-shift-left 1 10))
(def senvlog-outside-temp-not-numeric (bit-shift-left 1 11))

(defn- envlog-common-validations
  [mask {mpg :envlog/reported-avg-mpg
         mph :envlog/reported-avg-mph
         temp :envlog/reported-outside-temp
         odometer :envlog/odometer
         logged-at :envlog/logged-at
         :as envlog}]
  (let [contains-mpg (contains? envlog :envlog/reported-avg-mpg)
        contains-mph (contains? envlog :envlog/reported-avg-mph)
        contains-temp (contains? envlog :envlog/reported-outside-temp)
        contains-odometer (contains? envlog :envlog/odometer)
        contains-logged-at (contains? envlog :envlog/logged-at)]
    (-> mask
        (ucore/add-condition #(and contains-logged-at (nil? logged-at))
                             senvlog-logged-at-not-provided
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-odometer (not (number? odometer)))
                             senvlog-odometer-not-numeric
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-odometer (number? odometer) (< odometer 0))
                             senvlog-odometer-negative
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-mpg (not (number? mpg)))
                             senvlog-avg-mpg-not-numeric
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-mpg (number? mpg) (< mpg 0))
                             senvlog-avg-mpg-negative
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-mph (not (number? mph)))
                             senvlog-avg-mph-not-numeric
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-mph (number? mph) (< mph 0))
                             senvlog-avg-mph-negative
                             senvlog-any-issues)
        (ucore/add-condition #(and contains-temp (not (number? temp)))
                             senvlog-outside-temp-not-numeric
                             senvlog-any-issues))))

(defn save-envlog-validation-mask
  [envlog]
  (envlog-common-validations 0 envlog))

(defn create-envlog-validation-mask
  [envlog]
  (letfn [(reqd [mask field err-bit]
            (ucore/add-condition mask
                                 #(and (not (contains? envlog field)))
                                 err-bit
                                 senvlog-any-issues))]
    (-> 0
        (reqd :envlog/logged-at senvlog-logged-at-not-provided)
        (reqd :envlog/odometer  senvlog-odometer-not-provided)
        (envlog-common-validations envlog))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Station-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sfs-any-issues             (bit-shift-left 1 0))
(def sfs-name-not-provided      (bit-shift-left 1 1))
(def sfs-user-does-not-exist    (bit-shift-left 1 2))
(def sfs-name-cannot-be-purplex (bit-shift-left 1 3))
(def sfs-latitude-not-numeric   (bit-shift-left 1 4))
(def sfs-longitude-not-numeric  (bit-shift-left 1 5))

(defn- fuelstation-common-validations
  [mask {name :fpfuelstation/name :as fuelstation}]
  (let [contains-name (contains? fuelstation :fpfuelstation/name)]
    (-> mask
        (ucore/add-condition #(and contains-name (empty? name))
                             sfs-name-not-provided
                             sfs-any-issues)
        (ucore/add-condition #(and contains-name (.contains name "purplex"))
                             sfs-name-cannot-be-purplex
                             sfs-any-issues)
        (ucore/add-condition #(and (contains? fuelstation :fpfuelstation/latitude)
                                   (not (number? (:fpfuelstation/latitude fuelstation))))
                             sfs-latitude-not-numeric
                             sfs-any-issues)
        (ucore/add-condition #(and (contains? fuelstation :fpfuelstation/longitude)
                                   (not (number? (:fpfuelstation/longitude fuelstation))))
                             sfs-longitude-not-numeric
                             sfs-any-issues))))

(defn save-fuelstation-validation-mask
  [fuelstation]
  (fuelstation-common-validations 0 fuelstation))

(defn create-fuelstation-validation-mask
  [fuelstation]
  (-> 0
      (ucore/add-condition #(not (contains? fuelstation :fpfuelstation/name))
                           sfs-name-not-provided
                           sfs-any-issues)
      (fuelstation-common-validations fuelstation)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sv-any-issues                   (bit-shift-left 1 0))
(def sv-name-not-provided            (bit-shift-left 1 1)) ; for POST/PUT
(def sv-vehicle-already-exists       (bit-shift-left 1 2)) ; for POST
(def sv-vehicle-cannot-be-purple     (bit-shift-left 1 3))
(def sv-vehicle-cannot-be-red        (bit-shift-left 1 4))
(def sv-user-does-not-exist          (bit-shift-left 1 5))
(def sv-cannot-be-both-diesel-octane (bit-shift-left 1 6))

(defn- vehicle-common-validations
  [mask {name :fpvehicle/name :as vehicle}]
  (let [contains-name (contains? vehicle :fpvehicle/name)]
    (-> mask
        (ucore/add-condition #(and contains-name (empty? name))
                             sv-name-not-provided
                             sv-any-issues)
        (ucore/add-condition #(and contains-name (.contains name "purple"))
                             sv-vehicle-cannot-be-purple
                             sv-any-issues)
        (ucore/add-condition #(and contains-name (.contains name "red"))
                             sv-vehicle-cannot-be-red
                             sv-any-issues)
        (ucore/add-condition #(and (and (contains? vehicle :fpvehicle/default-octane)
                                        (not (nil? (:fpvehicle/default-octane vehicle))))
                                   (and (contains? vehicle :fpvehicle/is-diesel)
                                        (:fpvehicle/is-diesel vehicle)))
                             sv-cannot-be-both-diesel-octane
                             sv-any-issues))))

(defn save-vehicle-validation-mask
  [vehicle]
  (vehicle-common-validations 0 vehicle))

(defn create-vehicle-validation-mask
  [{name :fpvehicle/name :as vehicle}]
  (-> 0
      (ucore/add-condition #(not (contains? vehicle :fpvehicle/name))
                           sv-name-not-provided
                           sv-any-issues)
      (vehicle-common-validations vehicle)))
