(ns blank.crypto
  (:require [blank.config :as config]
            [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [buddy.core.mac :as mac]
            [buddy.core.crypto :as crypto]
            [buddy.core.kdf :as kdf])
  (:import (com.password4j Password Argon2Function)
           (com.password4j.types Argon2)))


(defn- argon-hasher []
  "Get an argon2-id hasher configured for hashing user passwords"
  (Argon2Function/getInstance
    (* 10 1024)  ; memory KB
    30           ; iterations
    8            ; parallelism
    256          ; output length bytes
    Argon2/ID    ; argon type
    19))         ; version


(defn hash-pw [pw]
  "Generate a user password hash using argon2-id"
  (.. (Password/hash pw)
      (addRandomSalt 12)
      (with (argon-hasher))
      (getResult)))


(defn check-pw [pw pw-hash]
  "Check a user password against an argon2-id hash"
  (.. (Password/check pw pw-hash)
      (with (argon-hasher))))


(defn sign
  "Generate an hmac signature for some text using the default signing key"
  ([text]
   (sign text (config/v :signing-key)))
  ([text key]
   (-> (mac/hash text {:key key :alg :hmac+sha512})
       codecs/bytes->hex)))


(defn verify-sig
  "Check if a signature was generated for some text"
  ([text signature-hex]
   (verify-sig text signature-hex (config/v :signing-key)))
  ([text signature-hex key]
   (mac/verify text
               (codecs/hex->bytes signature-hex)
               {:key key :alg :hmac+sha512})))


(defn rand-bytes
  "Build a byte buffer of securely randomized bytes"
  [n-bytes]
  (nonce/random-bytes n-bytes))


(defn new-salt
  "New random bytes of the correct size for salting"
  []
  (rand-bytes 16))


(defn derive-enc-key
  "Stretch a password to make it suitable for use as an
  encryption key. Returns a byte buffer"
  ([raw-password salt]
   (derive-enc-key raw-password salt 64))
  ([raw-password salt take-bytes]
   (kdf/get-bytes
     (kdf/engine {:key        raw-password
                  :salt       salt
                  :alg        :pbkdf2
                  :digest     :sha512
                  :iterations 1e4})
     take-bytes)))


(defn encrypt
  "Encrypt some text using our default encryption key
  returning a map of the encrypted data and the salt & iv
  (that must be used for decryption) as hex strings"
  ([text]
   (let [salt (new-salt)]
     (encrypt text (config/v :encryption-key) salt)))
  ([text password]
   (let [salt (new-salt)]
     (encrypt text password salt)))
  ([text password salt]
   (let [initialization-vector (rand-bytes 16)]
     {:data (-> (crypto/encrypt
                  (codecs/to-bytes text)
                  (derive-enc-key password salt 64)
                  initialization-vector
                  {:algorithm :aes256-cbc-hmac-sha512})
                codecs/bytes->b64u
                codecs/bytes->str)
      :salt (codecs/bytes->hex salt)
      :iv   (codecs/bytes->hex initialization-vector)})))


(defn decrypt
  "Decrypt a map of the same shape returned by `encrypt`"
  ([data]
   (decrypt data (config/v :encryption-key)))
  ([{:keys [data iv salt]} password]
   (-> (crypto/decrypt
         (-> (codecs/to-bytes data)
             codecs/b64u->bytes)
         (derive-enc-key password (codecs/hex->bytes salt) 64)
         (codecs/hex->bytes iv)
         {:algorithm :aes256-cbc-hmac-sha512})
       codecs/bytes->str)))
