# Accession

A Clojure library for redis

## Usage
   
Accession is used just like you would use redis from the command
line. All available redis commands will be implemented. For a list
of currently implemented commands see
[core.clj](https://github.com/abedra/accession/blob/master/src/accession/core.clj). The
example below demonstrates standard usage:

    (require '[accession.core :as redis])
	(def c (redis/defconnection {})
    (redis/with-connection c (redis/set "foo" "some value")
    -> "OK"
    (redis/with-connection c (redis/get "foo")
    -> "some value"

This library is targeted at Redis 2.0+. If you are using an older
version of Redis or are using a version of Clojure earlier than 1.3.0,
you may have better luck with
[redis-clojure](https://github.com/ragnard/redis-clojure).

## Contributors

[Brenton Ashworth](http://github.com/brentonashworth)
[Michael Fogus](http://github.com/fogus)

## License

Copyright (C) 2011 Aaron Bedra

Distributed under the Eclipse Public License, the same as Clojure. For
further details, visit the [license
file](https://github.com/abedra/accession/blob/master/epl-v10.html)
