; Copyright (c) Aaron Bedra. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license. You must not remove this notice, or any other,
; from this software.

(ns accession.core
  (:refer-clojure :exclude [get set keys type sync sort eval])
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.net Socket]
           [java.io OutputStream BufferedInputStream DataInputStream]
           [org.apache.commons.pool KeyedPoolableObjectFactory]
           [org.apache.commons.pool.impl GenericKeyedObjectPool]))

;;;; Connections
;; Pooling stuff adapted from redis-clojure

(defprotocol IConnection
  (get-spec      [conn])
  (conn-alive?   [conn])
  (close-conn    [conn])
  (input-stream  [conn])
  (output-stream [conn]))

(defprotocol IConnectionPool
  (get-conn        [pool spec])
  (release-conn    [pool conn] [pool conn exception]))

(declare request ping auth)

(defrecord Connection [^Socket socket spec]
  IConnection
  (get-spec      [this] spec)
  (conn-alive?   [this] (if (:for-listener? spec) true ; TODO
                            (= "PONG" (try (request this (ping))
                                           (catch Exception _)))))
  (close-conn    [this] (.close socket))
  (input-stream  [this] (-> (.getInputStream socket)
                            (BufferedInputStream.)
                            (DataInputStream.)))
  (output-stream [this] (.getOutputStream socket)))

(defrecord ConnectionPool [^GenericKeyedObjectPool pool]
  IConnectionPool
  (get-conn     [this spec] (.borrowObject pool spec))
  (release-conn [this conn] (.returnObject pool (get-spec conn) conn))
  (release-conn [this conn exception] (.invalidateObject pool (get-spec conn)
                                                         conn)))

(defn- make-new-connection
  "Actually create a new socket connection for use in pool or as a persistent
  connection for Pub/Sub."
  [{:keys [host port password timeout] :as spec}]
  (let [socket (doto (Socket. ^String host ^Integer port)
                 (.setTcpNoDelay true)
                 (.setKeepAlive true)
                 (.setSoTimeout ^Integer timeout))
        conn   (Connection. socket spec)]
    (when password (request conn (auth password)))
    conn))

(defn- make-connection-factory []
  (reify KeyedPoolableObjectFactory
    (makeObject      [this spec] (make-new-connection spec))
    (activateObject  [this spec conn])
    (validateObject  [this spec conn] (conn-alive? conn))
    (passivateObject [this spec conn])
    (destroyObject   [this spec conn] (close-conn conn))))

(defn make-connection-spec
  [& {:keys [host port password timeout]
      :or   {host "127.0.0.1" port 6379 password nil timeout 0}}]
  {:host host :port port :password password :timeout timeout})

(defn- set-pool-option [^GenericKeyedObjectPool pool [opt v]]
  (case opt
    :max-active (.setMaxActive pool v)
    :max-total  (.setMaxTotal pool v)
    :min-idle   (.setMinIdle pool v)
    :max-idle   (.setMaxIdle pool v)
    :max-wait   (.setMaxWait pool v)
    :lifo       (.setLifo pool v)
    :test-on-borrow  (.setTestOnBorrow pool v)
    :test-on-return  (.setTestOnReturn pool v)
    :test-while-idle (.setTestWhileIdle pool v)
    :when-exhausted-action         (.setWhenExhaustedAction pool v)
    :num-tests-per-eviction-run    (.setNumTestsPerEvictionRun pool v)
    :time-between-eviction-runs-ms (.setTimeBetweenEvictionRunsMillis pool v)
    :min-evictable-idle-time-ms    (.setMinEvictableIdleTimeMillis pool v))
  pool)

(defn make-connection-pool
  "Create a connection pool. For option documentation see
  http://commons.apache.org/pool/apidocs/org/apache/commons/pool/impl/GenericKeyedObjectPool.html"
  [& pool-opts]
  (let [;; Defaults stolen from Jedis
        default {:test-while-idle               true
                 :num-tests-per-eviction-run    -1
                 :min-evictable-idle-time-ms    60000
                 :time-between-eviction-runs-ms 30000}]
    (ConnectionPool.
     (reduce set-pool-option (GenericKeyedObjectPool. (make-connection-factory))
             (merge default (apply hash-map pool-opts))))))

(defmacro with-connection
  "Evaluate pipelined Redis commands in the context of a connection to Redis
  server. Returns the server's response. Use 'make-connection-pool' and
  'make-connection-spec' to generate the required arguments."
  [connection-pool connection-spec & commands]
  `(try
     (let [conn# (get-conn ~connection-pool ~connection-spec)]
       (try
         (let [server-response# (#'request conn# ~@commands)]
           (release-conn ~connection-pool conn#)
           server-response#)
         ;; Failed to execute body
         (catch Exception e# (release-conn ~connection-pool conn# e#)
                (throw e#))))
     ;; Failed to get connection from pool
     (catch Exception e# (throw e#))))

;;;; Redis network protocol

(def ^:private ^:const ^String charset "UTF-8")

(defn- query
  "Takes a Clojure form like '(set \"mykey\" \"myvalue\")' and returns the
  equivalent Redis command in its native byte string format. We call this
  string a query

  The new [unified protocol][up] was introduced in Redis 1.2, but it became
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

  [up]: http://redis.io/topics/protocol"
  [name & args]
  (str "*"
       (inc (count args)) "\r\n"
       "$" (count name) "\r\n"
       (str/upper-case name) "\r\n"
       (str/join
        "\r\n"
        (map (fn [a] (str "$" (count (.getBytes (str a) charset)) "\r\n" a))
             args))
       ;; "\r\n"
       ;; Better leave final CRLF for request fn so that Redis can interpret
       ;; malformed requests clearly and send back an ERR reply
       ))
;; <pre>"*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n"</pre>

(def ^:dynamic *return-bytes?* false)

(defmacro return-bytes
  "Wrap a 'with-connection', 'make-listener', or 'with-open-listener' block
  with this macro to get Redis server responses as byte arrays instead of
  strings."
  [& body] `(binding [*return-bytes?* true] ~@body))

(defn- parse-response
  "Redis will reply to commands with different kinds of replies. It is
  possible to check the kind of reply from the first byte sent by the
  server:

  * With a single line reply the first byte of the reply will be `+`
  * With an error message the first byte of the reply will be `-`
  * With an integer number the first byte of the reply will be `:`
  * With bulk reply the first byte of the reply will be `$`
  * With multi-bulk reply the first byte of the reply will be `*`"
  [^DataInputStream in]
  (let [reply-type (char (.readByte in))]
    (case reply-type
      \+ (.readLine in)
      \- (throw (Exception. (.readLine in)))
      \: (Long/parseLong (.readLine in))
      \$ (let [data-length (Integer/parseInt (.readLine in))]
           (when-not (neg? data-length)
             (let [data (byte-array data-length)
                   crlf (byte-array 2)]
               (.read in data 0 data-length)
               (.read in crlf 0 2)
               (if *return-bytes?* data (String. data charset)))))
      \* (let [length (Integer/parseInt (.readLine in))]
           (doall (repeatedly length (fn [] (parse-response in)))))
      (throw (Exception. (format "Unknown response type: %1c" reply-type))))))

(defn- send-command
  [conn & queries]
  (let [out ^OutputStream (output-stream conn)]
    (dorun (map (fn [q] (.write out (.getBytes (str q "\r\n") charset)))
                queries))))

(defn- request
  "Send a request (pipelined commands) to Redis server then recieve and parse
  response(s)."
  [conn & queries]
  (when (seq queries)
    (apply send-command conn queries)
    (let [in ^DataInputStream (input-stream conn)]
      (if (next queries)
        (doall (repeatedly (count queries) (fn [] (parse-response in))))
        (parse-response in)))))

;;;; Commands

;; We would like to create one function for each command which Redis
;; supports. The set function would look something like this:
;;
;;     (defn set [key value]
;;       (query "set" key value))
;;
;; Similarly, the get function would be:
;;
;;     (defn get [key]
;;       (query "get" key))
;;
;; Because each of these functions has the same pattern, we can use a
;; macro to create them and save a lot of typing.

(defn parameters
  "This function enables vararg style definitions in the queries. For
  example you can write:

       (mget [key & keys])

  and the defquery macro will properly expand out into a variable
  argument function"
  [params]
  (let [[args varargs] (split-with #(not= '& %)  params)]
    (conj (vec args) (last varargs))))

(defmacro defquery
  "Given a redis command and a parameter list, create a function of
  the form:

       (defn <name> <parameter-list>
         (query <command> <p1> <p2> ... <pN>))

  The name which is passed is a symbol and is first used as a symbol
  for the function name. We convert this symbol to a string and use
  that string as the command name. Use -'s to separate words for
  multi-word commands: script-flush for \"SCRIPT FLUSH\" command, etc.

  params is a list of N symbols which represent the parameters to the
  function. We use this list as the parameter-list when we create the
  function. Each symbol in this list will be an argument to query
  after the command. We use unquote splicing (~@) to insert these
  arguments after the command string."
  [name params]
  (let [command (str/replace (str name) #"-" " ")
        p (parameters params)]
    `(defn ~name ~params
       (apply query ~command ~@p))))

;; A call to:
;;
;; <pre><code>(defqueries (set [key value]))</code></pre>
;;
;; will expand to:
;;
;; <pre><code>(do (defn set [key value] (query "set" key value)))</pre></code>
;;

(defmacro defqueries
  "Given any number of redis commands and argument lists, convert them
  to function definitions.

  This is an interesting use of unquote splicing. Unquote splicing
  works on a sequence and that sequence can be the result of a
  function call as it is here. The function which produces this
  sequence maps each argument passed to this macro over a function
  which takes each list like (set [key value]), binds it to q, and
  uses unquote splicing again to create a call to defquery which
  looks like (defquery set [key value]).

  Finally, a macro can only return a single form, so we wrap all of
  the produced expressions in a do special form."
  [& queries]
  `(do ~@(map (fn [q] `(defquery ~@q)) queries)))

(defqueries
  (auth             [password])
  (quit             [])
  (select           [index])
  (bgwriteaof       [])
  (bgsave           [])
  (flushdb          [])
  (flushall         [])
  (dbsize           [])
  (info             [])
  (save             [])
  (sync             [])
  (lastsave         [])
  (shutdown         [])
  (slaveof          [host port])
  (slowlog          [command argument])
  (config-get       [parameter])
  (config-set       [parameter value])
  (config-resetstat [])
  (debug-object     [key])
  (debug-segfault   [])

  (echo             [message])
  (ping             [])
  (discard          [])
  (exec             [])
  (monitor          [])
  (multi            [])
  (object           [subcommand & arguments])

  (set              [key value])
  (setex            [key seconds value])
  (setnx            [key value])
  (setbit           [key offset value])
  (mset             [key value & pairs])
  (msetnx           [key value & pairs])
  (setrange         [key offset value])
  (get              [key])
  (mget             [key & keys])
  (getbit           [key offset])
  (getset           [key value])
  (getrange         [key start end])
  (append           [key value])
  (keys             [pattern])
  (exists           [key])
  (randomkey        [])
  (type             [key])
  (move             [key db])
  (rename           [key newkey])
  (renamenx         [key newkey])
  (strlen           [key])
  (watch            [key & keys])
  (unwatch          [])
  (del              [key & keys])

  (incr             [key])
  (incrby           [key increment])
  (decr             [key])
  (decrby           [key increment])

  (expire           [key seconds])
  (expireat         [key seconds])
  (persist          [key])
  (ttl              [key])

  (llen             [key])
  (lpop             [key])
  (lpush            [key value])
  (lpushx           [key value & values])
  (lset             [key value index])
  (linsert          [key before-after pivot value])
  (lrem             [key count value])
  (ltrim            [key start stop])
  (lrange           [key start end])
  (lindex           [key index])
  (rpop             [key])
  (rpush            [key value & values])
  (rpushx           [key value])
  (rpoplpush        [source destination])
  (blpop            [key timeout])
  (brpop            [key timeout])
  (brpoplpush       [source destination timeout])

  (hset             [key field value])
  (hmset            [key field value & pairs])
  (hsetnx           [key field value])
  (hget             [key field])
  (hmget            [key field & fields])
  (hexists          [key field])
  (hdel             [key field & fields])
  (hgetall          [key])
  (hincrby          [key field increment])
  (hkeys            [key])
  (hvals            [key])
  (hlen             [key])

  (sadd             [key member & members])
  (srem             [key member & members])
  (sismember        [key member])
  (smembers         [key])
  (sunion           [key & keys])
  (sunionstore      [destination key & keys])
  (sdiff            [key & keys])
  (sdiffstore       [destination set1 set2])
  (sinter           [key & keys])
  (sinterstore      [destination key & keys])
  (scard            [key])
  (smove            [source desination member])
  (spop             [key])
  (srandmember      [key])

  (zadd             [key score member & more])
  (zrange           [key start stop & more])
  (zrangebyscore    [key min max & more])
  (zrevrange        [key start stop & more])
  (zrevrangebyscore [key max min & more])
  (zcard            [key])
  (zcount           [key min max])
  (zincrby          [key increment member])
  (zrank            [member])
  (zrem             [key member & members])
  (zremrangebyrank  [key start stop])
  (zremrangebyscore [key min max])
  (zrevrank         [key member])
  (zscore           [key member])

  (subscribe        [& channels])
  (unsubscribe      [& channels])
  (psubscribe       [& patterns])
  (punsubscribe     [& patterns])
  (publish          [channel message])

  (eval             [script numkeys & more])
  (evalsha          [sha1 numkeys & more])
  (script-exists    [script & scripts])
  (script-flush     [])
  (script-kill      [])
  (script-load      [script]))

;;;; Non-standard commands
;; To provide conveniences over defquery implementations, etc.

(defn zinterstore
  [dest-key source-keys & options]
  (apply query "zinterstore" dest-key
         (count source-keys) (concat source-keys options)))

(defn zunionstore
  [dest-key source-keys & options]
  (apply query "zunionstore" dest-key
         (count source-keys) (concat source-keys options)))

;; Sort stuff adapted from redis-clojure
(defn- parse-sort-args [args]
  (loop [out [] remaining-args (seq args)]
    (if-not remaining-args
      out
      (let [[type & args] remaining-args]
        (case type
          :by (let [[pattern & rest] args]
                (recur (conj out "BY" pattern) rest))
          :limit (let [[offset count & rest] args]
                   (recur (conj out "LIMIT" offset count) rest))
          :get (let [[pattern & rest] args]
                 (recur (conj out "GET" pattern) rest))
          :mget (let [[patterns & rest] args]
                  (recur (into out (interleave (repeat "GET")
                                               patterns)) rest))
          :store (let [[dest & rest] args]
                   (recur (conj out "STORE" dest) rest))
          :alpha (recur (conj out "ALPHA") args)
          :asc   (recur (conj out "ASC")   args)
          :desc  (recur (conj out "DESC")  args)
          (throw (Exception. (str "Unknown sort argument: " type))))))))

(defn sort
  "Possible arguments are:
  :by pattern
  :limit offset count
  :get pattern
  :mget patterns
  :store destination
  :alpha
  :asc
  :desc"
  [key & sort-args]
  (apply query "sort" key (parse-sort-args sort-args)))

;;;; Pub/sub

(defrecord PubSubListener [connection handlers])

(defn with-open-listener
  "Evaluate pipelined Redis commands in the context of a preexisting Pub/Sub
  listener's persistent connection to Redis server. Returns nil. Only
  'p/un/subscribe' commands may be issued."
  [listener & commands] (apply send-command (:connection listener) commands))

(defn close-listener [listener] (close-conn (:connection listener)))

(defn make-listener
  "Create a persistent connection to Redis server and a thread to listen for
  published messages from channels that we'll subscribe to using 'p/subscribe'
  commands.

  While listening, incoming messages will be dispatched by source (channel or
  pattern) to the given handler functions:

       {\"channel1\" (fn [response] (prn \"Channel match: \" response))
        \"user*\"    (fn [response] (prn \"Pattern match: \" response))}

  SUBSCRIBE response:  `[\"message\" channel message]`
  PSUBSCRIBE response: `[\"pmessage\" pattern matching-channel message]`

  Returns a PubSubListener containing:
    1. The persistent connection to allow manual closing with 'close-listener'
       and subscription adjustments via 'with-open-listener'.
    2. An atom containing given handlers to allow handler adjustments.

  Listener's thread will close automatically when it's connection to Redis is
  closed."
  ;; TODO Once https://github.com/antirez/redis/issues/420 is implemented, can
  ;; add timed connection-alive check and auto-reconnect options.
  [connection-spec handlers & subscription-commands]
  (let [handlers-atom (atom handlers)
        conn (make-new-connection (assoc connection-spec
                                    :for-listener? true))
        in   (input-stream conn)]

    ;; Note that future will close when conn is closed
    (-> (fn receive-published-message! []
          (let [;; Blocks on input-stream
                [_ source :as next-response] (parse-response in)]
            (when-let [f (clojure.core/get @handlers-atom source)]
              (f next-response))))
        repeatedly doall future)

    (apply send-command conn subscription-commands)
    (PubSubListener. conn handlers-atom)))

;;;; Simple benching

(comment

  ;;    Ref: +/- 48k SETs/s for redis-benchmark  -n 100k -c 5
  ;;         +/- 14k SETs/s for jedis-benchmark* -n 100k -t 5 -c 5 -s 1
  ;;         +/- 28k SETs/s for redis-clojure :requests 100k :clients 5
  ;; Result: +/- 33k SETs/s for accession
  ;;  Notes: +/- 10-15% time spent generating query, cmp #=(command ...)
  ;;             which leaves about 20% unaccounted-for impact relative to
  ;;             redis-benchmark.
  ;;  * https://github.com/sheki/jedis-benchmark

  ;; TODO Doing something wrong? Why are the Jedis results so bad?

  (let [spec (make-connection-spec)
        pool (make-connection-pool :max-total 5 :test-on-borrow false
                                   :test-on-return false)]
    (println "---")
    (time
     (let [per (int (/ 100000 5)) ; Desired ops / concurrent clients
           op  (fn [] (dotimes [n per]
                       (with-connection pool spec
                         (set "key" "value"))))
           c1 (future (op)) c2 (future (op)) c3 (future (op))
           c4 (future (op)) c5 (future (op))]
       [@c1 @c2 @c3 @c4 @c5]))))

;;;; Dev/tests

(comment
  (def pool (make-connection-pool))
  (def spec (make-connection-spec))
  (def spec (make-connection-spec :host "panga.redistogo.com"
                                  :port 9475
                                  :password "foobar")) ; Remote

  ;;; Protocol
  (set "key" "value") ; "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue"

  ;;; Basic connections
  (def conn (get-conn pool spec))
  (conn-alive? conn)
  (release-conn pool conn) ; Return connection to pool (don't close)
  (close-conn conn) ; Actually close connection

  ;;; Basic requests
  (with-connection pool spec (ping))
  (with-connection pool spec
    (ping)
    (set "key" "value")
    (incrby "key2" 12)
    (get "key")
    (get "key2")
    (set "unicode" "año")
    (get "unicode"))

  (with-connection pool spec "This is invalid") ; Malformed

  ;;; Pub/Sub
  (def listener
    (make-listener
     spec {"foo1"  (fn [x] (println "Channel match: " x))
           "foo*"  (fn [x] (println "Pattern match: " x))
           "extra" (fn [x] (println "EXTRA: " x))}
     (subscribe  "foo1" "foo2")
     (psubscribe "foo*")))

  (do (println "---")
      (with-connection pool spec
        (publish "foo1" "Message to foo1")))

  (with-open-listener listener
    (unsubscribe))

  (close-listener listener))