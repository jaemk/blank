(ns user
  (:require [blank.core :as app]
            [blank.utils :as u]
            [blank.config :as config]
            [blank.database.core :as db]
            [blank.commands.core :as cmd]
            [blank.crypto :as cr]
            [blank.handlers :as h]
            [manifold.deferred :as d]
            [java-time :as jt]
            [cheshire.core :as json]
            [buddy.core.codecs :as codecs]
            [byte-streams :as bs]
            [aleph.http :as http])
  (:use [midje.repl]))

(->
  (u/map->b64-str {:sum (+ 1 2 3)
                   :enc (cr/encrypt "money" "password")})
  u/b64-str->map)

(defn -main []
  (app/-main))
