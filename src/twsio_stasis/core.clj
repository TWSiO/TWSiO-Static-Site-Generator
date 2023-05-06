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
      (re-find blog-path-regex path) :blog
      (= path "/index.mustache") :home
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

(defn process-other-page [file-meta, [path, contents]]
  (let [converted-path (util/convert-default-path path)

        page-title (get config/html-page-titles path)

        processed-contents (case (fs/extension path)
                             "md" (util/render-individual-page-md contents)

                             "mustache" (util/render-default
                                          file-meta
                                          (util/fixed-render
                                            contents
                                            file-meta
                                            )))
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

;First do special stuff for home page, then render-individual
(def home-page
  (as-> sectioned-raw-contents X
    (:blog X)
    (blog/process-posts X)
    (take 3 X)
    (map blog/get-metadata X)
    (util/render-template "home" {:posts X})
    (util/render-default {:title "Home"} X)))

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
    (blog/blog-page (:blog sectioned-raw-contents))
    {"/index.html" home-page}
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
