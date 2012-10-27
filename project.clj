(defproject accession "0.2.0-snapshot"
  :description "A Clojure library for Redis"
  :url "https://github.com/abedra/accession"
  :license {:name "Eclipse Public License"}
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :profiles {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-beta1"]]}
             :dev {:dependencies [[marginalia "0.7.1" :exclusions [org.clojure/clojure]]
                                  [lein-marginalia "0.7.1" :exclusions [org.clojure/clojure]]
                                  [lein-clojars "0.9.1" :exclusions [org.clojure/clojure]]]}}
  :aliases {"multi-test" ["with-profile" "test,1.3:test,1.4:test,1.5" "test"]}
  :min-lein-version "2.0.0"
  :warn-on-reflection false)