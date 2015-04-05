(ns pe-fp-core.validation
  (:require [pe-core-utils.core :as ucore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Purchase log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def savefuelpurchaselog-any-issues                 (bit-shift-left 1 0))
(def savefuelpurchaselog-purchase-date-not-provided (bit-shift-left 1 1))
(def savefuelpurchaselog-num-gallons-not-provided   (bit-shift-left 1 2))
(def savefuelpurchaselog-octane-not-provided        (bit-shift-left 1 3))
(def savefuelpurchaselog-gallon-price-not-provided  (bit-shift-left 1 4))

(defn save-fuelpurchaselog-validation-mask [fplog] 0)

(defn create-fuelpurchaselog-validation-mask
  [{purchase-date :fpfuelpurchaselog/purchase-date
    num-gallons :fpfuelpurchaselog/num-gallons
    octane :fpfuelpurchaselog/octane
    gallon-price :fpfuelpurchaselog/gallon-price}]
  (-> 0
      (ucore/add-condition #(nil? purchase-date)
                           savefuelpurchaselog-purchase-date-not-provided
                           savefuelpurchaselog-any-issues)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def saveenvironmentlog-any-issues            (bit-shift-left 1 0))
(def saveenvironmentlog-date-not-provided     (bit-shift-left 1 1))
(def saveenvironmentlog-odometer-not-provided (bit-shift-left 1 2))

(defn save-environmentlog-validation-mask [envlog] 0)

(defn create-environmentlog-validation-mask
  [{log-date :fpenvironmentlog/log-date
    odometer :fpenvironmentlog/odometer}]
  (-> 0
      (ucore/add-condition #(nil? log-date)
                           saveenvironmentlog-date-not-provided
                           saveenvironmentlog-any-issues)
      #_(ucore/add-condition #(nil? odometer)
                             saveenvironmentlog-odometer-not-provided
                             saveenvironmentlog-any-issues)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fuel Station-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def savefuelstation-any-issues                 (bit-shift-left 1 0))
(def savefuelstation-name-not-provided          (bit-shift-left 1 1))

(defn save-fuelstation-validation-mask
  [{name :fpfuelstation/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           savefuelstation-name-not-provided
                           savefuelstation-any-issues)))

(defn create-fuelstation-validation-mask
  [{name :fpfuelstation/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           savefuelstation-name-not-provided
                           savefuelstation-any-issues)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vehicle-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def savevehicle-any-issues             (bit-shift-left 1 0))
(def savevehicle-name-not-provided      (bit-shift-left 1 1)) ; for POST/PUT
(def savevehicle-vehicle-already-exists (bit-shift-left 1 2)) ; for POST

(defn save-vehicle-validation-mask
  [{name :fpvehicle/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           savevehicle-name-not-provided
                           savevehicle-any-issues)))

(defn create-vehicle-validation-mask
  [{name :fpvehicle/name}]
  (-> 0
      (ucore/add-condition #(empty? name)
                           savevehicle-name-not-provided
                           savevehicle-any-issues)))
