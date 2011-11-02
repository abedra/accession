(ns accession.core
  (:refer-clojure :exclude [get set])
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.net Socket)
           (java.io InputStreamReader)))

(defmulti response (fn [r] (char (.read r))))

(defmethod response \- [rdr]
  (.readLine rdr))

(defmethod response \+ [rdr]
  (.readLine rdr))

(defmethod response \$ [rdr]
  (.readLine rdr)
  (.readLine rdr))

(defmethod response \: [rdr]
  (.readLine rdr))

(defmethod response \* [rdr]
  (let [length (Integer/parseInt (.readLine rdr))
        resp (repeatedly (* 2 length) (fn [] (conj [] (.readLine rdr))))]
    (mapcat second (partition 2 resp))))

(defn query [name & args]
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

(defn request [cmd]
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
  (set         [key value])
  (get         [key])

  (incr        [key])

  (del         [key])
  (expire      [key seconds])
  (ttl         [key])

  (rpush       [key value])
  (lpush       [key value])
  (llen        [key])
  (lrange      [key start end])
  (lpop        [key])
  (rpop        [key])

  (sadd        [key member])
  (srem        [key member])
  (sismember   [key member])
  (sunion      [set1 set2])

  (zadd        [key score member])
  (zrange      [key start end]))