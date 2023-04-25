(ns twsio-stasis.blog
  (:require [markdown.core :as md])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.util :as util])
  (:require [twsio-stasis.config :as config])
  (:require [twsio-stasis.addons :as addons])
  )

(defn parse-post [raw-post]
  (as-> raw-post X
    (md/md-to-html-string-with-meta
      X
      :parse-meta? true
      :heading-anchors true
      :footnotes? true)
  ))

(def default-post-metadata
  {:title nil
   :subtitle nil
   })

(defn modify-metadata [path metadata]
  (as-> metadata X
    (merge default-post-metadata X)
    (assoc X :post-url (util/convert-default-path path))
    (update X :date first)
    (update X :title first)
    ))

; Parses a post, modifies the metadata, and does other modifications.
(defn process-post [[path, raw-post]]
  (as-> raw-post X
    (parse-post X)
    (update X :metadata (partial modify-metadata path))
    (update X :html addons/footnote-transform)
    [path, X]
    ))

(defn parse-date [date-string]
  (if (some? date-string) 
    (as-> "yyyy-MM-dd" X
      (java.text.SimpleDateFormat. X)
      (.parse X date-string)))
  )

(defn process-posts [raw-blog]
  (as-> raw-blog X
    (map process-post X)
    (sort-by #(parse-date (get-in % [1 :metadata :date])) X)
    (reverse X)))

(defn get-metadata [post-data]
  (get-in post-data [1 :metadata]))

(defn create-post-page [[path, {{title :title} :metadata :as data}]]
    [
     (util/convert-default-path path)
     (->> data
          (util/render-template "post")
          (util/render-default title))
     ])

(defn post-pages [raw-blog]
  (->> raw-blog
       process-posts
       (map create-post-page)
       util/to-hash-map
       ))

; The post listings page.
(defn blog-page [raw-blog]
  (let [path (str config/blog-path "index.html")
        ]
    (as-> raw-blog X
         (process-posts X)
         (map get-metadata X)
         (util/render-template "blog" {:posts X})
         (util/render-individual (get config/html-page-titles path) X)
         {path X})))
