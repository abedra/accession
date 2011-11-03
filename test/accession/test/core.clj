(ns accession.test.core
  (:use clojure.test)
  (:require [accession.core :as redis]))

(redis/flushall)

(deftest test-command-construction
  (is (= "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n" (redis/query "set" "foo" "bar")))
  (is (= "*2\r\n$3\r\nGET\r\n$3\r\nbar\r\n" (redis/query "get" "bar"))))

(deftest test-echo
  (is (= "Message" (redis/echo "Message")))
  (is (= "PONG" (redis/ping))))

(deftest test-exists
  (is (= "0" (redis/exists "singularity")))
  (redis/set "singularity" "exists")
  (is (= "1" (redis/exists "singularity"))))

(deftest test-keys
  (is (= (quote ("resource:lock" "singularity")) (redis/keys "*"))))

(deftest test-set-get
  (is (= "OK" (redis/set "server:name" "fido")))
  (is (= "fido" (redis/get "server:name")))
  (is (= "15" (redis/append "server:name" " [shutdown]")))
  (is (= "fido [shutdown]" (redis/getset "server:name" "fido [running]")))
  (is (= "running" (redis/getrange "server:name" "6" "12")))
  (redis/setbit "mykey" "7" "1")
  (is (= "1" (redis/getbit "mykey" "7")))
  (is (= "OK" (redis/set "multiline" "Redis\r\nDemo")))
  (is (= "Redis\r\nDemo" (redis/get "multiline"))))

(deftest test-incr-decr
  (redis/set "connections" "10")
  (is (= "11" (redis/incr "connections")))
  (is (= "20" (redis/incrby "connections" "9")))
  (is (= "19" (redis/decr "connections")))
  (is (= "10" (redis/decrby "connections" "9"))))

(deftest test-del
  ;; This works, but ideally should return true, not "1"
  (redis/set "something" "foo")
  (is (= "1" (redis/del "something"))))

(deftest test-expiry
  (redis/set "resource:lock" "Redis Demo")
  (is (= "1" (redis/expire "resource:lock" "120")))
  (is (< 0 (Integer/parseInt (redis/ttl "resource:lock"))))
  (is (= "-1" (redis/ttl "count"))))

(deftest test-lists
  (redis/rpush "friends" "Tom")
  (redis/rpush "friends" "Bob")
  (redis/lpush "friends" "Sam")
  (is (= (quote ("Sam" "Tom" "Bob"))
         (redis/lrange "friends" "0" "-1")))
  (is (= (quote ("Sam" "Tom"))
         (redis/lrange "friends" "0" "1")))
  (is (= (quote ("Sam" "Tom" "Bob"))
         (redis/lrange "friends" "0" "2")))
  (is (= "3" (redis/llen "friends")))
  (is (= "Sam" (redis/lpop "friends")))
  (is (= "Bob" (redis/rpop "friends")))
  (is (= "1" (redis/llen "friends")))
  (is (= "Tom")) (redis/lrange "friends" "0" "-1")
  (redis/del "friends"))

(deftest test-hashes
  (redis/hset "myhash" "field1" "value1")
  (is (= "value1" (redis/hget "myhash" "field1")))
  (redis/hsetnx "myhash" "field1" "newvalue")
  (is (= "value1" (redis/hget "myhash" "field1")))
  (is (= "1" (redis/hexists "myhash" "field1")))
  (is (= (quote ("field1" "value1")) (redis/hgetall "myhash")))
  (redis/hset "myhash" "field2" "1")
  (is (= "3" (redis/hincrby "myhash" "field2" "2")))
  (is (= (quote ("field1" "field2")) (redis/hkeys "myhash")))
  (is (= (quote ("value1" "3")) (redis/hvals "myhash")))
  (is (= "2" (redis/hlen "myhash")))
  (redis/hdel "myhash" "field1")
  (is (= "0" (redis/hexists "myhash" "field1"))))

(deftest test-sets
  (redis/sadd "superpowers" "flight")
  (redis/sadd "superpowers" "x-ray vision")
  (redis/sadd "superpowers" "reflexes")
  (redis/srem "superpowers" "reflexes")
  (is (= "1" (redis/sismember "superpowers" "flight")))
  (is (= "0" (redis/sismember "superpowers" "reflexes")))
  (redis/sadd "birdpowers" "pecking")
  (redis/sadd "birdpowers" "flight")
  (is (= (quote ("pecking" "x-ray vision" "flight"))
         (redis/sunion "superpowers" "birdpowers"))))

(deftest test-sorted-sets
  (redis/zadd "hackers" "1940" "Alan Kay")
  (redis/zadd "hackers" "1953" "Richard Stallman")
  (redis/zadd "hackers" "1965" "Yukihiro Matsumoto")
  (redis/zadd "hackers" "1916" "Claude Shannon")
  (redis/zadd "hackers" "1969" "Linus Torvalds")
  (redis/zadd "hackers" "1912" "Alan Turning")
  (is (= (quote ("Alan Kay" "Richard Stallman" "Yukihiro Matsumoto"))
         (redis/zrange "hackers" "2" "4"))))

(deftest test-dbsize
  (redis/flushdb)
  (redis/set "something" "with a value")
  (is (= "1" (redis/dbsize)))
  (redis/flushall)
  (is (= "0" (redis/dbsize))))
