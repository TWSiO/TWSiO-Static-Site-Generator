(ns twsio-stasis.core
  (:require [markdown.core :as md])
  (:require [clostache.parser :as stache])
  (:require [stasis.core :as stasis])
  (:require [medley.core :as util]))
;  (:gen-class))

;------- Utils

(defn trace [x]
  (#(first %&) x (println x)))

;-------- file matchers
; keys to file contents.
; Gets the initial file contents.
; Typically { :key { "filename/path" content } }

(def target-dir "target")

(def resources-path "resources/")

(def posts-path (str resources-path "posts/"))

(def templates-path (str resources-path "templates/"))

(def markdown-match #"\.md$")

(def file-matchers
  {:posts [ (str resources-path "posts/"), markdown-match ]
   :templates [ (str resources-path "templates"), #"\.mustache$" ]
   :home [ resources-path, #"index\.mustache" ]
   })

(def routes
  {:posts "/posts"
   :home "/"})

(def output-files {})

;; Need to recurse for more directories
(def file-contents
  (->> file-matchers
       (util/map-vals (partial apply stasis/slurp-directory))))

(defn get-file-contents [section filename] (get (section file-contents) filename))

;--------- Random pages

(def templates (:templates file-contents))

;(defn render-default [{header "/header.mustache", default "/default.mustache", footer "/footer.mustache"}]
(defn render-default [title, content]
  (stache/render
    (get templates "/default.mustache")
    {:title title, :content content}
    {:header (get templates "/header.mustache"), :footer (get templates "/footer.mustache")}))

;--------- Handle posts

(def posts-metadata
  (util/map-vals md/md-to-meta (:posts file-contents)))

;; Basic way would probably be simpler, but I want to see if this works
(def post-template
  (->> file-contents
       :templates
       (#(get % "/post.mustache"))))

(def default-template (get (:templates file-contents) "/default.mustache"))

; Make blog page
; Add URL from key to posts hashmaps and put into blog.mustache template

;; Converts post content to HTML, then puts that in blog post mustache template.
(defn convert-post [post-template, {[title] :title, subtitle-list :subtitle}, content];
  (->> content
    (#(md/md-to-html-string % :parse-meta? true))
    (#(identity {:post %, :title title, :subtitle subtitle-list}))
    (stache/render post-template)))

(defn process-post [{[title] :title, :as metadata}, content]
  (->> content
       (convert-post post-template metadata)
       (render-default title)))

(def processed-posts
  (->> file-contents
       :posts
       (util/map-kv-vals (fn [filename, contents] (process-post (get posts-metadata filename) contents)))
       (util/map-keys #(str (:posts routes) (clojure.string/replace % markdown-match ".html")))))

(def output-files (merge output-files processed-posts))

;----- Home page

(def home-page 
  (let [home-template (get-file-contents :home "/index.mustache")]
        (render-default "This Website is Online" home-template)))

(def output-files (assoc output-files "/index.html" home-page))

;------------- Routing

;; Only supports one level at the moment.
(defn replace-routing [tree aggregate route-name route-string]
  (stasis/merge-page-sources
    { route-name (util/map-keys #(str route-string %) (get tree route-name))
     :old aggregate}))

;; Convert keywords to routes
;; Filters out anything that isn't routed.
;; Should be the final pass.
(defn final-routing-pass [param-routing tree]
  (reduce-kv (partial replace-routing tree) {} param-routing
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
