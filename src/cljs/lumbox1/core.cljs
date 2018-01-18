(ns lumbox1.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [lumbox1.ajax :refer [load-interceptors!]]
            [lumbox1.events])
  (:import goog.History))

(defn nav-link [uri title page collapsed?]
  (let [selected-page (rf/subscribe [:page])]
    [:li.nav-item
     {:class (when (= page @selected-page) "active")}
     [:a.nav-link
      {:href uri
       :on-click #(reset! collapsed? true)} title]]))

(defn navbar []
  (r/with-let [collapsed? (r/atom true)]
    [:nav.navbar.navbar-dark.bg-primary
     [:button.navbar-toggler.hidden-sm-up
      {:on-click #(swap! collapsed? not)} "☰"]
     [:div.collapse.navbar-toggleable-xs
      (when-not @collapsed? {:class "in"})
      [:a.navbar-brand {:href "#/"} "lumbox1"]
      [:ul.nav.navbar-nav
       [nav-link "#/" "Home" :home collapsed?]
       [nav-link "#/about" "About" :about collapsed?]]]]))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     [:img {:src (str js/context "/img/warning_clojure.png")}]]]])

(defn home-page []
  [:div.container
   #_(when-let [docs @(rf/subscribe [:docs])]
     [:div.row>div.col-sm-12
      [:div {:dangerouslySetInnerHTML
             {:__html (md->html docs)}}]])
   [:form {:on-submit #(rf/dispatch [:get-user])}
    [:div.form-group
     [:label {:for "email"} "Email"]
     [:input#email.form-control {:type "email" :placeholder "Enter email"
                                 :value @(rf/subscribe [:user/email])
                                 :on-change #(rf/dispatch-sync [:set-user-email (-> % .-target .-value)])}]
     [:small#emailHelp.form-text.text-muted "We'll never share your email with anybody else."]]
    [:div.form-group
     [:label {:for "firstName"} "First Name"]
     [:input#firstName.form-control {:type "text" :placeholder "Enter first name"
                                     :value @(rf/subscribe [:user/first-name])
                                     :on-change #(rf/dispatch-sync [:set-user-first-name (-> % .-target .-value)])}]]
    [:div.form-group
     [:label {:for "lastName"} "Last Name"]
     [:input#lastName.form-control {:type "text" :placeholder "Enter last name"
                                    :value @(rf/subscribe [:user/last-name])
                                    :on-change #(rf/dispatch-sync [:set-user-last-name (-> % .-target .-value)])}]]
    [:div.form-row.form-group.align-items-center
     [:div.col-auto
      [:button.btn.btn-primary {:type     "button" :disabled (empty? @(rf/subscribe [:user/email]))
                                :on-click #(rf/dispatch [:get-user])} "Get"]]
     [:div.col-auto
      [:button.btn.btn-primary {:type     "button" :disabled (empty? @(rf/subscribe [:user/email]))
                                :on-click #(rf/dispatch [:upsert-user])} "Upsert"]]]
    [:div.form-row.form-group.align-items-center
     [:div.col-auto
      [:button.btn.btn-secondary {:type "button" :on-click #(rf/dispatch [:get-user "mick@jones.com"])} "Populate Mick Jones"]]
     [:div.col-auto
      [:button.btn.btn-secondary {:type "button" :on-click #(rf/dispatch [:get-user "mick@jagger.com"])} "Populate Mick Jagger"]]
     [:div.col-auto
      [:button.btn.btn-secondary {:type "button" :on-click #(rf/dispatch [:get-user "thomas@dolby.com"])} "Populate Thomas Dolby"]]]
    [:div.form-row.form-group.align-items-center
     [:div.col-auto
      [:button.btn.btn-secondary {:type "button" :on-click #(rf/dispatch [:query-user-by-email "mick@jones.com"])} "Query Mick Jones"]]
     [:div.col-auto
      [:button.btn.btn-secondary {:type "button" :on-click #(rf/dispatch [:query-user-by-email "mick@jagger.com"])} "Query Mick Jagger"]]
     [:div.col-auto
      [:button.btn.btn-secondary {:type "button" :on-click #(rf/dispatch [:query-hello])} "Hello"]]]
    [:div.form-group
     [:label "Status"]
     [:textarea.form-control {:read-only true :value @(rf/subscribe [:status])}]]
    [:div.form-group
     [:label "Result"]
     [:textarea.form-control {:read-only true :value (str @(rf/subscribe [:result]))}]]]
   ])

(def pages
  {:home #'home-page
   :about #'about-page})

(defn page []
  [:div
   [navbar]
   [(pages @(rf/subscribe [:page]))]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

(secretary/defroute "/about" []
  (rf/dispatch [:set-active-page :about]))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET "/docs" {:handler #(rf/dispatch [:set-docs %])}))

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  #_(fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
