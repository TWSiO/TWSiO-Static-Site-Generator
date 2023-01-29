(ns twsio-stasis.util
  (:require [stasis.core :as stasis])
  (:require [clostache.parser :as stache])
  (:require [markdown.core :as md])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.config :as config])
  )

;; Takes a vec of vec pairs and converts to hashmap
;; Good for output of mapping over hashmap
(defn to-hash-map [ v ]
  (->> v
    (apply concat)
    (apply hash-map)))

(defn trace [msg x]
  (println msg " " x)
  x)

; Copy everything else
(def process-match #"\.md$|\.mustache$")

(defn convert-default-path [path]
  (clojure.string/replace path process-match ".html"))

(def templates
  (stasis/slurp-resources "templates" #"\.mustache$"))

(defn render-template [ template & args ]
  (as-> templates _
    (get _ (str "/" template ".mustache"))
    (apply stache/render (concat [_] args))))

(defn render-default [title, content]
  (render-template
    "default"
    {:title title, :content content}
    {:header (get templates "/header.mustache"), :footer (get templates "/footer.mustache")}))

(defn render-individual [title, content]
  (->> {:content content}
       (render-template "individual_page")
       (render-default title)))

(defn remove-file-extension [ path ]
  (as-> path X
    (re-find #"(.*)\.([^.]*)$" X)
    (get X 1)))

;; Default processing of markdown
(defn process-md [raw-md]
  {:metadata (md/md-to-meta raw-md)
   :content (md/md-to-html-string raw-md :parse-meta? true)
   })

(def markdown-match #"\.md$")

(defn remove-path-artifacts [ extension path ]
  (let [regex (re-pattern (str "^\\/|\\." extension "$"))]
       (clojure.string/replace path regex "")))

(defn render-md-template [ template, raw-content & other-templates ]
  (render-template
    template
    (clojure.set/rename-keys (md/md-to-html-string-with-meta raw-content :parse-meta? true)
                 {:html :content})
    (if (first other-templates) (first other-templates) {})))

(defn render-individual-page-md [ raw-content ]
    (render-default
      (:title (md/md-to-meta raw-content))
      (render-md-template "individual_page" raw-content)))

(defn get-file-extension [ path ]
  (get
    (re-find #"(.*)\.([^.]*)$" path)
    2))

; Get everything, partition based on if we actually want to slurp it, then get paths and copy everything that isn't slurped.
(def raw-contents
  (stasis/slurp-resources "site" #"^site/[^.]+\.[^.]+"))

; Partitions things to blog pages and not blog pages
(defn identify-section [[ path, _ ]]
  (cond
    (re-find (re-pattern (str "^" config/blog-path)) path) :blog
    (= path "/index.mustache") :home
    (re-find process-match path) :other
    :else :file-copy
  ))

(def sectioned-raw-contents
  (as-> raw-contents _
    (group-by identify-section _)
    (medley/map-vals to-hash-map _)
    (to-hash-map _)))
