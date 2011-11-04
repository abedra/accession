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

(defmacro defquery
  [name params]
  (let [command (str name)]
    `(defn ~name ~params
       (query ~command ~@params))))

(defmacro defqueries
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
  (mget             [key])
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