(ns twsio-stasis.blog
  (:require [markdown.core :as md])
  (:require [medley.core :as medley])
  (:require [twsio-stasis.util :as util])
  (:require [twsio-stasis.config :as config])
  (:require [clojure.data.json :as json])
  )

(defn parse-post [raw-post]
  (md/md-to-html-string-with-meta
    raw-post
    :parse-meta? true
    :heading-anchors true
    :footnotes? true)
  )

(def default-post-metadata
  {:subtitle nil
   })

(defn get-blog-params [path metadata]
  {:post-url (util/convert-default-path path)
   :date-string (.format
                  (java.text.SimpleDateFormat. "yyyy-MM-dd")
                  (:date metadata))
   }
  )

(defn modify-metadata [path metadata]
  (merge
    default-post-metadata
    (get-blog-params path metadata)
    metadata))

; Parses a post, modifies the metadata, and does other modifications.
(defn process-post [[path, raw-post]]
  (as-> raw-post X
    (parse-post X)
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
    (sort-by #(get-in % [1 :metadata :date]) X)
    (reverse X)))

(defn get-metadata [post-data]
  (get-in post-data [1 :metadata]))

(defn create-post-page [[path, data]]
  (let [out-path (util/convert-default-path path)
        post-template (util/get-template "post")
        metadata (assoc
                   (:metadata data)
                   :content
                   (:html data))
        ]
    [out-path
     (util/render-default metadata post-template)
     ]
    ))

(defn post-pages [raw-blog]
  (->> raw-blog
       process-posts
       (map create-post-page)
       util/to-hash-map
       ))

(defn blog-list [posts]
  (util/render-template
    "blog-list" 
    {:posts (map get-metadata posts)}
  ))
