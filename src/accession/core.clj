; Copyright (c) Aaron Bedra. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license. You must not remove this notice, or any other,
; from this software.

(ns accession.core
  (:refer-clojure :exclude [get set keys type sync])
  (:use [accession.protocol :only (query)]
        [accession.request :only (request)]
        [accession.connection :only (connection)]))

(defmacro defconnection
  "Creates a socket with the given connection info in the form of:

       {:host hostname
        :port portnumber
        :password password}

   If an empty map is provided, localhost defaults will be used"
  [spec]
  `(connection ~spec))

(defmacro with-connection
  "Responsible for calling the request function and providing the
  connection details"
  [connection & body]
  `(request ~connection ~@body))

;; We would like to create one function for each command which Redis
;; supports. The set function would look something like this:
;;
;;    (defn set [key value]
;;      (request (query "set" key value)))
;;
;; Similarly, the get function would be:
;;
;;     (defn get [key]
;;       (request (query "get" key)))
;;
;; Because each of these functions has the same pattern, we can use a
;; macro to create them and save a lot of typing.

(defn parameters
  "This function enables vararg style definitions in the queries. For
  example you can say:

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
         (request (query <command> <p1> <p2> ... <pN>)))

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

(defmacro defqueries
  "Given any number of redis commands and argument lists, convert them
   to function definitions.

   A call to:

       (defqueries (set [key value]) (get [key]))

   will expand to:

       (do
         (defn set [key value]
           (request (query \"set\" key value)))
         (defn get [key]
           (request (query \"get\" key))))

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
  (object           [subcommand])
  
  (set              [key value])
  (setex            [key seconds value])
  (setnx            [key value])
  (setbit           [key offset value])
  (mset             [key value])
  (msetnx           [key value])
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
  (watch            [key])
  (unwatch          [key])
  (del              [key])
  ;; (sort     [])

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
  (lpushx           [key value])
  (lset             [key value index])
  (linsert          [key before-after pivot value])
  (lrem             [key count value])
  (ltrim            [key start stop])
  (lrange           [key start end])
  (lindex           [key index])
  (rpop             [key])
  (rpush            [key value])
  (rpushx           [key value])
  (rpoplpush        [source destination])
  (blpop            [key timeout])
  (brpop            [key timeout])
  (brpoplpush       [source destination timeout])

  (hset             [key field value])
  (hmset            [key field value])
  (hsetnx           [key field value])
  (hget             [key field])
  (hmget            [key field])
  (hexists          [key field])
  (hdel             [key field])
  (hgetall          [key])
  (hincrby          [key field increment])
  (hkeys            [key])
  (hvals            [key])
  (hlen             [key])
  
  (sadd             [key member])
  (srem             [key member])
  (sismember        [key member])
  (smembers         [key])
  (sunion           [set1 set2])
  (sunionstore      [destination set1 set2])
  (sdiff            [set1 set2])
  (sdiffstore       [destination set1 set2])
  (sinter           [set1 set2])
  (sinterstore      [destination set1 set2])
  (scard            [key])
  (smove            [source desination member])
  (spop             [key])
  (srandmember      [key])

  (zadd             [key score member])
  (zrange           [key start end])
  (zcard            [key])
  (zcount           [key min max])
  (zincrby          [key increment member])
  (zinterstore      [destination numkeys set1 set2])
  (zrange           [key start stop])
  (zrank            [member])
  (zrem             [key member])
  (zremrangebyrank  [key start stop])
  (zremrangebyscore [key min max])
  (zrevrank         [key member])
  (zcore            [key member])
  (zunionstore      [destination numkeys set1 set2])
  ;; (zrevrange)
  ;; (zrevrangebyscore)
  ;; (zrange)
  ;; (zrangebyscore)

  (publish          [channel message])
  (subscribe        [channel])
  (unsubscribe      [channel])
  (punsubscribe     [pattern])
  (psubscribe       [pattern]))
