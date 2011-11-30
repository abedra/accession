# Accession [![Build Status](https://secure.travis-ci.org/abedra/accession.png)](http://travis-ci.org/abedra/accession?branch=master)

A Clojure library for redis (current version 0.1.1)

## Installing

Simply add Accession to your leiningen project file:

    [accession "0.1.1"]

## Usage
   
Accession is used just like you would use redis from the command
line. All available redis commands will be implemented. For a list
of currently implemented commands see
[core.clj](https://github.com/abedra/accession/blob/master/src/accession/core.clj). The
example below demonstrates standard usage:

    ;; Make sure to alias accession.core since several clojure.core
    ;; functions are replaced
    (require '[accession.core :as redis])
     
    ;; Create a connection map
    (def c (redis/connection-map {}))
     
    ;; Use the connection to run commands against redis
    (redis/with-connection c (redis/set "foo" "some value"))
    -> "OK"
    (redis/with-connection c (redis/get "foo"))
    -> "some value"
     
    ;; When you run multiple queries, accession will assume
    ;; pipelining. This behavior is subject to change.
    (redis/with-connection c
        (redis/rpush "children" "A")
        (redis/rpush "children" "B")
        (redis/rpush "children" "C"))
    -> (1 2 3)
	
	;; You can use the Redis pub/sub features 
	(def channel (redis/subscribe c {"bar" (fn [x] (prn x))}))
	-> ("subscribe" "bar" 1)
	
	;; and afterwards add more consumers to the channel
	(redis/subscribe channel {"baz" #(prn %)})
	-> ("subscribe" "baz" 2)
	
	;; You can send messages like so
	(redis/with-connection c (redis/publish "bar" "Hello bar") 
	                         (redis/publish "baz" "Hello baz"))
	-> ("message" "bar" "Hello bar")
       ("message" "baz" "Hello baz")
	
	;; Again, this API is subject to change.

This library is targeted at Redis 2.0+. If you are using an older
version of Redis or are using a version of Clojure earlier than 1.3.0,
you may have better luck with
[redis-clojure](https://github.com/ragnard/redis-clojure).

## Documentation

Marginalia generated documentation can be found at [abedra.github.com/accession](http://abedra.github.com/accession).

## Contributors

[Brenton Ashworth](http://github.com/brentonashworth)

[Michael Fogus](http://github.com/fogus)

[Sebastián Galkin](https://github.com/paraseba)

## License

Copyright (C) 2011 Aaron Bedra

Distributed under the Eclipse Public License, the same as Clojure. For
further details, visit the [license
file](https://github.com/abedra/accession/blob/master/epl-v10.html)
