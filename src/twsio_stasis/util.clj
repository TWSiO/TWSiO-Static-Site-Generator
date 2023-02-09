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


; Debugging
(defn trace [msg x]
  (println msg " " x)
  x)


(def markdown-match #"\.md$")


(def md-or-mustache-regex #"\.md$|\.mustache$")


; Converts file extension .md and .mustache to .html
(defn convert-default-path [path]
  (clojure.string/replace path md-or-mustache-regex ".html"))


; All templates as {"PATH" "CONTENTS"}
(def templates
  (stasis/slurp-resources "templates" #"\.mustache$"))


; Given a template name, gets a template
(defn get-template [template-name]
  (get templates (str "/" template-name ".mustache"))
  )


; Given a (mustache) template name and template arguments, render the template.
(defn render-template [ template-name & args ]
  (as-> template-name X
    (get-template X)
    (concat [X] args)
    (apply stache/render X)))


; Renders default template which just puts the header and footer and sets the title.
(defn render-default [title, content]
  (render-template
    "default"
    {:title title, :content content}
    {:header (get templates "/header.mustache"), :footer (get templates "/footer.mustache")}))


; Renders individual page which mostly puts `<main>` tag around everything, then just does default render.
(defn render-individual [title, content]
  (->> {:content content}
       (render-template "individual_page")
       (render-default title)))


(defn remove-file-extension [ path ]
  (as-> path X
    (re-find #"(.*)\.([^.]*)$" X)
    (get X 1)))


;; Given MD string, gets metadata and content
(defn process-md [raw-md]
  {:metadata (md/md-to-meta raw-md)
   :content (md/md-to-html-string raw-md :parse-meta? true)
   })


; Removes things like "/" and "." from paths.
(defn remove-path-artifacts [ extension path ]
  (let [regex (re-pattern (str "^\\/|\\." extension "$"))]
       (clojure.string/replace path regex "")))


; Renders a template given raw markdown string.
; Not sure what the first-template is?
(defn render-md-template [ template, raw-content & other-templates ]
  (let [html-and-meta (md/md-to-html-string-with-meta raw-content :parse-meta? true)
        content-and-meta (clojure.set/rename-keys html-and-meta {:html :content})

        first-template (if (first other-templates) (first other-templates) {})
        ]

  (render-template template content-and-meta first-template)))


; Default for rendering just a random markdown page.
(defn render-individual-page-md [ raw-content ]
    (render-default
      (:title (md/md-to-meta raw-content))
      (render-md-template "individual_page" raw-content)))


; Given path string, get extension.
(defn get-file-extension [ path ]
  (get
    (re-find #"(.*)\.([^.]*)$" path)
    2))
