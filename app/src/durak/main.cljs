(ns durak.main
  (:require [durak.routes :refer [init-router! router-page]]
            [durak.controls.boot :as boot]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

(defn mount! []
  (rdom/render [router-page] (.getElementById js/document "app")))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn after-load []
  (mount!))

(defn -main []
  (init-router!)
  (mount!)
  (re-frame/dispatch-sync [::boot/boot]))
