(ns durak.pages.game
  (:require [durak.components.card :as card]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [durak.controls.client :as client]
            [durak.controls.helper :as helper]
            [durak.controls.game :as game]
            [durak.controls.ux :as ux]
            [durak.controls.wallet :as wallet]
            [durak.utils :as u]
            [durak.types :as types]))

(def deck-len 36)

(defn- clear-ux []
  (re-frame/dispatch [::ux/unset ::sels])
  (re-frame/dispatch [::ux/unset ::sel]))

(defn enter-game-page [params]
  (let [addr (get-in params [:path :addr])]
    (re-frame/dispatch [::wallet/connect])
    (re-frame/dispatch [::game/attach addr])))

(defn leave-game-page []
  (re-frame/dispatch [::client/close-client]))

(defn on-join []
  (re-frame/dispatch [::client/join (js/BigInt 1000000)]))

(defn sort-players-by-relative-position [curr-position players]
  (->> (vals players)
       (sort-by :position)
       (u/rotate-by #(= curr-position (:position %)))))

(defonce countdown-trigger (r/atom false))
(js/setInterval #(swap! countdown-trigger not) 10)
(defn render-countdown [state]
  (let [{:keys [timeout stage]} state
        secs (quot (- (js/Number timeout) (.getTime (js/Date.))) 1000)]
    @countdown-trigger
    (when (not= :stage/end-of-game stage)
      (when-not (<= secs 0)
        [:div {:class "absolute bottom-16 right-16"}
         [:span {:class "countdown font-mono text-3xl"}
          [:i.fa-sharp.fa-regular.fa-hourglass]
          [:span {:style {"--value" secs}}]]]))))

(defn waiting-header []
  [:div {:class "navbar bg-base-200 justify-between gap-8"}
   [:a {:class "btn btn-ghost text-xl"
        :href "#/"}
    [:i.fa-sharp.fa-regular.fa-bars {:class "mr-2"}] "durak.sol"]])

(defn playing-header [stage]
  [:div {:class "navbar bg-base-200 justify-between gap-8"}
   [:a {:class "btn btn-ghost text-xl"
        :href "#/"}
    [:i.fa-sharp.fa-regular.fa-bars {:class "mr-2"}] "durak.sol"]
   [:div {:class "uppercase"} (name stage)]])

(defn render-avatar [profile & [player player-action]]
  (let [{:keys [nick pfp]} profile
        {:keys [role]} player
        nft @(re-frame/subscribe [::helper/nft-by-addr pfp])
        tag-css "absolute bottom-4 left-1/2 -translate-x-1/2 text-xs font-mono uppercase px-3 rounded-md "]
    [:div {:class "flex flex-col items-center w-36 text-2xl text-white relative"}
     [:div {:class "avatar"}
      [:div {:class (str "w-24 rounded-full bg-base-100 "
                         (case role
                           :role/attacker "ring ring-primary"
                           :role/defender "ring ring-secondary"
                           :role/co-attacker "ring ring-accent"
                           ""))}
       [:img {:src (:image nft)}]]]
     [:div {:class "absolute right-full top-0 w-32"}
      (when player-action
        (case (types/action-type (:action player-action))
          :action/beated [:div {:class "chat chat-end"} [:div {:class "chat-bubble chat-bubble-primary"} "It's beated"]]
          :action/take [:div {:class "chat chat-end"} [:div {:class "chat-bubble chat-bubble-secondary"} "I'm taking"]]
          :action/forward [:div {:class "chat chat-end"} [:div {:class "chat-bubble chat-bubble-accent"} "I'm forwarding"]]
          nil))]
     (case role
       :role/attacker [:div {:class (str tag-css "bg-primary text-primary-content")} "ATT"]
       :role/defender [:span {:class (str tag-css "bg-secondary text-secondary-content")} "DEF"]
       :role/co-attacker [:span {:class (str tag-css "bg-accent text-accent-content")} "COATT"]
       nil)
     [:div {:class "text-ellipsis text-xs w-40 overflow-hidden whitespace-nowrap text-neutral"} nick]]))

(def kind->value {"2" 2, "3" 3, "4" 4, "5" 5, "6" 6, "7" 7, "8" 8, "9" 9, "t" 10, "j" 11, "q" 12, "k" 13, "a" 14})
(defn- sort-card [c]
  (when c
    [(subs c 0 1) (kind->value (subs c 1 2))]))

(defn render-action-panel-attacker [state player profile player-action]
  (let [decryption          @(re-frame/subscribe [::client/decryption (:random-id state)])
        sels                @(re-frame/subscribe [::ux/get ::sels #{}])
        {:keys [attacks attack-space]}   state
        {:keys [card-idxs]} player
        cards               (->> (map #(vector (get decryption %) %) card-idxs)
                                 (sort-by (comp sort-card first)))
        attack-kinds        (->> (mapcat (fn [att]
                                           (case (types/attack-type att)
                                             :attack/open   [(types/kind (:open att))]
                                             :attack/closed [(types/kind (:open att))
                                                             (types/kind (:close att))]
                                             nil))
                                         attacks)
                                 (into #{}))
        card-valid?         (fn [card]
                              (and (< (count sels) attack-space)
                                   (< (+ (count attacks) (count sels)) 6)
                                   (or (empty? attacks)
                                       (get attack-kinds (types/kind card)))
                                   (or (empty? sels)
                                       (= (types/kind (first sels)) (types/kind card)))))
        all-attacks-closed  (every? (comp #{:attack/closed} types/attack-type) attacks)]
    [:div {:class "p-8 absolute bottom-0 inset-x-0 flex flex-col items-stretch"}
     [:div {:class "h-16 flex justify-center items-center"}
      (when (seq sels)
        [:button {:class    "btn btn-accent"
                  :on-click (fn []
                              (re-frame/dispatch [::game/attack sels])
                              (clear-ux))}
         "Attack"])]
     [:div {:class "h-44 flex justify-center items-center gap-2"}
      (for [[c card-idx] cards
            :when        c
            :let         [card (types/->Card card-idx c)
                          selected? (sels card)
                          disabled? (not (card-valid? card))]]
        ^{:key card-idx}
        [card/card c
         :css (cond
                selected?
                "-translate-y-8 cursor-pointer active:scale-[90%]"

                disabled?
                "brightness-75"

                :else
                "hover:-translate-y-2 cursor-pointer active:scale-[90%] transition-all")
         :on-click (cond
                     selected?
                     #(re-frame/dispatch [::ux/set ::sels (disj sels card)])

                     disabled?
                     nil

                     :else
                     #(re-frame/dispatch [::ux/set ::sels (conj sels card)]))])]
     [:div {:class "h-24 flex justify-center items-center gap-4"}
      [render-avatar profile player player-action]
      [:div {:class "w-32"}
       (when (and (seq attacks)
                  all-attacks-closed)
         [:button {:class    "btn btn-primary text-2xl px-16"
                   :on-click #(re-frame/dispatch [::game/beated])}
          "Beated"])]]]))

(defn render-action-panel-co-attacker [state player profile player-action]
  (let [decryption          @(re-frame/subscribe [::client/decryption (:random-id state)])
        sels                @(re-frame/subscribe [::ux/get ::sels #{}])
        {:keys [attacks attack-space]}   state
        {:keys [card-idxs]} player
        cards               (->> (map #(vector (get decryption %) %) card-idxs)
                                 (sort-by (comp sort-card first)))
        attack-kinds        (->> (mapcat (fn [att]
                                           (case (types/attack-type att)
                                             :attack/open   [(types/kind (:open att))]
                                             :attack/closed [(types/kind (:open att))
                                                             (types/kind (:close att))]
                                             nil))
                                         attacks)
                                 (into #{}))
        card-valid?         (fn [card]
                              (and (< (count sels) attack-space)
                                   (or (empty? attacks)
                                       (get attack-kinds (types/kind card)))
                                   (or (empty? sels)
                                       (= (types/kind (first sels)) (types/kind card)))))
        all-attacks-closed  (every? (comp #{:attack/closed} types/attack-type) attacks)]
    [:div {:class "p-8 absolute bottom-0 inset-x-0 flex flex-col items-stretch"}
     [:div {:class "h-26 flex justify-center items-center"}
      (when (seq sels)
        [:button {:class    "btn btn-accent"
                  :on-click (fn []
                              (re-frame/dispatch [::game/co-attack sels])
                              (clear-ux))}
         "Attack"])]
     [:div {:class "h-44 flex justify-center items-center gap-2"}
      (for [[c card-idx] cards
            :when        c
            :let         [card (types/->Card card-idx c)
                          selected? (sels card)
                          disabled? (not (card-valid? card))]]
        ^{:key card-idx}
        [card/card c
         :css (cond
                selected?
                "-translate-y-8 cursor-pointer active:scale-[90%]"

                disabled?
                "brightness-75"

                :else
                "hover:-translate-y-2 cursor-pointer active:scale-[90%] transition-all")
         :on-click (cond
                     selected?
                     #(re-frame/dispatch [::ux/set ::sels (disj sels card)])

                     disabled?
                     nil

                     :else
                     #(re-frame/dispatch [::ux/set ::sels (conj sels card)]))])]
     [:div {:class "h-24 flex justify-center items-center gap-4"}
      [render-avatar profile player player-action]
      [:div {:class "w-32"}
       (when (and (seq attacks)
                  all-attacks-closed)
         [:button {:class    "btn btn-primary text-2xl px-16"
                   :on-click #(do
                                (re-frame/dispatch [::game/beated])
                                (clear-ux))}
          "Beated"])]]]))

(defn render-action-panel-defender [state player profile player-action]
  (let [decryption              @(re-frame/subscribe [::client/decryption (:random-id state)])
        sel                     @(re-frame/subscribe [::ux/get ::sel])
        {:keys [attacks stage]} state
        {:keys [card-idxs]}     player
        cards                   (->> (map #(vector (get decryption %) %) card-idxs)
                                     (sort-by (comp sort-card first)))
        end-of-round            (= :stage/end-of-round stage)
        can-forward             (and (seq attacks)
                                     sel
                                     (every? (comp #{:attack/open} types/attack-type) attacks)
                                     (apply = (map types/kind (conj (map :open attacks) sel))))
        all-attacks-closed      (every? (comp #{:attack/closed} types/attack-type) attacks)
        no-confirming-attack    (every? (comp #{:attack/open :attack/closed} types/attack-type) attacks)]
    [:div {:class "p-8 absolute bottom-0 inset-x-0 flex flex-col items-stretch"}
     [:div {:class "h-20 flex justify-center items-center"}
      (when can-forward
        [:button {:class    "btn btn-accent"
                  :on-click (fn []
                              (re-frame/dispatch [::game/forward sel])
                              (clear-ux))}
         "Forward"])]
     [:div {:class "h-44 flex justify-center items-center gap-2"}
      (for [[c card-idx] cards
            :let         [card (types/->Card card-idx c)
                          selected? (= sel card)]]
        ^{:key card-idx}
        [card/card c
         :css (cond
                end-of-round
                "brightness-75"

                selected?
                "-translate-y-8 cursor-pointer"

                :else
                "hover:-translate-y-2 brightness-75 cursor-pointer transition-all")
         :on-click (cond
                     end-of-round
                     nil

                     selected?
                     #(re-frame/dispatch [::ux/unset ::sel])

                     :else
                     #(re-frame/dispatch [::ux/set ::sel card]))])]
     [:div {:class "h-24 flex justify-center items-center gap-4"}
      [render-avatar profile player player-action]
      [:div {:class "w-32"}
       (when (and (not end-of-round)
                  (seq attacks)
                  no-confirming-attack
                  (not all-attacks-closed))
         [:button {:class    "btn btn-secondary text-2xl px-16"
                   :on-click #(do (re-frame/dispatch [::game/take])
                                  (clear-ux))}
          "Take"])]]]))

(defn render-action-panel [state player profiles player-action]
  (case (:role player)
    :role/attacker [render-action-panel-attacker state player profiles player-action]
    :role/defender [render-action-panel-defender state player profiles player-action]
    :role/co-attacker [render-action-panel-co-attacker state player profiles player-action]
    nil))

(defn- cover? [card target trump]
  (let [trump-suit (types/suit trump)
        target-is-trump-suit? (= trump-suit (types/suit target))
        card-is-trump-suit? (= trump-suit (types/suit card))
        same-suit? (= (types/suit card) (types/suit target))
        greater-than? (> (kind->value (types/kind card))
                         (kind->value (types/kind target)))]
    (cond
      (and same-suit? greater-than?) true
      (and card-is-trump-suit? (not target-is-trump-suit?)) true
      :else false)))

(defn render-attack [i attack trump role]
  [:div {:class "relative"}
   (case (types/attack-type attack)
     :attack/confirm-open
     [card/card-loading]

     :attack/open
     (let [sel @(re-frame/subscribe [::ux/get ::sel])]
       (if (and (= role :role/defender) sel)
         (let [{:keys [value idx] :as open} (:open attack)]
           [card/card value
            :css (if (cover? (:value sel) open trump)
                   "brightness-100 transition-all active:scale-[90%] cursor-pointer"
                   "brightness-75 hover:brightness-100 transition-all active:scale-[90%%%]")
            :on-click (fn []
                        (re-frame/dispatch [::game/defend sel i])
                        (clear-ux))])
         [card/card (:value (:open attack))]))

     :attack/confirm-close
     [:<>
      [card/card (:value (:open attack))]
      [:div {:class "absolute top-8 left-8"}
       [card/card-loading]]]

     :attack/closed
     [:<>
      [card/card (:value (:open attack))]
      [:div {:class "absolute top-8 left-8"}
       [card/card (:value (:close attack))]]])])

(defn render-attack-list [attacks trump role]
  [:div {:class "absolute left-1/2 top-1/3 -translate-x-1/2 -translate-y-1/2 flex flex-wrap gap-12 content-center justify-start"}
   (for [[i attack] (map-indexed vector attacks)]
     ^{:key i}
     [render-attack i attack trump role])])

(defn render-player [rel-pos player profiles player-action]
  (let [{:keys [addr card-idxs]} player]
    [:div {:class (str "absolute  flex flex-col gap-4 items-center "
                       (case rel-pos
                         1 "top-8 left-8"
                         2 "top-8 left-1/2 -translate-x-1/2"
                         3 "top-8 right-8"))}
     [render-avatar (get profiles addr) player player-action]
     [card/deck (count card-idxs) 18]]))

(defn render-winner-popup [state profiles]
  (let [{:keys [stage players]} state]
    (when (= :stage/end-of-game stage)
      (let [winner (->> players
                        vals
                        (filter #(= 0 (:rank %)))
                        (first))
            profile (get profiles (:addr winner))]
        [:div {:class "absolute inset-0 grid place-items-center bg-black/25"}
         [:div {:class "text-2xl w-96 h-64 backdrop-blur-sm shadow-lg bg-primary/75 rounded-md bg-primary-content grid place-items-center"}
          [:div "Congratulations to the Winner"]
          [render-avatar profile]]]))))

(defn render-deck [{:keys [deck-offset trump]}]
  (let [n (- deck-len deck-offset 1)]
    [:div {:class "absolute top-1/2 left-8"}
     [:div {:class "absolute -top-16 left-0"}
      (when-let [v (:value trump)]
        [card/card v])]
     [:div {:class "absolute top-0 left-8"}
      [card/deck n 6]]
     [:div {:class "absolute w-16 text-center -top-10 left-32 z-[99] bg-base-200 rounded-full"}
      [:span {:class "countdown font-mono text-3xl"}
       [:span {:style {"--value" n}}]]]]))

(defn render-waiting-page [{:keys [profiles addr state]}]
  (let [{:keys [players num-of-players]} state]
    [:div {:class "min-h-screen w-full bg-cover bg-center bg-base-300 flex flex-col items-stretch"}
     [waiting-header]
     [:div {:class "hero flex-1"}
      [:div {:class "hero-content flex flex-col gap-16 items-center"}
       [:div {:class "leading-loose text-xl"}
        [:span {:class "mr-4"} "PLAYERS"]
        [:span (str  (count players) " / " num-of-players)]]
       [:div {:class "flex flex-col gap-4"}
        (for [[addr _player] players]
          ^{:key addr}
          [:div {:class "border rounded-sm w-96 p-4 flex justify-between"}
           [render-avatar (get profiles addr)]
           [:div (u/format-addr addr)]])]
       (when-not (get players addr)
         [:button {:class    "btn btn-primary px-8"
                   :on-click on-join}
          "Join the game with 1 USDC"])]]]))

(defn render-playing-page [{:keys [profiles addr state displays]}]
  (let [{:keys [stage num-of-players players deck-offset trump attacks]} state
        curr-position  (get-in players [addr :position] 0)
        sorted-players (sort-players-by-relative-position curr-position players)
        rest-players   (next sorted-players)
        rel-pos-list   (case num-of-players
                         2 [2]
                         3 [1 3]
                         4 [1 2 3])
        curr-player    (first sorted-players)
        curr-profile   (get profiles (:addr curr-player))
        display-map    (->> (vals displays)
                            (group-by types/display-type))
        {:display/keys [deal-cards player-action]} display-map
        curr-player-action (first (filter #(= (:addr curr-player) (:addr %)) player-action))]
    [:div {:class "min-h-screen w-full bg-cover bg-center bg-base-300 flex flex-col items-stretch"}
     [playing-header stage]
     [:div {:class "flex-1 relative"}
      (for [[rel-pos player] (map vector rel-pos-list rest-players)
            :let [player-action (first (filter #(= (:addr player) (:addr %)) player-action))]]
        ^{:key (str rel-pos (:addr player))}
        [render-player rel-pos player profiles player-action])
      [render-action-panel state curr-player curr-profile curr-player-action]
      [render-attack-list attacks trump (:role curr-player)]
      [render-countdown state]
      [render-deck {:deck-offset deck-offset :trump trump}]
      [render-winner-popup state profiles]]]))

(defn game-page []
  (let [addr     (re-frame/subscribe [::wallet/addr])
        profiles (re-frame/subscribe [::helper/profiles-map])
        state    (re-frame/subscribe [::game/state])
        displays (re-frame/subscribe [::game/displays])]
    (fn []
      (let [state    @state
            profiles @profiles
            addr     @addr
            displays @displays]
        (if (or (nil? state) (= :stage/waiting (:stage state)))
          [render-waiting-page {:addr addr, :profiles profiles, :state state}]
          [render-playing-page {:addr addr, :profiles profiles, :state state, :displays displays}])))))
