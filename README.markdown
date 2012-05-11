# Accession [![Build Status](https://secure.travis-ci.org/abedra/accession.png)](http://travis-ci.org/abedra/accession?branch=master)

A *simple*, high-performance Clojure client library for Redis.
Supports Pub/Sub, LUA scripting and easy future extensibility.

## Installing

Simply add Accession to your leiningen project file:

    [ptaoussanis/accession "0.7.0-SNAPSHOT"] ; TODO

## Usage

Accession is used just like you would use Redis from the command
line. All available Redis commands will be implemented. For a list
of currently implemented commands see
[core.clj](https://github.com/abedra/accession/blob/master/src/accession/core.clj).

The example below demonstrates standard usage:

    ;; Make sure to alias accession.core since several clojure.core
    ;; functions are replaced
    (require '[accession.core :as redis])

    ;; Create a connection pool and spec
    (def p (redis/make-connection-pool))
    (def s (redis/make-connection-spec :port 9000 :password "mypassword"))

    ;; Use the connection to run commands against Redis
    (redis/with-connection p s (redis/set "foo" "some value"))
    -> "OK"
    (redis/with-connection p s (redis/get "foo"))
    -> "some value"

    ;; When you run multiple queries, accession will assume
    ;; pipelining. This behavior is subject to change.
    (redis/with-connection p s
        (redis/rpush "children" "A")
        (redis/rpush "children" "B")
        (redis/rpush "children" "C"))
    -> (1 2 3)

	;; You can use the Redis Pub/Sub features
        (def listener (redis/make-listener
                       s {"bar" (fn [x] (prn x))}
                       (psubscribe "b*")
                       (subscribe  "bar")))
        -> #'user/listener

	;; and afterwards adjust subscriptions on the listener
        (redis/with-open-listener listener
          (subscribe "foo" "baz"))

        ;; or modify handlers
        (swap! (:handlers listener) assoc "foo" #(prn %))

	;; You can send messages like so
	(redis/with-connection p s (redis/publish "bar" "Hello bar")
                                   (redis/publish "baz" "Hello baz"))
	-> ("message" "bar" "Hello bar")
       ("message" "baz" "Hello baz")

	;; Again, this API is subject to change.

This library is targeted at Redis 2.0+. If you are using an older
version of Redis or are using a version of Clojure earlier than 1.3.0,
you may have better luck with
[redis-clojure](https://github.com/tavisrudd/redis-clojure).

## Documentation

Marginalia generated documentation can be found at [abedra.github.com/accession](http://abedra.github.com/accession).

## Contributors

[Brenton Ashworth](http://github.com/brentonashworth)

[Michael Fogus](http://github.com/fogus)

[Sebastián Galkin](https://github.com/paraseba)

[Peter Taoussanis](https://github.com/ptaoussanis)

## License

Copyright (C) 2011 Aaron Bedra

Distributed under the Eclipse Public License, the same as Clojure. For
further details, visit the [license
file](https://github.com/abedra/accession/blob/master/epl-v10.html)