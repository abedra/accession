; Copyright (c) Aaron Bedra. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license. You must not remove this notice, or any other,
; from this software.

(ns accession.core
  (:refer-clojure :exclude [get set keys type sync sort])
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.net Socket)
           (java.io BufferedInputStream DataInputStream)))

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
                 (map (fn [a] (str "$" (count (.getBytes (str a))) "\r\n" a))
                      args))
       "\r\n"))
;; <pre>"*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n"</pre>

;; These protocols were created to support pub/sub, in particular,
;; subscribe. Subscribe is called in terms of both a connection-map
;; and an open channel. This is not implemented via the macro because
;; the semantics are so much different from that of the standard Redis
;; calls. Since channels and subscriptions live longer than a single
;; query, we needed a way to keep a connection open and close it later
;; when we are finished.
(defprotocol ISubscribable
  (subscribe [this channels])
  #_(psubscribe [this channels]))

(defprotocol IUnsubscribable
  (unsubscribe [this channels])
  #_(punsubscribe [this channels]))

(defprotocol IRedisChannel
  (close [this]))

(defmulti response
  "Redis will reply to commands with different kinds of replies. It is
  possible to check the kind of reply from the first byte sent by the
  server:

  * With a single line reply the first byte of the reply will be `+`
  * With an error message the first byte of the reply will be `-`
  * With an integer number the first byte of the reply will be `:`
  * With bulk reply the first byte of the reply will be `$`
  * With multi-bulk reply the first byte of the reply will be `*`"
  (fn [in] (char (.readByte in))))

(defmethod response \- [in]
  (.readLine in))

(defmethod response \+ [in]
  (.readLine in))

(defmethod response \$ [in]
  (let [length (Integer/parseInt (.readLine in))]
    (when (not= length -1)
      (let [content (byte-array (+ 2 length))]
        (.read in content)
        (String. content 0 length)))))

(defmethod response \: [in]
  (Long/parseLong (.readLine in)))

(defmethod response \* [in]
  (let [length (Integer/parseInt (.readLine in))]
    (doall (repeatedly length #(response in)))))

(defn- socket
  "Creates an initial socket for consumption attached to the proper
  host and port. This socket is subject to modification later depending
  on the request use case or if a timeout is set."
  [spec]
  (doto (Socket. (:host spec) (:port spec))
    (.setTcpNoDelay true)
    (.setKeepAlive true)))

(defn- socket-and-streams
  [spec]
  (let [socket (doto (socket spec) (.setSoTimeout (:timeout spec)))
        in (DataInputStream. (BufferedInputStream. (.getInputStream socket)))
        out (.getOutputStream socket)]
    [socket in out spec]))

(defn- close-socket-and-streams
  [[socket in out _]]
  (.close socket) (.close in) (.close out))

(def socket-atom (atom {}))

(defn reset-sockets! []
  (swap! socket-atom (fn [hash]
                       (doseq [s-and-s (map deref (vals hash))]
                         (close-socket-and-streams s-and-s))
                       {})))

(defn- socket-agent
  [spec]
  (when (not (@socket-atom spec))
    (swap! socket-atom #(assoc % spec (agent (socket-and-streams spec)))))
  (@socket-atom spec))

(defn request
  "Responsible for actually making the request to the Redis
  server. Sets the timeout on the socket if one was specified.

Uses a long lived open socket owned by an agent to execute the request.
If the socket throws an exception reading or writing, close it and start
a new one but do not retry the query.

Throwables thrown in the agent will be manually rethrown in the caller
thread."
  [conn & query]
  (let [p (promise)]
    (send (socket-agent conn)
          (fn [[socket in out spec :as s-and-s]]
            (try
              (.write out (.getBytes (apply str query)))
              (deliver p (if (next query)
                           (doall (repeatedly (count query) #(response in)))
                           (response in)))
              s-and-s
              (catch Throwable e
                (deliver p e) (close-socket-and-streams s-and-s)
                (socket-and-streams spec)))))
    (let [result (deref p)]
      ;; this seems potentially slow due to reflection - benchmark, maybe use protocol
      (if (instance? Throwable result)
        (throw result)
        result))))

(defn receive-message
  "Used in conjunction with an open channel to handle messages that
  arrive. Takes a channel spec and a message"
  [channel-spec in]
  (let [next-message (response in)
        channel-name (second next-message)]
    (if-let [f (clojure.core/get @channel-spec channel-name)]
      (f next-message))))

(defn write-commands
  "Sends commands out to the specified channels "
  [out command channels]
  (.write out (.getBytes (apply query command (clojure.core/keys channels)))))

;; This record implements the protocols defined above to provide the
;; pub/sub infrastructure
(defrecord RedisChannel [channel-fns socket out]
  ISubscribable
  (subscribe [this channels]
    (do (swap! channel-fns merge channels)
        (write-commands out "subscribe" channels)))
  IUnsubscribable
  (unsubscribe [this channel]
    (do (swap! channel-fns dissoc channel)
        (.write out (.getBytes (query "unsubscribe" channel)))))
  IRedisChannel
  (close [this]
    (.close socket)))

(defn open-channel
  "Takes a connection and sets up a connection to a Redis channel with
  the provided functions as callbacks to invoke when messages are
  received"
  [conn command channels]
  (let [socket (socket conn)
        in (.getInputStream socket)
        out (.getOutputStream socket)
        in (DataInputStream. (BufferedInputStream. in))
        channel-fns (atom channels)]
    (write-commands out command channels)
    (future (doall (repeatedly #(receive-message channel-fns in))))
    (RedisChannel. channel-fns socket out)))

(extend-type clojure.lang.PersistentArrayMap
  ISubscribable
  (subscribe [this channels]
    (open-channel this "subscribe" channels)))

(defn connection-map
  "Creates the initial connection spec. Options can be overridden by
  passing in a map. The following keys are valid:

  :host
  :port
  :password
  :socket
  :timeout

  Although passing in your own socket is not recommended and will
  probably cause more problems than it solves"
  ([] (connection-map {}))
  ([spec]
     (let [default {:host "127.0.0.1"
                    :port 6379
                    :password nil
                    :socket nil
                    :timeout 0}]
       (merge default spec))))

(defn with-connection [spec & body]
  (apply request spec body))

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
  that string as the command name.

  params is a list of N symbols which represent the parameters to the
  function. We use this list as the parameter-list when we create the
  function. Each symbol in this list will be an argument to query
  after the command. We use unquote splicing (~@) to insert these
  arguments after the command string."
  [name params]
  (let [command (str name)
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
  ;; (config-get [parameter])
  ;; (config-set [parameter value])
  ;; (config-resetstat [])
  ;; (debug-object [key])
  ;; (debug-segfault)

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
  (sort             [key & options])

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
  (zrange           [key start end])
  (zrangebyscore    [key min max & more])
  (zrevrange        [key start stop & more])
  (zrevrangebyscore [key max min & more])
  (zcard            [key])
  (zcount           [key min max])
  (zincrby          [key increment member])
  (zrange           [key start stop])
  (zrank            [member])
  (zrem             [key member & members])
  (zremrangebyrank  [key start stop])
  (zremrangebyscore [key min max])
  (zrevrank         [key member])
  (zscore           [key member])

  (publish          [channel message]))

(defn zinterstore
  [dest-key source-keys & options]
  (apply query "zinterstore" dest-key
         (count source-keys) (concat source-keys options)))

(defn zunionstore
  [dest-key source-keys & options]
  (apply query "zunionstore" dest-key
         (count source-keys) (concat source-keys options)))

