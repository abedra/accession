(ns accession.test.core
  (:use clojure.test)
  (:require [accession.core :as redis]))

(deftest test-command-construction
  (is (= "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n" (redis/query "set" "foo" "bar")))
  (is (= "*2\r\n$3\r\nGET\r\n$3\r\nbar\r\n" (redis/query "get" "bar"))))

(deftest test-set-and-get
  (is (= "OK" (redis/set "server:name" "fido")))
  (is (= "fido" (redis/get "server:name"))))

(deftest test-incr
  (redis/set "connections" "10")
  (is (= "11" (redis/incr "connections"))))

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