(ns twsio-stasis.core
  (:require [markdown.core :as md])
  (:require [clostache.parser :as stache])
  (:require [stasis.core :as stasis])
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

(def html-page-titles
  {"/index.mustache" "This Website is Online"
   (str blog-path "index.html") "Blog home"
   })

;-------- file matchers
; keys to file contents.
; Gets the initial file contents.
; Typically { :key { "filename/path" content } }

(def blog-path "/blog/")

(def target-dir "target")

(def resources-path "resources/")

(def site-path (str resources-path "site/"))

(def markdown-match #"\.md$")

(defn remove-path-artifacts [ extension path ]
  (let [regex (re-pattern (str "^\\/|\\." extension "$"))]
       (clojure.string/replace path regex "")))

; Copy everything else
(def process-match #"\.md$|\.mustache$")

(defn convert-default-path [path] (clojure.string/replace path process-match ".html"))

;; Need to recurse for more directories
;(def raw-contents (stasis/slurp-directory site-path #"^/.*.$"))
(def raw-contents (stasis/slurp-resources "site" #"[^.]+\.[^.]+"))

; Partitions things to blog pages and not blog pages
(defn identify-section [[ path, _ ]]
  (cond
    (re-find (re-pattern (str "^" blog-path)) path) :blog
    (re-find process-match path) :other
    :else :copy
  ))

(def sectioned-raw-contents
  (to-hash-map
    (util/map-vals to-hash-map
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

;--------- Random pages

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

(def processed-posts
  (util/map-vals
    #(md/md-to-html-string-with-meta % :parse-meta? true)
    (:blog sectioned-raw-contents)))

(def default-post-metadata
  {:title nil
   :subtitle nil
   })

(defn create-post-page [[path {:keys [html, metadata]}]]
  (let [final-meta (merge default-post-metadata metadata)
        title (->> final-meta :title first)
        subtitle (:subtitle final-meta)
        stache-data {:post html, :title title, :subtitle subtitle}
        ]
    [
     (convert-default-path path)
     (->> stache-data (render-template "post") (render-default title))
     ]))

(def post-pages
  (let [ posts-list (map create-post-page processed-posts) ]
    (->> posts-list
         (apply concat)
         (apply hash-map)
         )))

(defn create-post-listing [[path, {metadata :metadata}]]
  (as-> metadata m
    (merge default-post-metadata m)
    (assoc m :post-url (convert-default-path path))
    ;(assoc m (:title (first (:title m))))))
    (->> m :title first (assoc m :title))))

(defn get-post-date [[_, {{[date] :date} :metadata}]]
  (if (some? date) 
    (as-> "yyyy-MM-dd" |
      (java.text.SimpleDateFormat. |)
      (.parse | date))))

(def blog-page
  (let [path (str blog-path "index.html")]
    (->> processed-posts
         (sort-by get-post-date)
         (map create-post-listing)
         ((fn [list] {:posts list}))
         (render-template "blog")
         (render-default (get html-page-titles path))
         (#(identity {path %}))
         )))

;----- Individual pages

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

;------------- 

;; I gathered these all here, but in hindsight, I think adding to it gradually was better. It's not as if the order matters.
;; I guess it sort of gets clutter out of the way, but it also kind of marks the different "sections" of the script.
(def output-files
  (merge
    (:copy sectioned-raw-contents)
    post-pages
    other-pages
    blog-page
    ))

;; Potentially get from JSON or something.
(def config
  {:test-mode true
   })

(defn -main
  "Generate the website"
  [& args]
  (let [pages output-files]
    (stasis/empty-directory! target-dir)
     (stasis/export-pages pages target-dir config)))
