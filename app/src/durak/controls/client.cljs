(ns durak.controls.client
  "App client for game."
  (:require
   ["@race-foundation/sdk-core" :refer [AppClient]]
   ["@race-foundation/sdk-facade" :refer [FacadeTransport]]
   ["@race-foundation/sdk-solana" :refer [SolanaTransport]]
   [borsh.writer :as bw]
   [cljs.core.async :as a]
   [cljs.core.async.interop :refer [<p!]]
   [durak.constants :refer [chain->rpc]]
   [durak.controls.wallet :as-alias wallet]
   [durak.controls.helper :as-alias helper]
   [durak.utils :as u]
   [durak.constants :as c]
   [re-frame.core :as re-frame]))

;;; Utils

(defn init-client-opts
  "Construct the options needed for client initialization.

  Return a JS object of the type AppClientInitOpts."
  [{:keys [game-addr chain wallet on-message on-event on-tx-state on-conn-state transport]}]
  #js {:transport         transport,
       :wallet            wallet,
       :gameAddr          game-addr,
       :storage           js/window.localStorage
       :onEvent           on-event
       :onMessage         on-message
       :onTxState         on-tx-state
       :onConnectionState on-conn-state})

;;; Fxs

(defn attach-game [^js app-client]
  (a/go (<p! (.attachGame app-client))))

(defn update-game-info [^js app-client]
  (let [game-info (.-info app-client)]
    (re-frame/dispatch [::update-info game-info])))

(defn init-client
  [params]
  (a/go
    (let [init-opts (init-client-opts params)]
      (js/console.log "init-opts: " init-opts)
      (try
        (when-let [app-client (<p! (.initialize AppClient init-opts))]
          (attach-game app-client)
          (update-game-info app-client)
          (re-frame/dispatch [::update-client {:app-client app-client}]))
        (catch js/Error e
          (js/console.error e))))))

(re-frame/reg-fx :init-client init-client)

(defn close-client [{:keys [^js app-client]}]
  (a/go (<p! (.exit app-client))))

(re-frame/reg-fx :close-client close-client)

(defn- join
  "Player buys in with a certain amount."
  [{:keys [^js app-client amount]}]
  (js/console.log "Join game, amount:" amount)
  (a/go
    (let [join-opts #js {:amount amount
                         :createProfileIfNeeded true}]
      (<p! (.join app-client join-opts)))))

(re-frame/reg-fx :join join)

(defn- decrypt
  [{:keys [^js app-client random-id succ-event]}]
  (js/console.log "Decrypt, client:" app-client ", random-id:" random-id)
  (a/go
    (let [decrypted (u/map->clj (<p! (.getRevealed app-client random-id)))]
      (js/console.log "Decryption result:" decrypted)
      (re-frame/dispatch [succ-event random-id decrypted]))))

(re-frame/reg-fx :decrypt decrypt)

(defn- submit-event
  [{:keys [^js app-client event]}]
  (js/console.log "Submit event:" event)
  (a/go
    (<p! (.submitEvent app-client (bw/serialize event)))))

(re-frame/reg-fx :submit-event submit-event)

;;; Subs

(defn decryption [db random-id]
  (when-not (zero? random-id)
    (get-in db [::decryptions random-id])))

(re-frame/reg-sub ::decryption :=> decryption)

;;; Events

(re-frame/reg-event-fx
 ::init-client
 [re-frame/unwrap]
 (fn [{:keys [db]} {:keys [game-addr on-event on-message on-tx-state on-conn-state]}]
   (let [chain     c/chain
         wallet    (::wallet/wallet db)
         transport (::helper/transport db)]
     {:init-client {:game-addr     game-addr
                    :chain         chain
                    :transport     transport
                    :wallet        wallet
                    :on-event      on-event
                    :on-message    on-message
                    :on-tx-state   on-tx-state
                    :on-conn-state on-conn-state}})))

(re-frame/reg-event-fx
 ::close-client
 (fn [{:keys [db]} _]
   (let [client (::client db)]
     {:close-client {:app-client client}
      :db (dissoc db ::client)})))

(re-frame/reg-event-db
 ::update-client
 [re-frame/unwrap]
 (fn update-client [db {:keys [app-client]}]
   (assoc db ::client app-client)))

(re-frame/reg-event-db
 ::update-info
 [re-frame/trim-v]
 (fn update-game-info [db [info]]
   (assoc db ::info info)))

(re-frame/reg-event-fx
 ::join
 [re-frame/trim-v]
 (fn [{:keys [db]} [amount]]
   (let [app-client (::client db)]
     {:join {:app-client app-client, :amount amount}})))

(re-frame/reg-event-fx
 ::decrypt
 [re-frame/unwrap]
 (fn decrypt
   [{:keys [db]} {:keys [random-id succ-event]}]
   (let [app-client (::client db)]
     {:decrypt {:app-client app-client
                :random-id  random-id
                :succ-event succ-event}})))

(re-frame/reg-event-db
 ::update-decryption
 [re-frame/trim-v]
 (fn update-decryption
   [db [random-id decryption]]
   (assoc-in db [::decryptions random-id] decryption)))

(re-frame/reg-event-db
  ::clear-decryptions
  (fn clear-decryptions
    [db _]
    (dissoc db ::decryptions)))

(re-frame/reg-event-fx
 ::submit-event
 [re-frame/trim-v]
 (fn submit-event
   [{:keys [db]} [event]]
   (let [app-client (::client db)]
     {:submit-event {:app-client app-client
                     :event      event}})))
