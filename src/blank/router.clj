(ns blank.router
  (:require [compojure.core :refer [routes ANY GET POST]]
            [compojure.route :as route]
            [blank.handlers :as h]))

(defn load-routes []
  (routes
    (ANY "/" _ h/index)
    (ANY "/status" _ h/status)
    (route/not-found h/not-found)))
