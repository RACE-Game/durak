(ns durak.components.card
  (:require [goog.string :refer [format]]))

(defn card-loading [c]
  [:div {:class "w-[5rem] h-[7.5rem] rounded-lg"}
   [:div {:class "loading loading-spinner loading-lg"}]])

(defn card-back []
  [:div {:class "w-[5rem] h-[7.5rem] rounded-lg"
         :style {:background-image    "url('/assets/card.png')"
                 :background-size     "1300% 500%",
                 :background-position "-200% -400%"}}])

(defn deck [n base-offset]
  (let [w (format "calc(5rem + (%s * %spx))" (inc n) base-offset)]
    [:div {:class "h-[10.5rem] relative"
           :style {:width w}}
     (when (pos? n)
       (for [i (range n)]
         ^{:key i}
         [:div {:class "absolute top-0 left-0"
                :style {:z-index (- 52 i)
                        :transform (str "translateX(" (* i base-offset) "px)")}}
          [card-back]]))]))

(defn parse-kind-offset
  [kind]
  (case kind
    "a" 0
    "2" 1
    "3" 2
    "4" 3
    "5" 4
    "6" 5
    "7" 6
    "8" 7
    "9" 8
    "t" 9
    "j" 10
    "q" 11
    "k" 12
    (throw (ex-info "Invalid kind" {}))))

(defn parse-suit-offset
  [suit]
  (case suit
    "s" 3
    "h" 2
    "c" 0
    "d" 1))

(defn card [c & {:keys [on-click css]}]
  (when c
    (let [kind     (subs c 1 2)
          suit     (subs c 0 1)
          h-offset (parse-kind-offset kind)
          w-offset (parse-suit-offset suit)]
      [:div {:class "w-[5rem] h-[7.5rem] relative"}
       [:div {:class    (str "absolute top-0 left-0 flex justify-center items-center w-[5rem] h-[7.5rem] transition-all " css)
              :on-click on-click
              :style    {:background-image    "url('/assets/card.png')"
                         :background-size     "1300% 500%",
                         :background-position (format "-%s%% -%s%%"
                                                      (* h-offset 100)
                                                      (* w-offset 100))}}]])))
