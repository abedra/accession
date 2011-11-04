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