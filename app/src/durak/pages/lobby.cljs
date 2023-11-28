(ns durak.pages.lobby
  (:require [re-frame.core :as re-frame]
            [reagent.core :as r]
            [durak.controls.ux :as ux]
            [durak.controls.wallet :as wallet]
            [durak.controls.helper :as helper]
            [durak.controls.router :as router]
            [durak.utils :as u]
            [clojure.string :as str]))

(defn on-display-nfts [addr]
  (re-frame/dispatch [::helper/list-nfts addr])
  (re-frame/dispatch [::ux/set ::display-nfts true]))

(defn on-open-create-profile-modal []
  (-> (js/document.getElementById "create-profile")
      (.showModal)))

(defn on-create-profile [form]
  (re-frame/dispatch [::helper/create-profile form])
  (-> (js/document.getElementById "create-profile")
      (.close)))

(defn on-join [addr]
  (re-frame/dispatch [::router/push [:game {:addr addr}]]))

(defn header []
  (let [addr    (re-frame/subscribe [::wallet/addr])
        profile (re-frame/subscribe [::helper/my-profile])]
    (fn []
      [:div {:class "navbar bg-base-200 justify-between gap-8"}
       [:a {:class "btn btn-ghost text-xl"} "durak.sol"]
       [:div {:class "flex-1"}]

       (cond
         (not @addr) nil
         (= :loading @profile) [:div {:class "loading loading-spinner loading-lg"}]

         (not @profile)
         [:button {:class "btn btn-secondary btn-outline"
                   :on-click on-open-create-profile-modal}
          "Create Profile"]

         :else
         (let [{:keys [nick pfp]} @profile]
           [:button {:class "btn btn-ghost btn-outline font-bold text-lg"
                     :on-click on-open-create-profile-modal}
            nick]))

       (if-let [addr @addr]
         [:div {:class "dropdown dropdown-end dropdown-hover"}
          [:label {:class "btn btn-neutral"}
           [:i.fa-sharp.fa-regular.fa-wallet {:class "text-xl"}]
           (u/format-addr addr)]
          [:ul {:tab-index "0"
                :class "dropdown-content z-[1] menu shadow bg-base-100 w-52"}
           [:li [:a {:on-click #(re-frame/dispatch [::wallet/disconnect])}
                 "Disconnect"]]]]
         [:button {:class "btn btn-primary btn-outline"
                   :on-click #(re-frame/dispatch [::wallet/connect])}
          [:i.fa-sharp.fa-regular.fa-wallet {:class "text-xl"}]
          "Connect"])])))

(defn game-entry [game sel?]
  (let [game (u/bean game)
        {:keys [max-players title players addr]} game
        num-of-players (count players)]
    [:div {:class (str "bg-base-100 border-base-content flex gap-4 p-8 active:scale-[95%] transition-all"
                       (if sel?
                         " !border-accent !bg-accent !text-accent-content"
                         " hover:border-primary cursor-pointer"))
           :on-click #(re-frame/dispatch-sync [::ux/set ::selected-game addr])}
     [(case max-players
        2 :i.fa-sharp.fa-regular.fa-user-group
        3 :i.fa-sharp.fa-regular.fa-users)
      {:class "text-xl"}]
     [:div title]
     [:div {:class "flex-1 text-right"} (str num-of-players "/" max-players)]]))

(defn create-profile-modal []
  (let [profile  (re-frame/subscribe [::helper/my-profile])
        display-nfts? (re-frame/subscribe [::ux/get ::display-nfts])
        addr     (re-frame/subscribe [::wallet/addr])
        form     (r/atom {:nick ""
                          :pfp  ""})]
    (fn []
      (let [display-nfts? @display-nfts?
            addr @addr
            nfts @(re-frame/subscribe [::helper/nfts-by-owner addr])]
        [:dialog#create-profile {:class "modal"}
         [:div#create-profile {:class "modal-box"}
          [:h3 {:class "font-bold text-lg"} "Create On-chain Profile"]
          [:div {:class "form-control"}
           [:label {:class "label"}
            [:span {:class "label-text"}] "What's your name?"]
           [:input {:class       "input input-bordered w-full"
                    :placeholder "Nick name displayed in game"
                    :on-change   (fn [e] (swap! form assoc :nick (.. e -target -value)))
                    :value       (:nick @form)}]
           [:label {:class "label"}
            [:span {:class "label-text"}] "Pick an avatar?"]
           (if display-nfts?
             [:div {:class "flex flex-wrap gap-4"}
              (doall
               (for [nft nfts
                     :let [sel? (= (:addr nft) (:pfp @form))]]
                 ^{:key (:addr nft)}
                 [:div {:class "avatar"
                        :on-click #(swap! form assoc :pfp (:addr nft))}
                  [:div {:class (str "w-24 rounded-full ring " (if sel? "ring-primary" "ring-ghost cursor-pointer"))}
                   [:img {:src (:image nft)}]]]))]
             [:button {:class    "btn btn-base-100"
                       :on-click (partial on-display-nfts addr)}
              "Display my collections"])
           [:div {:class "modal-action"}
            (if (str/blank? (:nick @form))
              [:button {:class    "btn btn-ghost"
                        :disabled true}
               "Create"]
              [:button {:class    "btn btn-primary"
                        :on-click (partial on-create-profile @form)}
               "Create"])]]]]))))

(defn lobby-page []
  (let [games    (re-frame/subscribe [::helper/games])
        sel-game (re-frame/subscribe [::ux/get ::selected-game])]
    (fn []
      [:div {:class "w-screen min-h-screen bg-base-300 flex flex-col"}
       [header]
       [:div {:class "hero flex-1"}
        [create-profile-modal]
        [:div {:class "card w-96 bg-base-200/75 shadow-xl backdrop-blur-sm"}
         [:div {:class "card-body"}
          [:h2 {:class "card-title"} "Welcome to Durak!"]
          (doall
           (for [game @games]
             ^{:key (.-addr game)}
             [game-entry game (= (.-addr game) @sel-game)]))
          [:p {:class "text-sm text-center text-neutral"}
           [:span "* Built with "]
           [:a {:class "text-info" :href "https://solana.com/" :target "_blank"} "Solana"]
           [:span " and "]
           [:a {:class "text-warning" :href "https://github.com/RACE-Game/race" :target "_blank"} "Race Protocol"]]
          [:div {:class "card-actions justify-end"}
           (if @sel-game
             [:button {:class "btn btn-accent btn-outline px-12"
                       :on-click (partial on-join @sel-game)}
              "Join"]
             [:button {:class "btn btn-ghost btn-outline px-12" :disabled true} "Join"])]]]]])))
