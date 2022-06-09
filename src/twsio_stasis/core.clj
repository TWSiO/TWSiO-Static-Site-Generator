(ns twsio-stasis.core
  (:require [markdown.core :as md])
  (:require [clostache.parser :as stache])
  (:require [stasis.core :as stasis])
  (:require [medley.core :as util]))
;  (:gen-class))

;------- Utils

(defn trace [x]
  (#(first %&) x (println x)))

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
  {:posts "/posts"
   :home "/"})

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

(defn process-post [{:keys [filename, metadata, content] :or {metadata {:title nil, :subtitle nil}}}]
  (let [ title (->> metadata :title first)
        subtitle (:subtitle metadata)
        stache-data {:post content, :title title, :subtitle subtitle}
        ]
    [
     (str "/posts/" filename ".html")
     (->> stache-data (stache/render post-template) (render-default title))
     ]))

(def posts-list (map process-post processed-posts))

(def post-pages
  (let [ convert #(apply hash-map (apply concat %))
        posts-list (map process-post processed-posts)
        ]
    (convert posts-list)
    ))

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

(def output-files
  (merge
    (copy-file "/robots.txt")
    post-pages
    {"/not_found.html" not-found}
    {"/index.html" home-page}
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
