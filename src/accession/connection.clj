; Copyright (c) Aaron Bedra. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license. You must not remove this notice, or any other,
; from this software.

(ns accession.connection
  (:import (java.net Socket)))

(defn connection [spec]
  (let [default {:host "127.0.0.1"
                 :port 6379
                 :password nil
                 :socket nil}
        params (merge default spec)]
    (assoc params :socket (doto (Socket. (:host params) (:port params))
                            (.setTcpNoDelay true)
                            (.setKeepAlive true)))))