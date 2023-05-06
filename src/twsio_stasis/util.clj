(ns twsio-stasis.util
  (:require [stasis.core :as stasis])
  (:require [clostache.parser :as stache])
  (:require [markdown.core :as md])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.config :as config])
  (:require [babashka.fs :as fs])
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

; There's a bug with clostache that puts backspaces before dollar signs.
; This is a fix for that.
; Java/Clojure regex is weird requiring re-pattern and all of those backspaces.
(defn fixed-render [ & args ]
  (as-> args X
    (apply stache/render args)
    (clojure.string/replace X (re-pattern "\\\\\\$") "\\$")
    )
  )


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
; When I see it like this, it's not really doing much, is it.
(defn render-template
  ([ template-name data ]
    (fixed-render (get-template template-name) data))
  ([ template-name data partial-templates ]
    (fixed-render (get-template template-name) data partial-templates))
    )

(def default-partials
  {:header (get templates "/header.mustache"),
   :footer (get templates "/footer.mustache")})

; Renders default template which just puts the header and footer and sets the title.
; (Why not just pass whatever metadata markdown creates to template?
; TODO Can maybe mostly get rid of? Or simply make as a composed/partial function?
; Also, maybe content can be a partial template as well and just combine and render everything at once rather than content separately.
(defn render-default [source-meta, content]
  (render-template
    "default"
    (merge source-meta {:content content})
    default-partials
    ))


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
        rendered (render-template template content-and-meta first-template)
        ]
    rendered
  ))


; Default for rendering just a random markdown page.
(defn render-individual-page-md [ raw-content ]
  (let [page-meta (md/md-to-meta raw-content)
        ]
    (render-default
      page-meta
      (render-md-template "individual_md" raw-content))))


(defn meta-file? [ path ]
  (as-> path X
    (fs/file-name X)
    (fs/strip-ext X)
    (fs/split-ext X)
    (get X 1)
    (= "meta" X)
    ))

(defn find-matching-meta [meta-files-data, [content-path, _]]
  (let [pred (fn [[meta-path, _]]
                (=
                 (fs/strip-ext (fs/strip-ext meta-path))
                 (fs/strip-ext content-path)
                 )
               )
        ]
    (as-> meta-files-data X
      (filter pred X)
      (first X)
      (second X)
    )))
