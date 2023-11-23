(ns durak.controls.boot
  (:require [re-frame.core :as re-frame]
            [day8.re-frame.async-flow-fx]
            [durak.controls.helper :as helper]))

(defn boot-flow []
  {:first-dispatch [::helper/init-app-helper]

   :rules [{:when :seen?
            :events ::helper/update-app-helper
            :dispatch-n [[::helper/list-games]]
            :halt? true}]})

(re-frame/reg-event-fx
 ::boot
 (fn boot [_ _]
   {:async-flow (boot-flow)}))
