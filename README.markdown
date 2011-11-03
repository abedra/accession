# Accession

A Clojure library for redis

## Usage
   
   Accession is used just like you would use redis from the command
   line. All available redis commands will be implemented. For a list
   of currently implemented commands see core.clj. The example below
   demonstrates standard usage:

    (require '[accession.core :as redis])
    (redis/set "foo" "some value")
    -> "OK"
    (redis/get "foo")


## License

Copyright (C) 2011 Aaron Bedra

Distributed under the Eclipse Public License, the same as Clojure.
