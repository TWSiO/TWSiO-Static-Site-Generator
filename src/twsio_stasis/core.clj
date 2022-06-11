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

(def templates (:templates raw-contents))

(defn render-template [ template & args ]
  (as-> templates _
    (get _ (str "/" template ".mustache"))
    (apply stache/render (concat [_] args))))

;; Default processing of markdown
(defn process-md [[filepath, raw-md]]
  { :filename (remove-path-artifacts "md" filepath)
   :metadata (md/md-to-meta raw-md)
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

(defn route-basic-md [ path ] (str (remove-file-extension path) ".html"))

(defn render-md-template [ template, raw-content & other-templates ]
  (render-template
    template
    (clojure.set/rename-keys (md/md-to-html-string-with-meta raw-content)
                 {:html :content})
    (if (first other-templates) (first other-templates) {})))
    ;(if (first other-templates) (first other-templates) ({}))))

(defn render-individual-page-md [ raw-content ]
  (let [ content-metadata (clojure.set/rename-keys
                            (md/md-to-html-string-with-meta raw-content)
                            {:html :content})
        ]
    (render-default
      (:title (:metadata content-metadata))
      (render-md-template "individual_page" raw-content))))

;; This basically assumes the template has no parameters.
;(defn render-basic-mustache [ raw-content ]
;  )

;; Need filename before removing path artifacts.
;(defn render-basic [ filename, raw-content ]
;  (case (re-find #"\.[^.]*$" filename)
;    "md" (render-basic-md filename raw-content)
;    "mustache" (render-basic-mustache raw-content)
;  ))

;--------- Handle posts

(def processed-posts (map process-md (:posts raw-contents)))

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
     (route :posts :filename filename)
     (->> stache-data (render-template "post") (render-default title))
     ]))

(def post-pages
  (let [ posts-list (map create-post-page processed-posts) ]
    (->> posts-list
         (apply concat)
         (apply hash-map)
         )))

(defn create-post-listing [{:keys [filename, content, metadata]}]
  (as-> metadata m
    (merge default-post-metadata m)
    (assoc m :post-url (route :posts :filename filename))
    ;(assoc m :title (first (:title m)))))
    (->> m :title first (assoc m :title))))

(defn get-post-date [{{[date] :date} :metadata}]
  (if (some? date) 
    (as-> "yyyy-MM-dd" |
      (java.text.SimpleDateFormat. |)
      (.parse | date))))

(def blog-page
  (->> processed-posts
       (sort-by get-post-date)
       (map create-post-listing)
       ((fn [list] {:posts list}))
       (render-template "blog")
       (render-default "Blog home")
       (#(identity { (route :posts :filename "index") % }))))

;----- Individual pages

(def not-found 
  (->> "/not_found.mustache"
       (get-raw-contents :not-found)
       (render-individual "Page Not Found")))

(def home-page 
  (let [home-template (get-raw-contents :home "/index.mustache")]
    (render-default "This Website is Online" home-template)))

(def markdown-pages
  (->> raw-contents
       :pages
       (filter #(= "md" (get-file-extension (first %))))
       to-hash-map
       (util/map-vals render-individual-page-md)
       (util/map-keys #(->> % route-basic-md (str "/pages")))))

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
    markdown-pages
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
