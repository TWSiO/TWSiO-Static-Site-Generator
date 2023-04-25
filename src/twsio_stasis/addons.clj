(ns twsio-stasis.addons
  (:require [net.cgrand.enlive-html :as html])
  (:require [clojure.data.xml :as xml])
  )
; Might want to dedicate to just footnotes if complicated enough. Maybe move to addons folder to group.

(defn footnote-transform [post-html]
  (do
    ;(println "html" post-html)
    ;(println "Parsing" (html/select post-html [:span#foo]))
    ;(println "Parsing XML" (xml/parse post-html))
  post-html
  )
  )
