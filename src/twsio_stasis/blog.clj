(ns twsio-stasis.blog
  (:require [markdown.core :as md])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.util :as util])
  (:require [twsio-stasis.config :as config])
  )

(defn parse-posts [raw-blog]
  (medley/map-vals
    #(md/md-to-html-string-with-meta % :parse-meta? true)
    raw-blog))

(def default-post-metadata
  {:title nil
   :subtitle nil
   })

(defn process-post [[path, {content :html, metadata :metadata}]]
  [path
   {:html content
    :metadata 
    (as-> metadata m
      (merge default-post-metadata m)
      (assoc m :post-url (util/convert-default-path path))
      ;(assoc m (:title (first (:title m))))))
      (->> m :date first (assoc m :date))
      (->> m :title first (assoc m :title)))
    }])

(defn get-post-date [[_, {{date :date} :metadata :as all}]]
  (if (some? date) 
    (as-> "yyyy-MM-dd" X
      (java.text.SimpleDateFormat. X)
      (.parse X date))))

(defn process-posts [raw-blog]
  (as-> raw-blog X

    (parse-posts X)
    (map process-post X)
    (sort-by get-post-date X)
    (reverse X)))

; Testing using `let` in process-posts instead of `as->`
(defn pp [raw-blog]
  (let [parsed-posts (parse-posts raw-blog)
        processed-posts (map process-post parsed-posts)
        chronological-posts (sort-by get-post-date processed-posts)
        ]
    (reverse chronological-posts)))

(defn get-metadata [[path, {metadata :metadata}]] metadata)

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

(defn blog-page [raw-blog]
  (let [path (str config/blog-path "index.html")]
    (as-> raw-blog thread
         (process-posts thread)
         (map get-metadata thread)
         (util/render-template "blog" {:posts thread})
         (util/render-individual (get config/html-page-titles path) thread)
         {path thread})))
