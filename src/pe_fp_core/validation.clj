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

(defn save-fplog-validation-mask [fplog] 0)

(defn create-fplog-validation-mask
  [{purchase-date :fplog/purchased-at
    num-gallons   :fplog/num-gallons
    octane        :fplog/octane
    gallon-price  :fplog/gallon-price}]
  (-> 0
      (ucore/add-condition #(nil? num-gallons)
                           sfplog-num-gallons-not-provided
                           sfplog-any-issues)
      (ucore/add-condition #(nil? octane)
                           sfplog-octane-not-provided
                           sfplog-any-issues)
      (ucore/add-condition #(nil? gallon-price)
                           sfplog-gallon-price-not-provided
                           sfplog-any-issues)
      (ucore/add-condition #(nil? purchase-date)
                           sfplog-purchased-at-not-provided
                           sfplog-any-issues)
      (ucore/add-condition #(and (not (nil? num-gallons))
                                 (< num-gallons 0))
                           sfplog-num-gallons-negative
                           sfplog-any-issues)
      (ucore/add-condition #(and (not (nil? octane))
                                 (< octane 0))
                           sfplog-octane-negative
                           sfplog-any-issues)
      (ucore/add-condition #(and (not (nil? gallon-price))
                                 (< gallon-price 0))
                           sfplog-gallon-price-negative
                           sfplog-any-issues)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment log-related validation definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def senvlog-any-issues                (bit-shift-left 1 0))
(def senvlog-logged-at-not-provided    (bit-shift-left 1 1))
(def senvlog-odometer-not-provided     (bit-shift-left 1 2))
(def senvlog-outside-temp-not-provided (bit-shift-left 1 3))
(def senvlog-odometer-negative         (bit-shift-left 1 4))

(defn save-envlog-validation-mask [envlog] 0)

(defn create-envlog-validation-mask
  [{logged-at :envlog/logged-at
    odometer :envlog/odometer
    outside-temp :envlog/reported-outside-temp}]
  (-> 0
      (ucore/add-condition #(nil? odometer)
                           senvlog-odometer-not-provided
                           senvlog-any-issues)
      (ucore/add-condition #(nil? outside-temp)
                           senvlog-outside-temp-not-provided
                           senvlog-any-issues)
      (ucore/add-condition #(nil? logged-at)
                           senvlog-logged-at-not-provided
                           senvlog-any-issues)
      (ucore/add-condition #(and (not (nil? odometer))
                                 (< odometer 0))
                           senvlog-odometer-negative
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
(def sv-any-issues               (bit-shift-left 1 0))
(def sv-name-not-provided        (bit-shift-left 1 1)) ; for POST/PUT
(def sv-vehicle-already-exists   (bit-shift-left 1 2)) ; for POST
(def sv-vehicle-cannot-be-purple (bit-shift-left 1 3))
(def sv-vehicle-cannot-be-red    (bit-shift-left 1 4))

(defn save-vehicle-validation-mask
  [{name :fpvehicle/name
    :as vehicle}]
  (-> 0
      (ucore/add-condition #(and (not (nil? name))
                                 (.contains name "purple"))
                           sv-vehicle-cannot-be-purple
                           sv-any-issues)
      (ucore/add-condition #(and (not (nil? name))
                                 (.contains name "red"))
                           sv-vehicle-cannot-be-red
                           sv-any-issues)
      (ucore/add-condition #(and (contains? vehicle :fpvehicle/name)
                                 (empty? name))
                           sv-name-not-provided
                           sv-any-issues)))

(defn create-vehicle-validation-mask
  [{name :fpvehicle/name}]
  (-> 0
      (ucore/add-condition #(and (not (nil? name))
                                 (.contains name "purple"))
                           sv-vehicle-cannot-be-purple
                           sv-any-issues)
      (ucore/add-condition #(and (not (nil? name))
                                 (.contains name "red"))
                           sv-vehicle-cannot-be-red
                           sv-any-issues)
      (ucore/add-condition #(empty? name)
                           sv-name-not-provided
                           sv-any-issues)))
