; Copyright (c) Aaron Bedra. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license. You must not remove this notice, or any other,
; from this software.

(ns accession.protocol
  (:require [clojure.string :as str]))

(defn query
  "The new [unified protocol][up] was introduced in Redis 1.2, but it became
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

  [up]: http://redis.io/topics/protocol
  "
  [name & args]
  (str "*"
       (+ 1 (count args)) "\r\n"
       "$" (count name) "\r\n"
       (str/upper-case name) "\r\n"
       (str/join "\r\n"
                 (map (fn [a] (str "$" (count a) "\r\n" a))
                      args))
       "\r\n"))
;; <pre>"*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n"</pre>
