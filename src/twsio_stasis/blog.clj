(ns twsio-stasis.blog
  (:require [markdown.core :as md])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.util :as util])
  (:require [twsio-stasis.config :as config])
  (:require [net.cgrand.enlive-html :as html])
  )

(defn parse-post [raw-post]
  (as-> raw-post X
    (md/md-to-html-string-with-meta X :parse-meta? true)
  ))

(def default-post-metadata
  {:title nil
   :subtitle nil
   })

(defn modify-metadata [path metadata]
  (as-> metadata m
    (merge default-post-metadata m)
    (assoc m :post-url (util/convert-default-path path))
    ;(assoc m (:title (first (:title m))))))
    (->> m :date first (assoc m :date))
    (->> m :title first (assoc m :title)))
  )

(defn process-post [[path, raw-post]]
  (as-> raw-post X
    (parse-post raw-post)
    (update X :metadata (partial modify-metadata path))
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
