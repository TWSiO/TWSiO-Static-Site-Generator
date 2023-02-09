(ns twsio-stasis.core
  (:require [stasis.core :as stasis])
  (:require [babashka.fs :as fs])
  (:require [twsio-stasis.blog :as blog])
  (:require [twsio-stasis.util :as util])
  (:require [twsio-stasis.config :as config])
  )
;  (:gen-class))


; Get everything, partition based on if we actually want to slurp it, then get paths and copy everything that isn't slurped.
; Gets everything that's not a directory.
; {PATH CONTENTS}
(def raw-contents
  (stasis/slurp-resources "site" #"^site/[^.]+\.[^.]+"))


; Partitions things to blog pages and not blog pages
(defn identify-section [[ path, _ ]]
  (let [blog-path-regex (re-pattern (str "^" config/blog-path))]
    (cond
      (re-find blog-path-regex path) :blog
      (= path "/index.mustache") :home
      (re-find util/md-or-mustache-regex path) :other
      :else :file-copy
      )))


(def sectioned-raw-contents
  (as-> raw-contents X
    (group-by util/identify-section X)
    (medley/map-vals util/to-hash-map X)
    (util/to-hash-map X)))

;=== Individual pages ===

(defn process-other-page [[path, contents]]
  (let [converted-path (util/convert-default-path path)

        page-title (get config/html-page-titles path)

        processed-contents (case (util/get-file-extension path)
                             "md" (util/render-individual-page-md contents)

                             "mustache" (util/render-individual page-title contents))
        ]
    {converted-path contents}
    ))


(def other-pages
  (as-> util/sectioned-raw-contents X
    (:other X)
    (map process-other-page X)
    (apply merge X)))


;First do special stuff for home page, then render-individual
(def home-page
  (as-> util/sectioned-raw-contents thread
    (:blog thread)
    (blog/process-posts thread)
    (take 3 thread)
    (map blog/get-metadata thread)
    (util/render-template "home" {:posts thread})
    (util/render-individual "Home" thread)))

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
    (blog/post-pages (:blog util/sectioned-raw-contents))
    other-pages
    (blog/blog-page (:blog util/sectioned-raw-contents))
    {"/index.html" home-page}
    (:file-copy util/sectioned-raw-contents)
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
