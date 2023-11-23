(ns durak.routes
  (:require
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [re-frame.core :as re-frame]
   [durak.pages.lobby :refer [lobby-page]]
   [durak.pages.game :refer [game-page enter-game-page leave-game-page]]
   [durak.controls.router :as router]))

(def routes
  [["/"
    {:name :lobby,
     :view (fn [] [lobby-page])}]

   ["/game/:addr"
    {:name        :game
     :view        (fn [params] [game-page params])
     :controllers [{:parameters {:path [:addr]}
                    :start enter-game-page
                    :stop  leave-game-page}]}]])

(defn router-page
  []
  (when-let [route @(re-frame/subscribe [::router/current-route])]
    (let [{:keys [data parameters]} route
          {:keys [view]} data]
      [view parameters])))

(defn init-router!
  []
  (rfe/start!
   (rf/router routes {})
   (fn [m]
     (re-frame/dispatch [::router/match m]))
   {:use-fragment true}))
