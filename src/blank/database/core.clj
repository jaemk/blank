(ns blank.database.core
  (:require [blank.config :as config]
            [hikari-cp.core :refer [make-datasource]]
            [clojure.java.jdbc :as j]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [honeysql.format]
            [honeysql.types]
    #_:clj-kondo/ignore
            [honeysql-postgres.format :refer :all]
            [honeysql-postgres.helpers :as pg]
            [blank.utils :as u]
            [taoensso.timbre :as t]))


; ----- datasource config ------
(def db-config
  {:adapter           "postgresql"
   :username          (config/v :db-user)
   :password          (config/v :db-password)
   :database-name     (config/v :db-name)
   :server-name       (config/v :db-host)
   :port-number       (config/v :db-port)
   :maximum-pool-size (or (config/v :db-max-connections) (config/v :num-threads))})

(defonce datasource (delay (make-datasource db-config)))

(defn conn [] {:datasource @datasource})

(defn migration-config
  ([] (migration-config (conn)))
  ([connection] {:store         :database
                 :migration-dir "migrations"
                 ;:init-script   "init.sql"
                 :db            connection}))


; ----- helpers ------
(defn first-or-err
  "create a fn for retrieving a single row or throwing an error"
  [ty]
  (fn [result-set]
    (if-let [one (first result-set)]
      one
      (u/ex-does-not-exist! ty))))


(defn pluck
  "Plucks the first item from a result-set if it's a seq of only one item.
   Asserts the result-set, `rs`, has something in it, unless `:empty->nil true`"
  [rs & {:keys [empty->nil]
         :or   {empty->nil false}}]
  (let [empty-or-nil (or (nil? rs)
                         (empty? rs))]
    (cond
      (and empty-or-nil empty->nil) nil
      empty-or-nil (u/ex-error!
                     (format "Expected a result returned from database query, found %s" rs))
      :else (let [[head tail] [(first rs) (rest rs)]]
              (if (empty? tail)
                head
                rs)))))


(defn insert!
  "Executes insert statement returning a single map if
  the insert result is a seq of one item"
  [conn stmt]
  (j/query conn
           (-> stmt
               (pg/returning :*)
               sql/format)
           {:result-set-fn pluck}))


(defn update! [conn stmt]
  (j/query conn
           (-> stmt
               (pg/returning :*)
               sql/format)
           {:result-set-fn #(pluck % :empty->nil true)}))


(defn delete! [conn stmt]
  (j/query conn
           (-> stmt
               (pg/returning :*)
               sql/format)
           {:result-set-fn #(pluck % :empty->nil true)}))


(defn query [conn stmt & {:keys [first-err-key row-fn result-set-fn]
                          :or   {first-err-key nil
                                 row-fn        identity}}]
  (let [rs-fn (if (nil? first-err-key)
                result-set-fn
                (first-or-err first-err-key))]
    (j/query conn
             (-> stmt
                 sql/format)
             {:row-fn        row-fn
              :result-set-fn rs-fn})))



; ----- database queries ------
