(ns durak.controls.game
  (:require
   [borsh.reader :refer [deserialize]]
   [clojure.string :as str]
   [durak.controls.client :as client]
   [durak.controls.helper :as-alias helper]
   [durak.controls.wallet :as-alias wallet]
   [durak.types :as types]
   [durak.utils :as u]
   [re-frame.core :as re-frame]))

;;; Handlers

(defn on-event [ctx state-vec ^js event]
  (let [state               (deserialize types/->DurakState state-vec)
        {:keys [random-id displays]} state]
    (let [{:keys [players]} (u/bean ctx)]
      (doseq [p     players
              :let  [{:keys [profile]} (u/bean p)]
              :when profile
              :let  [{:keys [addr nick pfp]} (u/bean profile)]]
        (re-frame/dispatch [::helper/update-profile
                            {:nick nick
                             :addr addr
                             :pfp  pfp}])
        (when (and pfp (not (str/blank? pfp)))
          (re-frame/dispatch [::helper/fetch-nft pfp]))))

    (doseq [d displays]
      (re-frame/dispatch [::add-display d]))

    (when event
      (case (.kind event)
        "GameStart"
        (re-frame/dispatch [::client/clear-decryptions])
        "SecretsReady"
        (when (pos? random-id)
          (re-frame/dispatch [::client/decrypt {:random-id  random-id
                                                :succ-event ::client/update-decryption}]))
        nil))

    (re-frame/dispatch [::update-game-state state])))

(defn on-message [msg]
  (js/console.log "msg: " msg))

(defn on-tx-state [tx-state]
  (js/console.log "tx-state: " tx-state))

(defn on-conn-state [conn-state]
  (js/console.log "conn-state: " conn-state))

;;; Subs

(re-frame/reg-sub ::state :-> ::game-state)

(re-frame/reg-sub ::displays :-> ::displays)

;;; Events

(re-frame/reg-event-fx
  ::attach
  [re-frame/trim-v]
  (fn [{:keys [db]} [addr]]
    (let [transport (::helper/transport db)
          wallet    (::wallet/wallet db)]
      (if (and transport wallet)
        {:dispatch [::client/init-client {:game-addr addr
                                          :on-event on-event
                                          :on-message on-message
                                          :on-tx-state on-tx-state
                                          :on-conn-state on-conn-state}]}
        {:dispatch-later [{:ms 1000
                           :dispatch [::attach addr]}]}))))

(re-frame/reg-event-db
  ::update-game-state
  [re-frame/trim-v]
  (fn update-game-state
    [db [state]]
    (assoc db ::game-state state)))

(re-frame/reg-event-db
  ::clear-display
  [re-frame/trim-v]
  (fn clear-display
    [db [id]]
    (update db ::displays dissoc id)))

(re-frame/reg-event-fx
  ::add-display
  [re-frame/trim-v]
  (fn add-display
    [{:keys [db]} [display]]
    (let [id       (random-uuid)
          duration (case (types/display-type display)
                     :display/deal-cards    1000
                     :display/player-action 5000)]
      {:db             (assoc-in db [::displays id] display)
       :dispatch-later [{:ms       duration
                         :dispatch [::clear-display id]}]})))

;;; Player actions

(re-frame/reg-event-fx
  ::attack
  [re-frame/trim-v]
  (fn attack
    [_ [cards]]
    {:dispatch [::client/submit-event (types/->GameEvent (types/->Attack cards))]}))

(re-frame/reg-event-fx
  ::co-attack
  [re-frame/trim-v]
  (fn co-attack
    [_ [cards]]
    {:dispatch [::client/submit-event (types/->GameEvent (types/->CoAttack cards))]}))

(re-frame/reg-event-fx
  ::defend
  [re-frame/trim-v]
  (fn defend
    [_ [card target]]
    {:dispatch [::client/submit-event (types/->GameEvent (types/->Defend card target))]}))

(re-frame/reg-event-fx
  ::take
  [re-frame/trim-v]
  (fn take
    [_ _]
    {:dispatch [::client/submit-event (types/->GameEvent (types/->Take))]}))

(re-frame/reg-event-fx
  ::forward
  [re-frame/trim-v]
  (fn forward
    [_ [card]]
    {:dispatch [::client/submit-event (types/->GameEvent (types/->Forward card))]}))

(re-frame/reg-event-fx
  ::beated
  [re-frame/trim-v]
  (fn beated
    [_ _]
    {:dispatch [::client/submit-event (types/->GameEvent (types/->Beated))]}))
