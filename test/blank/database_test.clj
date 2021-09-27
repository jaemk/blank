(ns blank.database-test
  (:use midje.sweet)
  (:require [blank.database.core :as db]
            [blank.test-utils :refer [setup-db
                                      teardown-db
                                      truncate-db]]))

(defonce test-db (atom nil))
(defonce state (atom {}))

;(with-state-changes
;  [(before :contents (do
;                       (setup-db test-db)
;                       (reset! state {})))
;   (after :contents (teardown-db test-db))
;   (before :facts (truncate-db test-db))])
