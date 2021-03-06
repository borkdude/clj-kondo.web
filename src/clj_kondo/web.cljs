(ns ^:figwheel-hooks clj-kondo.web
  (:require
   [ajax.core :as ajax]
   [applied-science.js-interop :as j]
   [cljsjs.codemirror]
   [cljsjs.codemirror.addon.edit.matchbrackets]
   [cljsjs.codemirror.addon.lint.lint]
   [cljsjs.codemirror.mode.clojure]
   [cljsjs.parinfer]
   [cljsjs.parinfer-codemirror]
   [clojure.string :as str]
   [reagent.core :as r]))

(def initial-code (str/trim "
(ns foo
  (:require
    [clojure.string :as str]
    ;; We're never using this namespace. Also, the namespaces aren't sorted.
    [clojure.set :as set]))

;; Here we made a typo, so the symbol is unresolved:
(but-last [1 2 3])

;; Clj-kondo knows about arities of clojure namespaces, but you can also teach
;; it about your libraries or own namespaces
(str/join)

;; foo has an arity of 2, but we're not actually using y
(defn foo-fn [x y]
  ;; this do is redundant:
  (do
    ;; this is handy for debugging, but please remove it before pushing your code:
    (def tmp_x x)
    (let [y (fn [] (inc x))]
      ;; the next let can be squashed together with the previous:
      (let [z y]
        ;; whoopsy, calling a local function with an incorrect number of args:
        (y x)
        ;; also wrong:
        (recur)))))

(letfn
  [(f [] (h 1))
   (h [] (f 1))])

(defn- private-fn [])
;; redefining it...
(defn- private-fn [])

(defn foo [] :foo)
;; Type error, because foo doesn't return a number!
(inc (foo))

;; I'm tired now, let's sleep...
;; Oops, not happening because of wrong amount of args:
(Thread/sleep 1000 1 2)

;; Here we switch to another namespace and require the previous:
(ns bar (:require [foo :as f]))

;; Wrong arity when calling a function from the previous namespace:
(f/foo-fn)

;; private:
(f/private-fn)

;; this won't pass the reader:
{:a 1 :a 2}
;; and neither will this:
#{1 1}
;; nor this:
{:a 1 :b}

(ns bar-test (:require [clojure.test :as t]))

(t/deftest my-tests
  ;; you're not actually testing something here:
  (odd? (inc 1)))
"))

(defonce editor-ref (atom nil))

(defn editor [id path]
  (r/create-class
   {:render (fn [] [:textarea
                    {:type "text"
                     :id id
                     :default-value initial-code
                     :auto-complete "off"}])
    :component-did-mount
    (fn [this]
      (let [opts #js {:mode "clojure"
                      :matchBrackets true
                      ;;parinfer does this better
                      ;;:autoCloseBrackets true
                      :lineNumbers true
                      :lint #js {:lintOnChange false}
                      :gutters #js ["CodeMirror-lint-markers"]}
            cm (.fromTextArea js/CodeMirror
                              (r/dom-node this)
                              opts)]
        (js/parinferCodeMirror.init cm)
        (.removeKeyMap cm)
        (.setOption cm "extraKeys" #js {:Shift-Tab false
                                        :Tab false})
        (reset! editor-ref cm)))
    :component-will-unmount
    (fn []
      (let [cm @editor-ref]
        ;; toTextArea will destroy and clean up cm
        (.toTextArea cm)))}))

(defn controls []
  [:div.buttons
   [:button.btn.btn-sm.btn-outline-primary
    {:on-click #(j/call @editor-ref :performLint)}
    "lint!"]
   [:button.btn.btn-sm.btn-outline-primary
    {:on-click #(.setValue @editor-ref "\n\n")}
    "clear!"]
   [:button.btn.btn-sm.btn-outline-primary
    {:on-click #(do (.setValue @editor-ref initial-code)
                    (j/call @editor-ref :performLint))}
    "reset!"]])

(defn app []
  [:div#clj-kondo.container
   [:div.row
    [:p.col-12.lead
     [:span [:a {:href "https://github.com/borkdude/clj-kondo"
                 :target "_blank"}
             "clj-kondo"]
      " clojure linter playground"]]]
   [:div
    [controls]
    [editor "code" [:code]]
    [controls]]])

(goog-define server "")

(defn mount [el]
  (r/render-component [app] el))

(defn register-clj-kondo-linter []
  (js/CodeMirror.registerHelper
   "lint" "clojure"
   (fn [text _opts]
     (js/Promise.
      (fn [resolve reject]
        (ajax/POST server
                   {:format :url
                    :params {:code text}
                    :handler
                    (fn [response]
                      (let [results
                            (into-array
                             (for [line (str/split-lines response)
                                   :let [[_ _file row col level message]
                                         (re-matches #"(.+):(\d+):(\d+): (\w+): (.*)" line)]
                                   :when message]
                               (let [[row col] [(js/parseInt row) (js/parseInt col)]
                                     row (dec row)
                                     col (dec col)
                                     start-pos (js/CodeMirror.Pos row col)
                                     token (.getTokenAt @editor-ref
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
