; Copyright (c) Aaron Bedra. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license. You must not remove this notice, or any other,
; from this software.

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
      (apply str
             (map char
                  (take length
                        (doall (repeatedly (+ 2 length) #(.read rdr)))))))))

(defmethod response \: [rdr]
  (Long/parseLong (.readLine rdr)))

(defmethod response \* [rdr]
  (let [length (Integer/parseInt (.readLine rdr))]
    (doall (repeatedly length #(response rdr)))))

(defn request
  "Creates the connection to the sever and sends the query. Parses and
  returns the response."
  [connection & query]
  (let [socket (:socket connection)
        in (.getInputStream socket)
        out (.getOutputStream socket)
        rdr (io/reader (InputStreamReader. in))]
    (.write out (.getBytes (apply str query)))
    (if (next query)
      (doall (repeatedly (count query) #(response rdr)))
      (response rdr))))
