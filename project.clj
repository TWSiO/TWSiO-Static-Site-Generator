(defproject twsio_stasis "0.1.1"
  :description "Static Site Generator for TWSiO"
  :url "thiswebsiteis.online"
  :license {:name "AGPL-3.0-only"
            :url "https://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [org.clojure/clojure "1.10.0"]
                 [ring "1.9.5"]
                 [stasis "2.5.1"]
                 [markdown-clj "1.11.1"]
                 [cljstache "2.0.6"]
                 [medley "1.4.0"]
                 [babashka/fs "0.1.6"]
                 [enlive "1.1.6"]
                 [org.clojure/data.json "2.4.0"]
                 [babashka/fs "0.3.17"]
                 ]
  :main ^:skip-aot twsio-stasis.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-ring "0.12.6"] [cider/cider-nrepl "0.24.0"]]
  :ring {:handler twsio-stasis.core/app}
  )
