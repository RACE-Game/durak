(ns durak.utils
  (:require [clojure.string :as str]
            [cljs-bean.core :as bean]
            [camel-snake-kebab.core :as csk]))

(defn rotate-by
  [pred xs]
  (loop [rotated []
         remain  xs]
    (if (empty? remain)
      rotated
      (if (pred (first remain))
        (concat remain rotated)
        (recur (conj rotated (first remain)) (next remain))))))

(defn bean [x]
  (bean/bean x
             :recursive true
             :prop->key csk/->kebab-case-keyword
             :key->prop csk/->camelCaseString))

(defn format-addr [addr]
  (when addr
    (let [l (count addr)]
      (str
       (subs addr 0 4)
       ".."
       (subs addr (- l 4) l)))))

(defn parse-query-params
  "Parse URL parameters into a hashmap"
  []
  (let [param-strs (-> (.. js/window -location -href)
                       (str/split #"\?")
                       last
                       (str/split #"\&"))]
    (into {} (for [[k v] (map #(str/split % #"=") param-strs)]
               [(keyword k) v]))))

(defn map->clj
  "Convert js/Map to clj hash-map."
  [m]
  (->> (for [[k v] m] [k v]) (into {})))
