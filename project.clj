(defproject twsio_stasis "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [
                 [org.clojure/clojure "1.10.0"]
                 [ring "1.9.5"]
                 [stasis "2.5.1"]
                 [markdown-clj "1.11.1"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [medley "1.4.0"]
                 [babashka/fs "0.1.6"]
                 ]
  :main ^:skip-aot twsio-stasis.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-ring "0.12.6"] [cider/cider-nrepl "0.24.0"]]
  :ring {:handler twsio-stasis.core/app}
  )
