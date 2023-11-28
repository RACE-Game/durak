(ns durak.controls.wallet
  (:require [re-frame.core :as re-frame]
            [durak.utils :as u]
            [durak.constants :as c]
            [durak.controls.helper :as-alias helper]
            [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer [<p!]]
            ["@solana/wallet-adapter-phantom" :refer [PhantomWalletAdapter]]
            ["@solana/wallet-adapter-solflare" :refer [SolflareWalletAdapter]]
            ["@race-foundation/sdk-solana" :as SdkSolana :refer [SolanaWalletAdapter]]
            ["@race-foundation/sdk-facade" :refer [FacadeWallet]]))

(re-frame/reg-fx
 :connect-wallet
 (fn [{:keys [chain wallet-key succ-event]}]
   (let [cb (fn [pk wrapped]
              (when succ-event
                (re-frame/dispatch [succ-event pk wrapped])))]
     (js/console.log "Connect wallet, chain:" chain ", wallet-key:" wallet-key)
     (case chain
       (:chain/solana-local :chain/solana-mainnet)
       (go
         (let [[adapter _wrapped] (case wallet-key
                                    :solana-solflare
                                    (let [adapter (SolflareWalletAdapter.)
                                          wrapped (SolanaWalletAdapter. adapter)]
                                      (.on adapter "connect" (fn [pk] (cb pk wrapped)))
                                      [adapter wrapped])

                                    :solana-phantom
                                    (let [adapter (PhantomWalletAdapter.)
                                          wrapped (SolanaWalletAdapter. adapter)]
                                      (.on adapter "connect" (fn [pk] (cb pk wrapped)))
                                      [adapter wrapped]))]
           (<p! (.connect adapter))))

       :chain/facade
       (let [pk (or
                 (:facade (u/parse-query-params))
                 (str (random-uuid)))
             wrapped (FacadeWallet. pk)]
         (cb pk wrapped))

       (throw (ex-info "Unsupported chain" {:chain chain}))))))

(re-frame/reg-sub ::wallet :-> ::wallet)

(re-frame/reg-sub ::addr :-> ::addr)

(re-frame/reg-event-fx
 ::set-wallet
 (fn [{:keys [db]} [_ pk wallet]]
   (let [addr (str pk)]
     {:db (assoc db
                 ::addr addr
                 ::wallet wallet)
      :dispatch-n (cond-> [[::helper/fetch-profile addr]]
                    c/facade-auto-create-profile
                    (conj [::helper/create-profile {:nick addr :pfp "nft01"}]))})))

(re-frame/reg-event-db
 ::disconnect
 (fn [db _]
   (dissoc db ::wallet ::addr)))

(re-frame/reg-event-fx
 ::connect
 (fn [{:keys [db]} _]
   (let [wallet (::wallet db)]
     (when-not (and wallet (.-isConnected wallet))
       {:connect-wallet {:chain c/chain
                         :wallet-key c/wallet-key
                         :succ-event ::set-wallet}}))))
