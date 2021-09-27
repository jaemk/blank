(ns blank.config
  (:require [taoensso.timbre :as t]
            [cheshire.core :as json]
            [clojure.string :as string]
            [cheshire.generate :refer [add-encoder]]
            [blank.utils :as u]
            [clojure.core.strint :refer [<<]]
            [clojure.java.io :as io])
  (:import (java.time ZoneId)
           (java.time.format DateTimeFormatter)
           (java.io StringWriter)
           (java.io PrintWriter)))


(defn- nil-if-ignorable [s]
  (if (or (string/starts-with? s "#")
          (empty? s))
    nil
    s))

(defn- parse-dotenv-line [line]
  (some-> line
          u/trim-to-nil
          nil-if-ignorable
          (string/split #"=" 2)))

(defn- parse-dotenv [raw]
  (->> raw
       string/split-lines
       (map parse-dotenv-line)
       (filter some?)
       flatten
       (apply hash-map)))

(defn- get-dotenv [filename]
  "Load a .env file to key/value pairs"
  (if-not (.exists (io/as-file filename))
    {}
    (->> filename
         slurp
         parse-dotenv)))

(defn- env
  "Get a value for an env var from a mapping, parse
  the value, or return a default"
  ([mapping k] (env mapping k {}))
  ([mapping k {:keys [default parse]
               :or   {default nil
                      parse   identity}}]
   (if-let [value (get mapping k)]
     (try
       (parse value)
       (catch Exception e
         (u/ex-error! (<< "Error parsing value ~{value} of key ~{k}: ~{e}"))))
     default)))

(defn- num-cpus [] (.availableProcessors (Runtime/getRuntime)))

(defn- not-nil [msg x]
  (if (nil? x)
    (u/ex-error! msg)
    x))

(defonce ^:dynamic *values* (atom nil))

(defn load-values!
  "Re/load environment variables from the system env,
  jvm properties, and a .env file"
  ([] (load-values! {}))
  ([{:keys [filename]
     :or   {filename ".env"}}]
   (swap! *values*
     (fn [_]
       (let [raw (into {} [(System/getenv)
                           (System/getProperties)
                           (get-dotenv filename)])]
         {
          :db-host     (env raw "DATABASE_HOST")
          :db-port     (env raw "DATABASE_PORT")
          :db-name     (env raw "DATABASE_NAME")
          :db-user     (env raw "DATABASE_USER")
          :db-password (env raw "DATABASE_PASSWORD")
          :db-max-connections
                       (env raw "DATABASE_MAX_CONNECTIONS" {:default nil :parse #(some-> % u/trim-to-nil u/parse-int)})
          :app-port    (env raw "PORT" {:default 3003 :parse u/parse-int})
          :app-public  (env raw "PUBLIC" {:default false :parse u/parse-bool})
          :repl-port   (env raw "REPL_PORT" {:default 3999 :parse u/parse-int})
          :repl-public (env raw "REPL_PUBLIC" {:default false :parse u/parse-bool})
          :pretty-logs (env raw "PRETTY_LOGS" {:default true :parse u/parse-bool})
          :max-client-connections
                       (env raw "MAX_CLIENT_CONNECTIONS" {:default 2000 :parse u/parse-int})
          :max-client-connections-per-host
                       (env raw "MAX_CLIENT_CONNECTIONS_PER_HOST" {:default 500 :parse u/parse-int})
          :keep-alive-client-connections
                       (env raw "KEEP_ALIVE_CLIENT_CONNECTIONS" {:default true :parse u/parse-bool})
          :keep-alive-client-timeout-ms
                       (env raw "KEEP_ALIVE_CLIENT_TIMEOUT_MS" {:default 5000 :parse u/parse-int})

          :encryption-key
                       (env raw "ENCRYPTION_KEY" {:parse (partial not-nil "ENCRYPTION_KEY is a required env var")})
          :signing-key (env raw "SIGNING_KEY" {:parse (partial not-nil "SIGNING_KEY is a required env var")})

          :hash        (env raw "COMMIT_HASH")
          :num-cpus    (num-cpus)
          :num-threads (* (num-cpus)
                          (env raw "THREAD_MULTIPLIER" {:default 8 :parse u/parse-int}))})))))


(defn v
  "Get an env var, optionally reload the current env"
  [k & {:keys [default reload dotenv-filename]
        :or   {default nil
               reload false
               dotenv-filename ".env"}}]
  (when (or reload (nil? @*values*))
    (load-values! {:filename dotenv-filename}))
  (if-let [value (get @*values* k)]
    value
    default))


(def utc-zone (ZoneId/of "UTC"))
(def ny-zone (ZoneId/of "America/New_York"))


;; -- Structured logging
(defonce ^:dynamic *pretty-console-logs* (atom (v :pretty-logs)))

(defn fmt-exc [e]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.printStackTrace e pw)
    (.toString sw)))

(defn fmt-exc-info [data]
  (if-let [e (:exc-info data)]
    (-> (dissoc data :exc-info)
        (assoc :_/exc-info (fmt-exc e)))
    data))

(defn log-args->map [log-args]
  (let [n-args (count log-args)
        -first (first log-args)
        -second (second log-args)
        data {:event nil}]
    (cond
      (= n-args 0) data
      (and (= n-args 1)
           (map? -first)) (merge data -first)
      (and (= n-args 1)
           (string? -first)) {:event -first}
      (and (= n-args 2)
           (string? -first)
           (map? -second)) (merge {:event -first} -second)
      :else (merge data {:args log-args}))))

(defn build-log-map [data]
  (let [{:keys [level instant _config vargs ?ns-str ?line ?msg-fmt]} data
        real-instant (.toInstant instant)
        utc-time (.atZone real-instant utc-zone)
        local-time (.atZone real-instant ny-zone)
        timestamp-utc (.format utc-time DateTimeFormatter/ISO_OFFSET_DATE_TIME)
        timestamp-local (.format local-time DateTimeFormatter/ISO_OFFSET_DATE_TIME)
        log-data (if-not (nil? ?msg-fmt)
                   {:event (apply format ?msg-fmt vargs)}
                   (log-args->map vargs))]
    (-> {:_/level                 level
         :_/timestamp             timestamp-utc
         :_/timestamp-local       timestamp-local
         :_/source-namespace      ?ns-str
         :_/source-namespace-line ?line}
        (merge log-data)
        fmt-exc-info)))

(defn pretty-fmt-log-data [data]
  (->> (seq data)
       (remove (fn [[k _]]
                 (-> (namespace k)
                     (= "_"))))
       (map #(string/join "=" %))
       (string/join " ")))

(defn fmt-log [data]
  (if-not (v :pretty-logs)
    (json/encode data)
    (let [{:keys [_/level
                  _/timestamp
                  _/timestamp-local
                  _/source-namespace
                  _/source-namespace-line
                  _/exc-info
                  event]} data]
      (format "%-25s [%-7s] %-20s [%s:%d] %s%s"
              timestamp
              (name level)
              event
              source-namespace
              source-namespace-line
              (pretty-fmt-log-data (assoc data :timestamp-local timestamp-local))
              (if exc-info
                (str " " exc-info)
                "")))))


;; override the log formatter
(t/merge-config!
  {:output-fn (fn [d]
                (->> d
                     build-log-map
                     (into (sorted-map))
                     fmt-log))})


;; -- json encoder additions
(add-encoder java.net.InetSocketAddress
             (fn [d jsonGenerator]
               (.writeString jsonGenerator (str d))))
