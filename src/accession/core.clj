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
  (let [length (Integer/parseInt (.readLine rdr))
        resp (repeatedly (* 2 length) (fn [] (conj [] (.readLine rdr))))]
    (mapcat second (partition 2 resp))))

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