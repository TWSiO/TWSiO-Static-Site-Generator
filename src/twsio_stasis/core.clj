(ns twsio-stasis.core
  (:require [markdown.core :as md])
  (:require [clostache.parser :as stache])
  (:require [stasis.core :as stasis]))
;  (:gen-class))

;; Will probably want to gather all of the slurped up data, put it all into a structure that indicates where it came from and has parsed the path strings, so posts is in a "branch".
;; Then once everything is gathered, then figure out how to process it all, then figure out where it all goes.
;; Want to keep all of the old info just in case.
;; Might also want to go in "passes" for particular purposes. Like a pass for tags, then a pass for substitutions, etc.
;; Want to have all of the initial data gathered in one place so we can decide based on all of the initial information how to process it next.
;; So want to refactor what I have now to not process posts all the way first, but gather up everything, then process it all.

;; Might want to .gitignore /resources

;; It conceptually takes a map of file contents (named keys to contents), and transforms it in several passes into a map of files it outputs.
; The final pass routes stuff in keys to particular paths.

;; Could I simplify by somehow taking a smaller amount of stuff during a pass and modifying a small amount of stuff in a pass, but still passing the whole thing?
; (I'm getting flashbacks of monads).
; I guess using destructuring and modifying variable with everything could be alright.

;;;;;;;;;;;;;;;

;; I think I'm going to try and combine Clojure and Scala. Move some of the more "structural" stuff into Scala and have the "implementation" stuff in Clojure. Or something.

;;;;;;;;;;;;;;;

;------- Utils

(defn trace [x]
  (#(first %&) x (println x)))

(defn key-map [f m]
  (let [change-key (fn [[k v]] {(f k) v})]
    (->> m
         (map change-key)
         (apply merge))))


(defn val-map [f m]
  (let [change-key (fn [[k v]] {k (f v)})]
    (->> m
         (map change-key)
         (apply merge))))

;-------- file matchers
; keys to file contents.
; Gets the initial file contents.
; Typically { :key { "filename/path" content } }

(def target-dir "target")

; (def built-pages
;   {"/index.html" "<h1>Welcome!</h1>"
;    "/test-page.html" (fn [context] (str "<p>Test mode is " (if (:test-mode context) "on" "off") "!</p"))
;    "/baz.html" (fn [context] (str "<h1>Welcome to " (:uri context) "!</h1>"))})

(def resources-path "resources/")

(def posts-path (str resources-path "posts/"))

(def templates-path (str resources-path "templates/"))

(def markdown-match #"\.md$")

(def file-matchers
  {:posts [ (str resources-path "posts/"), markdown-match ]
   :templates [ (str resources-path "templates"), #"\.mustache$" ]
   :home [ resources-path, #"index\.mustache" ]
   })

(def output-files {})

;; Need to recurse for more directories
(def file-contents
  (->> file-matchers
       (val-map (partial apply stasis/slurp-directory))))

;--------- Home page (and maybe other pages)

(def templates (:templates file-contents))

;(defn render-default [{header "/header.mustache", default "/default.mustache", footer "/footer.mustache"}]
(defn render-default [title, content]
  (stache/render
    (get templates "/default.mustache")
    {:title title, :content content}
    {:header (get templates "/header.mustache"), :footer (get templates "/footer.mustache")}))

(defn handle-home [{home :home, templates :templates, :as tree}]
  )

;--------- Handle posts

;; Converts post content to HTML, then puts that in blog post mustache template.
(defn convert-post [post-template content];
  (->> content
    md/md-to-html-string
    (#(identity {:post %}))
    (stache/render post-template)))

;; Basic way would probably be simpler, but I want to see if this works
(def post-template
  (->> file-contents
       :templates
       (#(get % "/post.mustache"))))

(def default-template (get (:templates file-contents) "/default.mustache"))

(defn process-post [title, content]
  (->> content
       (convert-post post-template)
       (render-default title)))

(def processed-posts
  (->> file-contents
       :posts
       (key-map #(str "/posts" (clojure.string/replace % markdown-match ".html")))
       (val-map (partial process-post "TODO"))))

(def output-files (merge output-files processed-posts))

;------------- Routing

;; Only supports one level at the moment.
(defn replace-routing [tree aggregate route-name route-string]
  (stasis/merge-page-sources
    { route-name (key-map #(str route-string %) (get tree route-name))
     :old aggregate}))

;; Convert keywords to routes
;; Filters out anything that isn't routed.
;; Should be the final pass.
(defn final-routing-pass [param-routing tree]
  (reduce-kv (partial replace-routing tree) {} param-routing
   ))

;-----------

; Compilation
; 1. Matches keys with raw file output based on file-matchers
; 2. Converts blog post markdown to HTML with post template.
; 3. Routes all known pages.

;; Compiling it all together
;(defn compile-pages [gathered-filepaths]
;  (->> gathered-filepaths
;       file-matcher-pass
;       convert-posts-pass
;       ((partial final-routing-pass routing))
;       ))

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
