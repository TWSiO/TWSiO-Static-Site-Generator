(ns twsio-stasis.core
  (:require [stasis.core :as stasis])
  (:require [babashka.fs :as fs])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.blog :as blog])
  (:require [twsio-stasis.util :as util])
  (:require [twsio-stasis.config :as config])
  (:require [babashka.fs :as fs])
  (:require [clojure.data.json :as json])
  (:require [clostache.parser :as stache])
  (:require [markdown.core :as md])
  )


; Get everything, partition based on if we actually want to slurp it, then get paths and copy everything that isn't slurped.
; Gets everything that's not a directory.
; {PATH CONTENTS}
(def raw-contents
  (stasis/slurp-resources "site" #"^site/[^.]+\.[^.]+"))

; Partitions things to blog pages and not blog pages
(defn identify-section [[ path, _ ]]
  (let [blog-path-regex (re-pattern (str "^" config/blog-path))]
    (cond
      (and
        (not= "/blog/index.mustache" path)
        (not= "/blog/index.meta.json" path)
        (re-find blog-path-regex path)) :blog

      (re-find util/md-or-mustache-regex path) :other

      ; I think that was original plan was to put this stuff in the config. Oh well.
      (util/meta-file? path) :meta

      :else :file-copy
      )))


(def sectioned-raw-contents
  (as-> raw-contents X
    (group-by identify-section X)
    (medley/map-vals util/to-hash-map X)
    (util/to-hash-map X)))


(def parsed-meta
  (medley/map-vals
    #(json/read-str % :key-fn keyword)
    (:meta sectioned-raw-contents)
    ))

;=== Individual pages ===

(defn get-partials [text, templates]
  (let [special-partials (merge util/default-partials
                                {}) ; Maybe fill later.

        joiner (fn [aggr, [tmp, prtial-text]]
                 (assoc
                   aggr
                   (keyword (str tmp "-content"))
                   prtial-text
                   ))

        shift-zipped (as-> templates X
                       (rest X)
                       (map util/get-template X)
                       (conj X text)
                       (zipmap templates X))

        nested-content (reduce joiner {} shift-zipped)
        ]

    (merge special-partials nested-content)))

(defn blog-list-lambda
  "Parse JSON, do blog list stuff, return blog list HTML.
  Create a template just for a blog listing."
  [json-string]
  (let [params (json/read-str json-string :key-fn keyword)
        raw-blog (:blog sectioned-raw-contents)
        posts (blog/process-posts raw-blog)
        ]
    (if (contains? params :max)
      (blog/blog-list (take (:max params) posts))
      (blog/blog-list posts))
    )
  )

(def mustache-lambdas
  {:blog-list blog-list-lambda
   })

(defn process-mustache
  "Special processing of mustache templates to add in lambdas and other things."
  [text, metadata]
  (let [complete-metadata (merge metadata mustache-lambdas)
        partials (get-partials text (:templates metadata))
        ]
    (if (contains? metadata :templates)

      (util/render-template 
        (first (:templates metadata))
        complete-metadata
        partials)

      (util/fixed-render text complete-metadata)
      )))

(defn escape-heading
  "I've somewhat forgotten what this is, but I think it allows adding HTML or mustache stuff in markdown files.
  I think that it might allow for multi-line things. Not quite sure."
  [text, state]
  (if (contains? state :inline-heading)

    [(if (= "<h1>@@" (subs text 0 6))
       (subs (subs text 0 (- (count text) 6)) 6)
       text
       ),
     state]
      
  [text, state]
    ))

; TODO Use with blogs posts.
(defn generic-processing
  " Process mustache
  Surround with template specified in template metadata
  Maybe make extensible or something?
  Convert markdown to HTML (mustache) then render mustache."
  [text, metadata]
    (as-> text X
      (md/md-to-html-string
        X
        :parse-meta? true
        :inhibit-separator "%%"
        :custom-transformers [escape-heading])
      (process-mustache X metadata)
      )
  )

; Get metadata
(defn simple-processing [page-text]
  (generic-processing page-text (md/md-to-meta page-text)))

(defn process-other-page [file-meta, [path, contents]]
  (let [converted-path (util/convert-default-path path)

        page-title (get config/html-page-titles path)

        processed-contents (case (fs/extension path)
                             "md" (util/render-individual-page-md contents)

                             "mustache" (case (fs/extension (fs/strip-ext path))
                                          "md" (simple-processing contents)
                                          (process-mustache contents file-meta)))
        ]

    {converted-path processed-contents}
    ))


(def other-pages
  (let [process-other-page-with-meta (fn [page-data]
                                       (process-other-page
                                         (util/find-matching-meta parsed-meta page-data)
                                         page-data))
        ]
  (as-> sectioned-raw-contents X
    (:other X)
    (map process-other-page-with-meta X)
    (apply merge X))))

;=== Copy other files ===

;; Has to come after stasis stuff
;; slurp-dir doesn't work with ttf and non-text files.
(defn copy-other-files []
  (fs/copy-tree
    (str config/resources-path "/assets")
    (str config/target-dir "/assets"))
  )

;======

;; I gathered these all here, but in hindsight, I think adding to it gradually was better. It's not as if the order matters.
;; I guess it sort of gets clutter out of the way, but it also kind of marks the different "sections" of the script.
(def output-files
  (merge
    (blog/post-pages (:blog sectioned-raw-contents))
    other-pages
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
    (stasis/empty-directory! config/target-dir)
    (stasis/export-pages pages config/target-dir config))
  (copy-other-files))
