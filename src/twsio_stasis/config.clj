(ns twsio-stasis.config
  )

;; Was going to use stasis way, but then can't view page output with conjure.

(def blog-path "/blog/")

(def target-dir "target")

(def resources-path "resources")

(def site-path (str resources-path "/site"))

(def html-page-titles
  {"/index.mustache" "This Website is Online"
   (str blog-path "index.html") "Blog home"
   })
