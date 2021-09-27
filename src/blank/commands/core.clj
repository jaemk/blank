(ns blank.commands.core
  (:require [blank.utils :as u]
            [blank.database.core :as db]
            [clojure.core.match :refer [match]]
            [migratus.core :as migratus]
            [clojure.core.strint :refer [<<]]))


(defn pending-migrations []
  (migratus/pending-list (db/migration-config)))

(defn applied-migrations []
  (migratus/completed-list (db/migration-config)))

(defn init-migrations! []
  (migratus/init (db/migration-config)))

(defn create-migration! [name']
  (migratus/create (db/migration-config) name'))

(defn migrate! []
  (migratus/migrate (db/migration-config)))

(defn rollback! [& args]
  (match
    (u/pad-vec args 1)
    [nil] (migratus/rollback (db/migration-config))
    [:all] (loop [applied (applied-migrations)]
             (when (not-empty applied)
               (rollback!)
               (recur (applied-migrations))))
    [k] ([u/ex-error!] (<< "unknown op ~{k}"))))

