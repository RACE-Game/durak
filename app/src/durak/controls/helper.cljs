(ns durak.controls.helper
  (:require [re-frame.core :as re-frame]
            [cljs.core.async :as a]
            [cljs.core.async.interop :refer [<p!]]
            ["@race-foundation/sdk-core" :refer [AppHelper]]
            ["@race-foundation/sdk-facade" :refer [FacadeTransport]]
            ["@race-foundation/sdk-solana" :refer [SolanaTransport]]
            [durak.constants :as c]
            [durak.controls.wallet :as wallet]
            [clojure.string :as str]
            [cljs-bean.core :refer [->clj ->js]]))

;;; Fxs

(defn- init-app-helper
  "Initialize the app helper by chain name."
  [{:keys [chain succ-event] :as params}]
  (js/console.log "Init app helper" params)
  (let [rpc        (c/chain->rpc chain)
        transport  (case chain
                     (:chain/solana-local :chain/solana-mainnet) (SolanaTransport. rpc)
                     :chain/facade (FacadeTransport. rpc))
        app-helper (AppHelper. #js {:transport transport
                                    :storage js/window.localStorage})]
    (when succ-event
      (re-frame/dispatch [succ-event {:app-helper app-helper
                                      :transport  transport}]))))

(re-frame/reg-fx :init-app-helper init-app-helper)

(defn- create-profile
  "Create player profile."
  [{:keys [nick pfp ^js app-helper ^js wallet-adapter succ-event] :as params}]
  {:pre [(string? nick)
         (not (str/blank? nick))
         (some? app-helper)
         (some? wallet-adapter)]}
  (js/console.log "Create profile" params)
  (a/go
    (<p! (.createProfile app-helper wallet-adapter nick pfp))
    ;; Due to RPC cache, we have to wait some seconds
    (when-not goog.DEBUG
      (a/<! (a/timeout 5000)))
    (when succ-event
      (let [addr (.-walletAddr wallet-adapter)]
        (re-frame/dispatch [succ-event {:addr addr :nick nick :pfp pfp}])))))

(re-frame/reg-fx :create-profile create-profile)

(defn- get-profile
  "Get player's profile by its address."
  [{:keys [^js app-helper addr]}]
  {:pre [(string? addr)
         (some? app-helper)]}
  (re-frame/dispatch [::set-profile-loading addr])
  (a/go
    (if-let [profile (<p! (.getProfile app-helper addr))]
      (let [profile (->clj profile)]
        (re-frame/dispatch [::update-profile
                            {:addr addr
                             :nick (.-nick profile)
                             :pfp  (.-pfp profile)}]))
      (re-frame/dispatch [::remove-profile addr]))))

(re-frame/reg-fx :fetch-profile get-profile)

(defn- list-games
  "List games."
  [{:keys [^js app-helper chain succ-event] :as params}]
  {:pre [(some? app-helper)
         (keyword? chain)]}
  (js/console.log "List games" params)
  (a/go
    (let [reg-addrs (c/chain->reg-addrs chain)
          games (<p! (.listGames app-helper (->js reg-addrs)))]
      (when succ-event
        (re-frame/dispatch [succ-event (js->clj games)])))))

(re-frame/reg-fx :list-games list-games)

(defn- list-nfts
  [{:keys [^js app-helper succ-event wallet-addr] :as params}]
  {:pre [(some? app-helper)
         (string? wallet-addr)]}
  (js/console.log "List NFTs" params)
  (a/go
    (let [nfts (<p! (.listNfts app-helper wallet-addr))]
      (js/console.log "nfts: " nfts)
      (when succ-event
        (re-frame/dispatch [succ-event wallet-addr (mapv ->clj nfts)])))))

(re-frame/reg-fx :list-nfts list-nfts)

(defn- get-nft
  [{:keys [^js app-helper succ-event addr] :as params}]
  {:pre [(some? app-helper)
         (string? addr)]}
  (a/go
    (when-let [nft (<p! (.getNft app-helper addr))]
      (when succ-event
        (re-frame/dispatch [succ-event (->clj nft)])))))

(re-frame/reg-fx :get-nft get-nft)

;;; Subs

(re-frame/reg-sub ::app-helper :-> ::app-helper)

(defn profile [db addr]
  (get-in db [::profiles addr]))

(re-frame/reg-sub ::profile :=> profile)

(re-frame/reg-sub ::profiles-map :-> ::profiles)

(re-frame/reg-sub ::my-profile
  :<- [::profiles-map]
  :<- [::wallet/addr]
  (fn [[m addr]]
    (get m addr)))

(re-frame/reg-sub ::games :-> ::games)

(defn nfts-by-owner [db owner]
  (get-in db [::nfts :by-owner owner]))

(re-frame/reg-sub ::nfts-by-owner :=> nfts-by-owner)

(defn nft-by-addr [db addr]
  (get-in db [::nfts :by-addr addr]))

(re-frame/reg-sub ::nft-by-addr :=> nft-by-addr)

;;; Events

(re-frame/reg-event-fx
  ::fetch-profile
  [re-frame/trim-v]
  (fn [{:keys [db]} [addr]]
    (when-not (get-in db [::profiles addr])
      {:fetch-profile {:app-helper (::app-helper db)
                       :addr       addr}})))

(re-frame/reg-event-db
  ::update-app-helper
  [re-frame/debug re-frame/unwrap]
  (fn update-app-helper
    [db {:keys [app-helper transport]}]
    (assoc db
           ::app-helper app-helper
           ::transport transport)))

(re-frame/reg-event-fx
  ::init-app-helper
  (fn init-app-helper
    [_ _]
    {:init-app-helper {:chain      c/chain
                       :succ-event ::update-app-helper}}))

(re-frame/reg-event-fx
  ::create-profile
  [re-frame/unwrap]
  (fn [{:keys [db]} {:keys [nick pfp]}]
    (let [app-helper     (::app-helper db)
          wallet-adapter (::wallet/wallet db)]
      {:create-profile {:app-helper     app-helper
                        :wallet-adapter wallet-adapter
                        :nick           nick
                        :pfp            pfp
                        :succ-event     ::update-profile}})))

(re-frame/reg-event-db
  ::remove-profile
  [re-frame/trim-v]
  (fn remove-profile
    [db [addr]]
    (update db ::profiles dissoc addr)))

(re-frame/reg-event-db
  ::update-profile
  [re-frame/unwrap]
  (fn update-profile
    [db {:keys [addr nick pfp]}]
    (assoc-in db [::profiles addr] {:nick nick
                                    :pfp  pfp})))

(re-frame/reg-event-db
  ::unset-profile-loading
  [re-frame/trim-v]
  (fn unset-profile-loading
    [db [addr]]
    (cond-> db
      (= :loading (get-in db [::profiles addr]))
      (update ::profiles dissoc addr))))

(re-frame/reg-event-fx
  ::set-profile-loading
  [re-frame/trim-v]
  (fn set-profile-loading
    [{:keys [db]} [addr]]
    {:db (assoc-in db [::profiles addr] :loading)
     :dispatch-later [{:ms 3000
                       :dispatch [::unset-profile-loading addr]}]}))

(re-frame/reg-event-db
  ::update-games
  [re-frame/debug re-frame/trim-v]
  (fn [db [games]]
    (assoc db ::games games)))

(re-frame/reg-event-fx
  ::list-games
  (fn list-games
    [{:keys [db]} _]
    (let [app-helper (::app-helper db)]
      {:list-games {:app-helper app-helper
                    :chain      c/chain
                    :succ-event ::update-games}})))

(re-frame/reg-event-fx
  ::list-nfts
  [re-frame/trim-v]
  (fn list-nfts
    [{:keys [db]} [addr]]
    (let [app-helper (::app-helper db)]
      {:list-nfts {:app-helper app-helper
                   :wallet-addr addr
                   :succ-event ::add-nfts-by-owner}})))

(re-frame/reg-event-fx
  ::fetch-nft
  [re-frame/trim-v]
  (fn fetch-nft
    [{:keys [db]} [addr]]
    (let [app-helper (::app-helper db)]
      {:get-nft {:app-helper app-helper
                 :addr addr
                 :succ-event ::add-nft}})))

(re-frame/reg-event-db
  ::add-nft
  [re-frame/trim-v]
  (fn add-nft [db [nft]]
    (assoc-in db [::nfts :by-addr (:addr nft)] nft)))

(re-frame/reg-event-db
  ::add-nfts-by-owner
  [re-frame/trim-v]
  (fn add-nfts [db [owner nfts]]
    (let [by-address (->> nfts
                          (map (juxt :addr identity))
                          (into {}))]
      (-> db
          (update-in [::nfts :by-addr] merge by-address)
          (update-in [::nfts :by-owner owner] (fnil into []) nfts)))))
