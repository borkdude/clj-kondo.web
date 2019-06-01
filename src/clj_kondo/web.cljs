(ns ^:figwheel-hooks clj-kondo.web
  (:require
   [ajax.core :as ajax]
   [cljsjs.codemirror]
   [cljsjs.codemirror.addon.lint.lint]
   [cljsjs.codemirror.mode.clojure]
   [cljsjs.parinfer]
   [cljsjs.parinfer-codemirror]
   [clojure.string :as str]
   [reagent.core :as r])
  (:import [goog Uri]))

(defonce app-state (r/atom {}))
(defonce editors (atom {}))

(defn editor [id path]
  (r/create-class
   {:render (fn [] [:textarea
                    {:type "text"
                     :id id
                     :default-value (get-in @app-state path)
                     :auto-complete "off"}])
    :component-did-mount
    (fn [this]
      (let [opts #js {:mode "clojure"
                      :matchBrackets true
                      ;;parinfer does this better
                      ;;:autoCloseBrackets true
                      :lineNumbers true
                      :lint true
                      :gutters #js ["CodeMirror-lint-markers"]}
            cm (.fromTextArea js/CodeMirror
                              (r/dom-node this)
                              opts)]
        (.on cm "change"
             (fn [x]
               (let [v (.getValue x)]
                 (swap! app-state assoc-in path v))))
        (js/parinferCodeMirror.init cm)
        (.removeKeyMap cm)
        (.setOption cm "extraKeys" #js {:Shift-Tab false
                                        :Tab false})
        (swap! editors assoc-in path cm)))
    :component-will-unmount
    (fn []
      (let [cm (get-in @editors path)]
        ;; toTextArea will destroy and clean up cm
        (.toTextArea cm)))}))

(defn app []
  [:div#clj-kondo.container
   [:div.row
    [:p.col-12.lead
     [:span [:a {:href "https://github.com/borkdude/clj-kondo"
                 :target "_blank"}
             "clj-kondo"]
      " playground"]]]
   [:div.field.col-md-10.col-sm-9
    [editor "args" [:args]]]])

(defn mount [el]
  (r/render-component [app] el))

(defn register-clj-kondo-linter []
  (js/CodeMirror.registerHelper
   "lint" "clojure"
   (fn [text _opts]
     (js/Promise.
      (fn [resolve reject]
        (ajax/POST
         "https://re-find.it/clj-kondo/index.php"
         {:format :url
          :params {:code text}
          :handler
          (fn [response]
            (let [results
                  (into-array
                   (for [line (str/split-lines response)
                         :let [[_ file row col level message]
                               (re-matches #"(.+):(\d+):(\d+): (\w+): (.*)" line)]
                         :when message]
                     (let [[row col] [(js/parseInt row) (js/parseInt col)]
                           row (dec row)
                           col (dec col)
                           start-pos (js/CodeMirror.Pos row col)
                           token (.getTokenAt (get-in @editors [:args])
                                              #js {:line row
                                                   :ch (inc col)} true)
                           end-pos (js/CodeMirror.Pos row (.-end token))]
                       #js {:message message
                            :from start-pos
                            :to end-pos
                            :severity level})))]
              (resolve results)))}))))))

(defn mount-app-element []
  (register-clj-kondo-linter)
  (when-let [el (js/document.getElementById "app")]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
