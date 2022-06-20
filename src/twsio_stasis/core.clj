(ns twsio-stasis.core
  (:require [markdown.core :as md])
  (:require [clostache.parser :as stache])
  (:require [stasis.core :as stasis])
  (:require [babashka.fs :as fs])
  (:require [medley.core :as util]))
;  (:gen-class))

;------- Utils

;; Takes a vec of vec pairs and converts to hashmap
;; Good for output of mapping over hashmap
(defn to-hash-map [ v ]
  (->> v
    (apply concat)
    (apply hash-map)))

(defn trace [msg x]
  (println msg " " x)
  x)

;=== Configs ===

;; Was going to use stasis way, but then can't view page output with conjure.

(def blog-path "/blog/")

(def target-dir "target")

(def resources-path "resources")

(def site-path (str resources-path "/site"))

(def html-page-titles
  {"/index.mustache" "This Website is Online"
   (str blog-path "index.html") "Blog home"
   })

;=== General functionality ===

(def markdown-match #"\.md$")

(defn remove-path-artifacts [ extension path ]
  (let [regex (re-pattern (str "^\\/|\\." extension "$"))]
       (clojure.string/replace path regex "")))

; Copy everything else
(def process-match #"\.md$|\.mustache$")

(defn convert-default-path [path] (clojure.string/replace path process-match ".html"))

; Get everything, partition based on if we actually want to slurp it, then get paths and copy everything that isn't slurped.
(def raw-contents (stasis/slurp-resources "site" #"[^.]+\.[^.]+"))

; Partitions things to blog pages and not blog pages
(defn identify-section [[ path, _ ]]
  (cond
    (re-find (re-pattern (str "^" blog-path)) path) :blog
    (= path "/index.mustache") :home
    (re-find process-match path) :other
    :else :file-copy
  ))

(def sectioned-raw-contents
  (to-hash-map
    (util/map-vals
      to-hash-map
      (group-by
        identify-section
        raw-contents))))

(def templates (stasis/slurp-resources "templates" #"\.mustache$"))

(defn render-template [ template & args ]
  (as-> templates _
    (get _ (str "/" template ".mustache"))
    (apply stache/render (concat [_] args))))

;; Default processing of markdown
(defn process-md [raw-md]
  {:metadata (md/md-to-meta raw-md)
   :content (md/md-to-html-string raw-md :parse-meta? true)
   })

(defn render-default [title, content]
  (render-template
    "default"
    {:title title, :content content}
    {:header (get templates "/header.mustache"), :footer (get templates "/footer.mustache")}))

(defn render-individual [title, content]
  (->> {:content content}
       (render-template "individual_page")
       (render-default title)))

(defn get-file-extension [ path ] (get (re-find #"(.*)\.([^.]*)$" path) 2))
(defn remove-file-extension [ path ]
  (get
    (re-find
      #"(.*)\.([^.]*)$"
      path)
    1))

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

;=== Blog ===

(def parsed-posts
  (util/map-vals
    #(md/md-to-html-string-with-meta % :parse-meta? true)
    (:blog sectioned-raw-contents)))

(def default-post-metadata
  {:title nil
   :subtitle nil
   })

(defn process-post [[path, {content :html, metadata :metadata}]]
  [path
   {:html content
    :metadata 
    (as-> metadata m
      (merge default-post-metadata m)
      (assoc m :post-url (convert-default-path path))
      ;(assoc m (:title (first (:title m))))))
      (->> m :date first (assoc m :date))
      (->> m :title first (assoc m :title)))
    }])

(defn get-post-date [[_, {{date :date} :metadata :as all}]]
  (if (some? date) 
    (as-> "yyyy-MM-dd" |
      (java.text.SimpleDateFormat. |)
      (.parse | date))))

(def processed-posts
  (reverse
    (sort-by
      get-post-date
      (map
        process-post 
        parsed-posts))))

(defn get-metadata [[path, {metadata :metadata}]] metadata)

(defn create-post-page [[path, {{title :title} :metadata :as data}]]
    [(convert-default-path path)
     (->> data (render-template "post") (render-default title))
     ])

(def post-pages
  (->> processed-posts
       (map create-post-page)
       to-hash-map
       ))

(def blog-page
  (let [path (str blog-path "index.html")]
    (as-> processed-posts thread
         (map get-metadata thread)
         (render-template "blog" {:posts thread})
         (render-individual (get html-page-titles path) thread)
         {path thread})))

;=== Individual pages ===

(defn process-other-page [[path, contents]]
  (case (get-file-extension path)
    "md" { (convert-default-path path),
          (render-individual-page-md contents)
          }
    "mustache" { (convert-default-path path),
                (render-individual
                  (get html-page-titles path)
                  contents
                  )}
    ))

(def other-pages
  (apply merge
    (map
      process-other-page
      (:other
        sectioned-raw-contents
        ))))

;First do special stuff for home page, then render-individual
(def home-page
  (as-> processed-posts thread
    (take 3 thread)
    (map get-metadata thread)
    (render-template "home" {:posts thread})
    (render-individual "Home" thread)))

;=== Copy other files ===

;; Has to come after stasis stuff
;; slurp-dir doesn't work with ttf and non-text files.
(defn copy-other-files []
  (fs/copy-tree (str resources-path "/fonts") (str target-dir "/assets/fonts"))
  )

;======

;; I gathered these all here, but in hindsight, I think adding to it gradually was better. It's not as if the order matters.
;; I guess it sort of gets clutter out of the way, but it also kind of marks the different "sections" of the script.
(def output-files
  (merge
    post-pages
    other-pages
    blog-page
    {"/index.html" home-page}
    (:file-copy sectioned-raw-contents)
    ))

;; Potentially get from JSON or something.
(def config
  {:test-mode true
   })

;(defn get-pages [] output-files)

;(def app (stasis/serve-pages get-pages))

(def app (stasis/serve-pages output-files))

(defn -main
  "Generate the website"
  [& args]
  (let [pages output-files]
    (stasis/empty-directory! target-dir)
    (stasis/export-pages pages target-dir config))
  (copy-other-files))
