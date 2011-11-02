(ns accession.test.core
  (:use clojure.test
        accession.core))

(deftest test-command-construction
  (is (= "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n" (command "set" "foo" "bar")))
  (is (= "*2\r\n$3\r\nGET\r\n$3\r\nbar\r\n" (command "get" "bar"))))

(deftest test-set-and-get
  (testing "setting a key returns a response of 'OK'"
    (is (= "OK" (send-command (command "set" "server:name" "fido")))))
  (testing "getting a key returns the value of the key"
    (is (= "fido" (send-command (command "get" "server:name"))))))

(deftest test-incr
  (send-command (command "set" "connections" "10"))
  (is (= "11" (send-command (command "incr" "connections")))))

(deftest test-del
  ;; This works, but ideally should return true, not "1"
  (send-command (command "set" "something" "foo"))
  (is (= "1" (send-command (command "del" "something")))))

(deftest test-expiry
  (send-command (command "set" "resource:lock" "Redis Demo"))
  (is (= "1" (send-command (command "expire" "resource:lock" "120"))))
  (is (< 0 (Integer/parseInt (send-command (command "ttl" "resource:lock")))))
  (is (= "-1" (send-command (command "ttl" "count")))))

(deftest test-lists
  (send-command (command "rpush" "friends" "Tom"))
  (send-command (command "rpush" "friends" "Bob"))
  (send-command (command "lpush" "friends" "Sam"))
  (is (= (quote ("Sam" "Tom" "Bob")) (send-command (command "lrange" "friends" "0" "-1"))))
  (is (= (quote ("Sam" "Tom")) (send-command (command "lrange" "friends" "0" "1"))))
  (is (= (quote ("Sam" "Tom" "Bob")) (send-command (command "lrange" "friends" "0" "2"))))
  (is (= "3" (send-command (command "llen" "friends"))))
  (is (= "Sam" (send-command (command "lpop" "friends"))))
  (is (= "Bob" (send-command (command "rpop" "friends"))))
  (is (= "1" (send-command (command "llen" "friends"))))
  (is (= "Tom")) (send-command (command "lrange" "friends" "0" "-1"))
  (send-command (command "del" "friends")))

(deftest test-sets
  (send-command (command "sadd" "superpowers" "flight"))
  (send-command (command "sadd" "superpowers" "x-ray vision"))
  (send-command (command "sadd" "superpowers" "reflexes"))
  (send-command (command "srem" "superpowers" "reflexes"))
  (is (= "1" (send-command (command "sismember" "superpowers" "flight"))))
  (is (= "0" (send-command (command "sismember" "superpowers" "reflexes"))))
  (send-command (command "sadd" "birdpowers" "pecking"))
  (send-command (command "sadd" "birdpowers" "flight"))
  (is (= (quote ("pecking" "x-ray vision" "flight"))
         (send-command (command "sunion" "superpowers" "birdpowers")))))

(deftest test-sorted-sets
  (send-command (command "zadd" "hackers" "1940" "Alan Kay"))
  (send-command (command "zadd" "hackers" "1953" "Richard Stallman"))
  (send-command (command "zadd" "hackers" "1965" "Yukihiro Matsumoto"))
  (send-command (command "zadd" "hackers" "1916" "Claude Shannon"))
  (send-command (command "zadd" "hackers" "1969" "Linus Torvalds"))
  (send-command (command "zadd" "hackers" "1912" "Alan Turning"))
  (is (= (quote ("Alan Kay" "Richard Stallman" "Yukihiro Matsumoto"))
         (send-command (command "zrange" "hackers" "2" "4")))))