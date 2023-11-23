(ns durak.controls.ux
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::get
  (fn [db [_ k default]]
    (get-in db [::ux-vars k] default)))

(re-frame/reg-event-db
  ::set
  (fn [db [_ k v]]
    (assoc-in db [::ux-vars k] v)))

(re-frame/reg-event-db
  ::unset
  (fn [db [_ k]]
    (update db ::ux-vars dissoc k)))

(re-frame/reg-event-db
  ::toggle
  (fn [db [_ k]]
    (update-in db [::ux-vars k] not)))

(re-frame/reg-event-fx
  ::set-temp
  (fn [{:keys [db]} [_ k v duration]]
    {:db             (assoc-in db [::ux-vars k] v)
     :dispatch-later [{:ms       duration
                       :dispatch [::unset k]}]}))
