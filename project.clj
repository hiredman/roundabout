(defproject com.manigfeald/roundabout "0.1.0"
  :description "A library for flow control"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.3.443"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [org.clojure/clojurescript "1.7.170"]]}
             :clj19 {:dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                                    [org.clojure/spec.alpha "0.1.108"]]}}
  :cljsbuild {:builds [{:source-paths ["src"]
                        :compiler {:output-to "main.js"
                                   :optimizations :none
                                   :pretty-print true}}]}
  :plugins [[lein-cljsbuild "1.1.1"]])
