(ns accession.request
  (:require [clojure.java.io :as io])
  (:import (java.net Socket)
           (java.io InputStreamReader)))

(defmulti response
  "Redis will reply to commands with different kinds of replies. It is
  possible to check the kind of reply from the first byte sent by the
  server:

  * With a single line reply the first byte of the reply will be `+`
  * With an error message the first byte of the reply will be `-`
  * With an integer number the first byte of the reply will be `:`
  * With bulk reply the first byte of the reply will be `$`
  * With multi-bulk reply the first byte of the reply will be `*`"
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

(defn request
  "Creates the connection to the sever and sends the query. Parses and
  returns the response."
  [cmd]
  (let [socket (doto (Socket. "127.0.0.1" 6379)
                 (.setTcpNoDelay true)
                 (.setKeepAlive true))
        in (.getInputStream socket)
        out (.getOutputStream socket)
        rdr (io/reader (InputStreamReader. in))]
    (.write out (.getBytes cmd))
    (response rdr)))