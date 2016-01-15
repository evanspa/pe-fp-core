(ns pe-fp-core.data-loads
  (:require [pe-fp-core.core :as core]
            [pe-fp-core.validation :as val]
            [clojure.tools.logging :as log]
            [pe-jdbc-utils.core :as jcore]
            [clojure.java.jdbc :as j]
            [pe-user-core.core :as usercore]
            [pe-fp-core.ddl :as fpddl]
            [pe-core-utils.core :as ucore]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.pprint :refer (pprint)]))

(defn v6-data-loads
  [db-spec]
  (letfn [(insert-fstype [type-id type-name]
            (j/insert! db-spec
                       fpddl/tbl-fuelstation-type
                       {:type_id type-id
                        :type_name type-name
                        :type_sort_order type-id}))]
    (insert-fstype 0  "Other")
    (insert-fstype 1  "Exxon")
    (insert-fstype 2  "Marathon")
    (insert-fstype 3  "Shell")
    (insert-fstype 4  "BP")
    (insert-fstype 5  "7-Eleven")
    (insert-fstype 6  "Chevron")
    (insert-fstype 7  "Hess")
    (insert-fstype 8  "Sunoco")
    (insert-fstype 9  "CITGO")
    (insert-fstype 10 "GULF")
    (insert-fstype 11 "Sam's Club")
    (insert-fstype 12 "BJ's")
    (insert-fstype 13 "Costco")
    (insert-fstype 14 "Sheetz")
    (insert-fstype 15 "Texaco")
    (insert-fstype 16 "Valero")
    (insert-fstype 17 "76")
    (insert-fstype 18 "Circle K")
    (insert-fstype 19 "Getty")
    (insert-fstype 20 "QuikTrip")
    (insert-fstype 21 "Friendship Xpress")
    (insert-fstype 22 "Murphy USA")
    (insert-fstype 23 "Stewart's")
    (insert-fstype 24 "Cumberland Farms")
    (insert-fstype 25 "Go-Mart")
    (insert-fstype 26 "Clark")
    (insert-fstype 27 "Kwik Trip")
    (insert-fstype 28 "Sinclair")
    (insert-fstype 29 "Pilot")
    (insert-fstype 30 "Love's")
    (insert-fstype 31 "Royal Farms")
    (insert-fstype 32 "Kroger")
    (insert-fstype 33 "Rutter's")
    (insert-fstype 34 "Speedway")
    (insert-fstype 35 "Kum & Go")
    (insert-fstype 36 "Mobile")
    (insert-fstype 38 "ARCO"))
  (j/update! db-spec :fuelstation {:type_id 0} [])
  (j/execute! db-spec [(format "update %s set location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)"
                               fpddl/tbl-fuelstation)]))
