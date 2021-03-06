(ns blank.utils-test
  (:use midje.sweet)
  (:require
    [blank.utils :as u]))

(def money-map {"redirect" "/money$$--money"
                "money" {"mine" 200}})

(def uuid (u/uuid))

(facts
  (fact
    "we can roundtrip b64"
    (u/b64-str->map
      (u/map->b64-str money-map)) => money-map)
  (fact
    "we can roundtrip uuids"
    (u/parse-uuid (u/format-uuid uuid)) => uuid)
  (fact
    "trim to none"
    (u/trim-to-nil nil) => nil
    (u/trim-to-nil "    ") => nil
    (u/trim-to-nil "  a   ") => "a"
    (u/trim-to-nil "") => nil)
  (fact
    "get some thread macro works"
    (u/get-some-> money-map "redirect") => (get money-map "redirect")
    (u/get-some-> money-map "money" "mine") => 200
    (u/get-some-> money-map :a :b :c) => nil
    (u/get-some-> money-map "money" :mine) => nil))
