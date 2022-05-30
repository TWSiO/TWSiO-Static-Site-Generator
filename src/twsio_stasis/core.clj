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

;;; Utils

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

(def target-dir "target")

(def built-pages
  {"/index.html" "<h1>Welcome!</h1>"
   "/test-page.html" (fn [context] (str "<p>Test mode is " (if (:test-mode context) "on" "off") "!</p"))
   "/baz.html" (fn [context] (str "<h1>Welcome to " (:uri context) "!</h1>"))})

(def resources-path "resources/")

(def posts-path (str resources-path "posts/"))

(def templates-path (str resources-path "templates/"))

(def markdown-match #"\.md$")

(def file-matchers
  {:posts [ (str resources-path "posts/") #"\.md$" ]
   :templates [ (str resources-path "templates") #"\.mustache$" ]})

;; Need to recurse
(defn file-matcher-pass [fm]
  (->> fm
       (val-map (partial apply stasis/slurp-directory))
  ))

(defn convert-post [post-template content];
  (->> content
    md/md-to-html-string
    (#(identity {:content %}))
    (stache/render post-template)))

;; Do convert-post on posts, change endpoints, maybe remove post template
(defn convert-posts-pass
  [{posts :posts, templates :templates, :as tree}]

  (let [post-template (get templates "/post.mustache")
        converter (partial convert-post post-template)
        converted (val-map converter posts)
        with-endpoints (key-map #(clojure.string/replace % markdown-match ".html") converted)
        ]
    (assoc tree :posts with-endpoints
           )))

(def routing
  { :posts "/posts"
   })

;; Only supports one level at the moment.
(defn replace-routing [tree aggregate route-name route-string]
  (stasis/merge-page-sources
    { route-name (key-map #(str route-string %) (get tree route-name))
     :old aggregate}))

;; Convert keywords to routes
;; Filters out anything that isn't routed.
(defn final-route-pass [param-routing tree]
  (reduce-kv (partial replace-routing tree) {} param-routing
   ))

;; Compiling it all together
(defn compile-pages [gathered-filepaths]
  (->> gathered-filepaths
       file-matcher-pass
       convert-posts-pass
       ((partial final-route-pass routing))
       ))

;; Potentially get from JSON or something.
(def config
  {:test-mode true
   })

(defn -main
  "Generate the website"
  [& args]
  (let [pages (compile-pages file-matchers)]
    (stasis/empty-directory! target-dir)
     (stasis/export-pages pages target-dir config)))
