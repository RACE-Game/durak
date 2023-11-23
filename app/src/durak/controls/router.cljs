(ns durak.controls.router
  (:require
   [re-frame.core :as re-frame]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]))

;;; Events

(re-frame/reg-event-db ::match
  (fn [db [_ new-match]]
    (update db
            ::current-route
            (fn [old-match]
              (when new-match
                (assoc new-match
                       :controllers
                       (rfc/apply-controllers (:controllers old-match) new-match)))))))

(re-frame/reg-event-fx ::push
  (fn [_ [_ route]]
    {:push route}))

;;; Subs

(re-frame/reg-sub ::current-route :-> ::current-route)

(re-frame/reg-sub ::current-route-name
  :<- [::current-route]
  :-> (comp :name :data))

;;; Fxs

(re-frame/reg-fx :push
  (fn [route]
    (apply rfe/push-state route)))
