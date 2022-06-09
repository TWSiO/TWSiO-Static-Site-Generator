(ns twsio-stasis.core
  (:require [markdown.core :as md])
  (:require [clostache.parser :as stache])
  (:require [stasis.core :as stasis])
  (:require [medley.core :as util]))
;  (:gen-class))

;------- Utils

(defn trace [msg & all]
  (println msg " " all)
  all)

(defn put-arg [fun pos & args]
  (let [ [fst, snd] (split-at (- pos 1) args) ]
  #(apply fun (concat fst [%] snd))))

(defn apply-put-arg [& args]
  (print args)
  ((apply put-arg (butlast args)) (take-last args)))

;-------- file matchers
; keys to file contents.
; Gets the initial file contents.
; Typically { :key { "filename/path" content } }

(def target-dir "target")

(def resources-path "resources/")

(def posts-path (str resources-path "posts/"))

(def templates-path (str resources-path "templates/"))

(def markdown-match #"\.md$")

(defn remove-path-artifacts [ extension path ]
  (let [regex (re-pattern (str "^\\/|\\." extension "$"))]
       (clojure.string/replace path regex "")))

(def file-matchers
  {:posts [ (str resources-path "posts/"), markdown-match ]
   :templates [ (str resources-path "templates"), #"\.mustache$" ]
   :home [ resources-path, #"index\.mustache" ]
   :not-found [ resources-path, #"not_found\.mustache" ]
   :pages [ (str resources-path "pages/"), #"\.md" ]
   })

(def routes
  {:posts "/blog/{{{filename}}}.html"
   :home "/index.html"
   :not-found "/not_found.html"
   })

(defn route [ route-id & {:as all} ]
  (stache/render (get routes route-id) all))

(defn copy-file [ path ]
  {(str path) (slurp (str resources-path path))}
  )

;; Need to recurse for more directories
(def raw-contents
  (->> file-matchers
       (util/map-vals (partial apply stasis/slurp-directory))))

(defn get-raw-contents [section filename] (get (section raw-contents) filename))

(defn render-template [ template & args ]
  (as-> raw-contents _
    (:templates _)
    (get _ (str "/" template ".mustache"))
    (apply stache/render (concat [_] args))))

;; Default processing of markdown
(defn process-md [[filepath, raw-md]]
  { :filename (remove-path-artifacts "md" filepath)
   :metadata (md/md-to-meta raw-md)
   :content (md/md-to-html-string raw-md :parse-meta? true)
   })

;--------- Random pages

(def templates (:templates raw-contents))

;(defn render-default [{header "/header.mustache", default "/default.mustache", footer "/footer.mustache"}]
(defn render-default [title, content]
  (stache/render
    (get templates "/default.mustache")
    {:title title, :content content}
    {:header (get templates "/header.mustache"), :footer (get templates "/footer.mustache")}))

(defn render-individual [title, content]
  (render-default title
                  (stache/render
                    (get templates "/individual_page.mustache")
                    {:content content})))

;--------- Handle posts

(def processed-posts (map process-md (:posts raw-contents)))

;; Basic way would probably be simpler, but I want to see if this works
(def post-template
  (->> raw-contents
       :templates
       (#(get % "/post.mustache"))))

(def default-template (get (:templates raw-contents) "/default.mustache"))

(def default-post-metadata
  {:title nil
   :subtitle nil
   })

(defn create-post-page [{:keys [filename, content, metadata]}]
  (let [final-meta (merge default-post-metadata metadata)
        title (->> final-meta :title first)
        subtitle (:subtitle final-meta)
        stache-data {:post content, :title title, :subtitle subtitle}
        ]
    [
     (route :posts filename)
     (->> stache-data (stache/render post-template) (render-default title))
     ]))

(def post-pages
  (let [ convert #(apply hash-map (apply concat %))
        posts-list (map create-post-page processed-posts)
        ]
    (convert posts-list)
    ))

(defn create-post-listing [{:keys [filename, content, metadata]}]
  (as-> metadata m
    (merge default-post-metadata m)
    (assoc m :post-url (route :posts filename))
    ;(assoc m :title (first (:title m)))))
    (->> m :title first (assoc m :title))))

(defn get-post-date [{{[date] :date} :metadata}]
  (if (some? date) 
    (as-> "yyyy-MM-dd" |
      (java.text.SimpleDateFormat. |)
      (.parse | date))))

;; TODO Parse dates, order posts.
(def blog-page
  (->> processed-posts
       (sort-by get-post-date)
       (trace "1")
       (map create-post-listing)
       (trace "2")
       ((fn [list] {:posts list}))
       (trace "3")
       (render-template "blog")
       (trace "4")
       (render-default "Blog home")
       (trace "5")
       (#(identity { (route :posts "index") % }))))

;----- Individual pages

(def not-found 
  (->> "/not_found.mustache"
       (get-raw-contents :not-found)
       (render-individual "Page Not Found")))

(def home-page 
  (let [home-template (get-raw-contents :home "/index.mustache")]
        (render-default "This Website is Online" home-template)))

;; Need to get metadata to pass into the function.
;(def individual-pages
;  (->> raw-contents
;       :pages
;       (util/map-vals #(render-individual))

;------------- 

;; I gathered these all here, but in hindsight, I think adding to it gradually was better. It's not as if the order matters.
;; I guess it sort of gets clutter out of the way, but it also kind of marks the different "sections" of the script.
(def output-files
  (merge
    (copy-file "/robots.txt")
    post-pages
    {(route :not-found) not-found}
    {(route :home) home-page}
    blog-page
    ))

;; Potentially get from JSON or something.
(def config
  {:test-mode true
   })

(defn -main
  "Generate the website"
  [& args]
  ;(let [pages (compile-pages file-matchers)]
  (let [pages output-files]
    (stasis/empty-directory! target-dir)
     (stasis/export-pages pages target-dir config)))

;(defn -main [] (println "foobarbaz"))
