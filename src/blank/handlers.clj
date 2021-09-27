(ns blank.handlers
  (:require [blank.utils :refer [->resp ->text ->json ->redirect]]
            [blank.config :as config]))



;(defn build-cookie [auth-token]
;  (format
;    "auth_token=%s; Secure; HttpOnly; Max-age=%s; Path=/; Domain=%s; SameSite=Lax"
;    auth-token
;    (* 60 24 30)
;    (config/v :domain)))

;(defn user-for-req
;  "load a user based on their auth token, or null"
;  [req]
;  (let [cookie (u/get-some-> req :headers :cookie)
;        [_ token] (some->> cookie (re-find #"auth_token=(\w+)"))
;        user-auth (some->> token cr/sign (db/get-user-for-auth (db/conn)))
;        user-id (some-> user-auth :id)
;        ^Instant created (some-> user-auth :created ((fn [^Timestamp ts] (.toInstant ts))))
;        ^Instant month-ago (jt/instant (jt/minus (u/utc-now) (jt/days 30)))]
;    (if (or (nil? created) (.isBefore created month-ago))
;      (do
;        (t/info "cookie expired" {:created created :user-id user-id})
;        nil)
;      user-id)))


(defn index [_]
  (->text "hello!"))


(defn status [_]
  (->json {:status  :ok
           :version (config/v :app-version)}))


(defn not-found [_]
  (->resp {:body   "nothing to see here"
           :status 404}))
