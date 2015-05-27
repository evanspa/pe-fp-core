(ns pe-fp-core.validation
  (:require [pe-core-utils.core :as ucore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Purchase log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sfplog-any-issues                 (bit-shift-left 1 0))
(def sfplog-purchase-date-not-provided (bit-shift-left 1 1))
(def sfplog-num-gallons-not-provided   (bit-shift-left 1 2))
(def sfplog-octane-not-provided        (bit-shift-left 1 3))
(def sfplog-gallon-price-not-provided  (bit-shift-left 1 4))

(defn save-fuelpurchaselog-validation-mask [fplog] 0)

(defn create-fuelpurchaselog-validation-mask
  [{purchase-date :fpfuelpurchaselog/purchase-date
    num-gallons :fpfuelpurchaselog/num-gallons
    octane :fpfuelpurchaselog/octane
    gallon-price :fpfuelpurchaselog/gallon-price}]
  (-> 0
      (ucore/add-condition #(nil? purchase-date)
                           sfplog-purchase-date-not-provided
                           sfplog-any-issues)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def senvlog-any-issues            (bit-shift-left 1 0))
(def senvlog-date-not-provided     (bit-shift-left 1 1))
(def senvlog-odometer-not-provided (bit-shift-left 1 2))

(defn save-environmentlog-validation-mask [envlog] 0)

(defn create-environmentlog-validation-mask
  [{log-date :fpenvironmentlog/log-date
    odometer :fpenvironmentlog/odometer}]
  (-> 0
      (ucore/add-condition #(nil? log-date)
                           senvlog-date-not-provided
                           senvlog-any-issues)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Station-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sfs-any-issues                 (bit-shift-left 1 0))
(def sfs-name-not-provided          (bit-shift-left 1 1))

(defn save-fuelstation-validation-mask
  [{name :fpfuelstation/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           sfs-name-not-provided
                           sfs-any-issues)))

(defn create-fuelstation-validation-mask
  [{name :fpfuelstation/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           sfs-name-not-provided
                           sfs-any-issues)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sv-any-issues             (bit-shift-left 1 0))
(def sv-name-not-provided      (bit-shift-left 1 1)) ; for POST/PUT
(def sv-vehicle-already-exists (bit-shift-left 1 2)) ; for POST

(defn save-vehicle-validation-mask
  [{name :fpvehicle/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           sv-name-not-provided
                           sv-any-issues)))

(defn create-vehicle-validation-mask
  [{name :fpvehicle/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           sv-name-not-provided
                           sv-any-issues)))
