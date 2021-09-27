(ns user)

(defn initenv []
  (require '[blank.core :as app]
           '[blank.utils :as u]
           '[blank.config :as config]
           '[blank.database.core :as db]
           '[blank.commands.core :as cmd]))
