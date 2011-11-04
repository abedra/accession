(ns accession.test.core
  (:use clojure.test)
  (:require [accession.core :as redis]
            [accession.protocol :as protocol]))

(def c (redis/defconnection {}))

(redis/with-connection c (redis/flushall))

(deftest test-command-construction
  (is (= "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n" (protocol/query "set" "foo" "bar")))
  (is (= "*2\r\n$3\r\nGET\r\n$3\r\nbar\r\n" (protocol/query "get" "bar"))))

(deftest test-echo
  (is (= "Message" (redis/with-connection c (redis/echo "Message"))))
  (is (= "PONG" (redis/with-connection c (redis/ping)))))

(deftest test-exists
  (is (= 0 (redis/with-connection c (redis/exists "singularity"))))
  (redis/with-connection c (redis/set "singularity" "exists"))
  (is (= 1 (redis/with-connection c (redis/exists "singularity")))))

(deftest test-keys
  (is (= (quote ("resource:lock" "singularity"))
         (redis/with-connection c (redis/keys "*")))))

(deftest test-set-get
  (is (= "OK"
         (redis/with-connection c (redis/set "server:name" "fido"))))
  (is (= "fido"
         (redis/with-connection c (redis/get "server:name"))))
  (is (= 15
         (redis/with-connection c (redis/append "server:name" " [shutdown]"))))
  (is (= "fido [shutdown]"
         (redis/with-connection c (redis/getset "server:name" "fido [running]"))))
  (is (= "running"
         (redis/with-connection c (redis/getrange "server:name" "6" "12"))))
  (redis/with-connection c (redis/setbit "mykey" "7" "1"))
  (is (= 1
         (redis/with-connection c (redis/getbit "mykey" "7"))))
  (is (= "OK"
         (redis/with-connection c (redis/set "multiline" "Redis\r\nDemo"))))
  (is (= "Redis\r\nDemo"
         (redis/with-connection c (redis/get "multiline")))))

(deftest test-incr-decr
  (redis/with-connection c (redis/set "connections" "10"))
  (is (= 11
         (redis/with-connection c (redis/incr "connections"))))
  (is (= 20
         (redis/with-connection c (redis/incrby "connections" "9"))))
  (is (= 19
         (redis/with-connection c (redis/decr "connections"))))
  (is (= 10
         (redis/with-connection c (redis/decrby "connections" "9")))))

(deftest test-del
  (redis/with-connection c (redis/set "something" "foo"))
  (is (= 1
         (redis/with-connection c (redis/del "something")))))

(deftest test-expiry
  (redis/with-connection c (redis/set "resource:lock" "Redis Demo"))
  (is (= 1
         (redis/with-connection c (redis/expire "resource:lock" "120"))))
  (is (< 0
         (redis/with-connection c (redis/ttl "resource:lock"))))
  (is (= -1
         (redis/with-connection c (redis/ttl "count")))))

(deftest test-lists
  (redis/with-connection c (redis/rpush "friends" "Tom"))
  (redis/with-connection c (redis/rpush "friends" "Bob"))
  (redis/with-connection c (redis/lpush "friends" "Sam"))
  (is (= (quote ("Sam" "Tom" "Bob"))
         (redis/with-connection c (redis/lrange "friends" "0" "-1"))))
  (is (= (quote ("Sam" "Tom"))
         (redis/with-connection c (redis/lrange "friends" "0" "1"))))
  (is (= (quote ("Sam" "Tom" "Bob"))
         (redis/with-connection c (redis/lrange "friends" "0" "2"))))
  (is (= 3
         (redis/with-connection c (redis/llen "friends"))))
  (is (= "Sam"
         (redis/with-connection c (redis/lpop "friends"))))
  (is (= "Bob"
         (redis/with-connection c (redis/rpop "friends"))))
  (is (= 1
         (redis/with-connection c (redis/llen "friends"))))
  (is (= (quote ("Tom"))
         (redis/with-connection c (redis/lrange "friends" "0" "-1"))))
  (redis/with-connection c (redis/del "friends")))

(deftest test-hashes
  (redis/with-connection c (redis/hset "myhash" "field1" "value1"))
  (is (= "value1"
         (redis/with-connection c (redis/hget "myhash" "field1"))))
  (redis/with-connection c (redis/hsetnx "myhash" "field1" "newvalue"))
  (is (= "value1"
         (redis/with-connection c (redis/hget "myhash" "field1"))))
  (is (= 1
         (redis/with-connection c (redis/hexists "myhash" "field1"))))
  (is (= (quote ("field1" "value1"))
         (redis/with-connection c (redis/hgetall "myhash"))))
  (redis/with-connection c (redis/hset "myhash" "field2" "1"))
  (is (= 3
         (redis/with-connection c (redis/hincrby "myhash" "field2" "2"))))
  (is (= (quote ("field1" "field2"))
         (redis/with-connection c (redis/hkeys "myhash"))))
  (is (= (quote ("value1" "3"))
         (redis/with-connection c (redis/hvals "myhash"))))
  (is (= 2
         (redis/with-connection c (redis/hlen "myhash"))))
  (redis/with-connection c (redis/hdel "myhash" "field1"))
  (is (= 0
         (redis/with-connection c (redis/hexists "myhash" "field1")))))

(deftest test-sets
  (redis/with-connection c (redis/sadd "superpowers" "flight"))
  (redis/with-connection c (redis/sadd "superpowers" "x-ray vision"))
  (redis/with-connection c (redis/sadd "superpowers" "reflexes"))
  (redis/with-connection c (redis/srem "superpowers" "reflexes"))
  (is (= 1
         (redis/with-connection c (redis/sismember "superpowers" "flight"))))
  (is (= 0
         (redis/with-connection c (redis/sismember "superpowers" "reflexes"))))
  (redis/with-connection c (redis/sadd "birdpowers" "pecking"))
  (redis/with-connection c (redis/sadd "birdpowers" "flight"))
  (is (= (quote ("pecking" "x-ray vision" "flight"))
         (redis/with-connection c (redis/sunion "superpowers" "birdpowers")))))

(deftest test-sorted-sets
  (redis/with-connection c (redis/zadd "hackers" "1940" "Alan Kay"))
  (redis/with-connection c (redis/zadd "hackers" "1953" "Richard Stallman"))
  (redis/with-connection c (redis/zadd "hackers" "1965" "Yukihiro Matsumoto"))
  (redis/with-connection c (redis/zadd "hackers" "1916" "Claude Shannon"))
  (redis/with-connection c (redis/zadd "hackers" "1969" "Linus Torvalds"))
  (redis/with-connection c (redis/zadd "hackers" "1912" "Alan Turning"))
  (is (= (quote ("Alan Kay" "Richard Stallman" "Yukihiro Matsumoto"))
         (redis/with-connection c (redis/zrange "hackers" "2" "4")))))

(deftest test-dbsize
  (redis/with-connection c (redis/flushdb))
  (redis/with-connection c (redis/set "something" "with a value"))
  (is (= 1
         (redis/with-connection c (redis/dbsize))))
  (redis/with-connection c (redis/flushall))
  (is (= 0
         (redis/with-connection c (redis/dbsize)))))

(redis/with-connection c (redis/flushall))