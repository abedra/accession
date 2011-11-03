(ns accession.core
  (:refer-clojure :exclude [get set keys])
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.net Socket)
           (java.io InputStreamReader)))

(defmulti response
  "Redis will reply to commands with different kinds of replies. It is
  possible to check the kind of reply from the first byte sent by the
  server:

  * With a single line reply the first byte of the reply will be \"+\"
  * With an error message the first byte of the reply will be \"-\"
  * With an integer number the first byte of the reply will be \":\"
  * With bulk reply the first byte of the reply will be \"$\"
  * With multi-bulk reply the first byte of the reply will be \"*\""
  (fn [r] (char (.read r))))

(defmethod response \- [rdr]
  (.readLine rdr))

(defmethod response \+ [rdr]
  (.readLine rdr))

(defmethod response \$ [rdr]
  (let [length (Integer/parseInt (.readLine rdr))]
    (when (not= length -1)
      (apply str (map char (take length (doall (repeatedly (+ 2 length) #(.read rdr)))))))))

(defmethod response \: [rdr]
  (.readLine rdr))

(defmethod response \* [rdr]
  (let [length (Integer/parseInt (.readLine rdr))]
    (doall (repeatedly length #(response rdr)))))

(defn query
  "The new unified protocol was introduced in Redis 1.2, but it became
   the standard way for talking with the Redis server in Redis 2.0.
   In the unified protocol all the arguments sent to the Redis server
   are binary safe. This is the general form:

   *<number of arguments> CR LF
   $<number of bytes of argument 1> CR LF
   <argument data> CR LF
   ...
   $<number of bytes of argument N> CR LF
   <argument data> CR LF

   See the following example:

   *3
   $3
   SET
   $5
   mykey
   $7
   myvalue

   This is how the above command looks as a quoted string, so that it
   is possible to see the exact value of every byte in the query:

   \"*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n\""
  [name & args]
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

(defn request
  "Creates the connection to the sever and sends the query. Parses and
  returns the response"
  [cmd]
  (let [socket (doto (Socket. "127.0.0.1" 6379)
                 (.setTcpNoDelay true)
                 (.setKeepAlive true))
        in (.getInputStream socket)
        out (.getOutputStream socket)
        rdr (io/reader (InputStreamReader. in))]
    (.write out (.getBytes cmd))
    (response rdr)))

(defmacro defquery
  [name params]
  (let [command (str name)]
    `(defn ~name ~params
       (request (query ~command ~@params)))))

(defmacro defqueries
  [& queries]
  `(do ~@(map (fn [q] `(defquery ~@q)) queries)))

(defqueries
  (echo        [message])
  (exists      [key])
  (keys        [pattern])
  
  (set         [key value])
  (get         [key])
  (append      [key value])
  (getset      [key value])

  (incr        [key])
  (incrby      [key increment])

  (del         [key])
  (expire      [key seconds])
  (ttl         [key])

  (llen        [key])
  (lpop        [key])
  (lpush       [key value])
  (lpushx      [key value])
  (lset        [key value index])
  (lrem        [key count value])
  (ltrim       [key start stop])
  (lrange      [key start end])
  (rpop        [key])
  (rpush       [key value])
  (rpushx      [key value])
  (rpoplpush   [source destination])

  (hset        [key field value])
  (hsetnx      [key field value])
  (hget        [key field])
  (hexists     [key field])
  (hdel        [key field])
  (hgetall     [key])
  (hincrby     [key field increment])
  (hkeys       [key])
  (hvals       [key])
  (hlen        [key])
  
  (sadd        [key member])
  (srem        [key member])
  (sismember   [key member])
  (sunion      [set1 set2])

  (zadd        [key score member])
  (zrange      [key start end])

  (flushdb     [])
  (flushall    [])
  (dbsize      [])
  (info        []))
