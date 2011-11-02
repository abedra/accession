(ns accession.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.net Socket)
           (java.io InputStreamReader)))

(defmulti response (fn [r] (char (.read r))))

(defmethod response \+ [rdr]
  (.readLine rdr))

(defmethod response \$ [rdr]
  (.readLine rdr)
  (.readLine rdr))

(defmethod response \: [rdr]
  (.readLine rdr))

(defmethod response \* [rdr]
  (throw (UnsupportedOperationException. "Not Implemented")))

(defn command [name & args]
  (str "*"
       (+ 1 (count args)) "\r\n"
       "$" (count name) "\r\n"
       (str/upper-case name) "\r\n"
       (str/join "\r\n"
                 (map
                  (fn [a]
                    (str "$" (count a) "\r\n" a))
                  args))
       "\r\n"))

(defn send-command [cmd]
  (let [socket (doto (Socket. "127.0.0.1" 6379)
                 (.setTcpNoDelay true)
                 (.setKeepAlive true))
        in (.getInputStream socket)
        out (.getOutputStream socket)
        rdr (io/reader (InputStreamReader. in))]
    (.write out (.getBytes cmd))
    (response rdr)))

(comment
  ; Test basic command construction
  (command "set" "foo" "bar")
  (command "get" "bar")

  ;; Tutorial (from the website)
  (send-command (command "set" "server:name" "fido"))
  (send-command (command "get" "server:name"))

  (send-command (command "set" "connections" "10"))
  (send-command (command "incr" "connections"))
  (send-command (command "del" "connections"))
  (send-command (command "incr" "connections"))

  (send-command (command "set" "resource:lock" "Redis Demo"))
  (send-command (command "expire" "resource:lock" "120"))
  (send-command (command "ttl" "resource:lock"))
  (send-command (command "ttl" "count"))

  (send-command (command "rpush" "friends" "Tom"))
  (send-command (command "rpush" "friends" "Bob"))
  (send-command (command "lpush" "friends" "Sam"))

  ; TODO
  (send-command (command "lrange" "friends" "0" "-1"))
  (send-command (command "lrange" "friends" "0" "1"))
  (send-command (command "lrange" "friends" "0" "2"))

  (send-command (command "llen" "friends"))
  (send-command (command "lpop" "friends"))
  (send-command (command "rpop" "friends"))
  (send-command (command "llen" "friends"))
  ; TODO
  (send-command (command "lrange" "friends" "0" "-1"))

  (send-command (command "sadd" "superpowers" "flight"))
  (send-command (command "sadd" "superpowers" "x-ray vision"))
  (send-command (command "sadd" "superpowers" "reflexes"))
  (send-command (command "srem" "superpowers" "reflexes"))
  (send-command (command "sismember" "superpowers" "flight"))
  (send-command (command "sismember" "superpowers" "reflexes"))
  (send-command (command "sadd" "birdpowers" "pecking"))
  (send-command (command "sadd" "birdpowers" "flight"))
  ; TODO
  (send-command (command "sunion" "superpowers" "birdpowers"))

  (send-command (command "zadd" "hackers" "1940" "Alan Kay"))
  (send-command (command "zadd" "hackers" "1953" "Richard Stallman"))
  (send-command (command "zadd" "hackers" "1965" "Yukihiro Matsumoto"))
  (send-command (command "zadd" "hackers" "1916" "Claude Shannon"))
  (send-command (command "zadd" "hackers" "1969" "Linus Torvalds"))
  (send-command (command "zadd" "hackers" "1912" "Alan Turning"))
  ; TODO
  (send-command (command "zrange" "hackers" "2" "4"))
)
