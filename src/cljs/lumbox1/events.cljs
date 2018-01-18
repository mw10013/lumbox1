(ns lumbox1.events
  (:require [lumbox1.db :as db]
            [re-frame.core :as rf :refer [dispatch reg-event-db reg-sub]]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]))

;;dispatchers

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :set-active-page
  (fn [db [_ page]]
    (assoc db :page page)))

#_(reg-event-db
  :set-docs
  (fn [db [_ docs]]
    (assoc db :docs docs)))

(reg-event-db
  :set-user-first-name
  (fn [db [_ s]] (assoc-in db [:user :first-name] s)))

(reg-event-db
  :set-user-last-name
  (fn [db [_ s]] (assoc-in db [:user :last-name] s)))

(reg-event-db
  :set-user-email
  (fn [db [_ s]] (assoc-in db [:user :email] s)))

(rf/reg-event-db
  :http-xhrio-failed
  (fn [db [_ result]]
    (-> db
        (assoc :status "http xhrio failed")
        (assoc :result result))))

(rf/reg-event-fx
  :get-user-by-email
  (fn [cofx [_ email]]
    (let [email (or email (-> cofx :db :user :email))]
      {:http-xhrio {:method          :post
                    :uri             "/graphql"
                    :params          {:query         "query UserByEmail($email: String!) { user_by_email(email: $email) { first_name last_name email id}}"
                                      :variables     {:email email}
                                      :operationName "UserByEmail"}
                    :format          (ajax/json-request-format)
                    :response-format (ajax/transit-response-format)
                    :on-success      [:get-user-by-email-succeeded]
                    :on-failure      [:http_xhrio-failed]}})))

(defn ^:private graphql-to-clojure
  "Transform graphql map to clojure."
  [m]
  (into {} (map (fn [[k v]]
                  [(-> k name (clojure.string/replace \_ \-) keyword) v]) m)))

(defn ^:private clojure-to-graphql
  "Transform clojure map to graphql"
  [m]
  (into {} (map (fn [[k v]]
                  [(-> k name (clojure.string/replace \- \_) keyword) v]) m)))

(rf/reg-event-db
  :get-user-by-email-succeeded
  (fn [db [e result]]
    (-> db
        (assoc :status e)
        (assoc :result result)
        (update :user merge (-> result :data :user_by_email graphql-to-clojure)))))

(rf/reg-event-fx
  :upsert-user
  (fn [{db :db}
       [_]]
    {:http-xhrio {:method          :post
                  :uri             "/graphql"
                  :params          {:query     "mutation UpsertUser($email: String! $first_name: String, $last_name: String) {
                  upsert_user(email: $email first_name: $first_name last_name: $last_name) { email first_name last_name id }}"
                                    :variables (-> db :user (select-keys [:email :first-name :last-name]) clojure-to-graphql)}
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:upsert-user-succeeded]
                  :on-failure      [:http-xhrio-failed]}}))

; TODO: Handle graphql :errors
(rf/reg-event-db
  :upsert-user-succeeded
  (fn [db [e result]]
    (-> db
        (assoc :status e)
        (assoc :result result)
        (update :user merge (-> result :data :upsert_user graphql-to-clojure)))))

(rf/reg-event-fx
  :delete-user-by-email
  (fn [cofx [_ email]]
    (let [email (or email (-> cofx :db :user :email))]
      {:http-xhrio {:method          :post
                    :uri             "/graphql"
                    :params          {:query         "mutation DeleteUserByEmail($email: String!) { delete_user_by_email(email: $email) }"
                                      :variables     {:email email}}
                    :format          (ajax/json-request-format)
                    :response-format (ajax/transit-response-format)
                    :on-success      [:delete-user-by-email-succeeded]
                    :on-failure      [:http_xhrio-failed]}})))

; TODO: Handle graphql :errors
(rf/reg-event-db
  :delete-user-by-email-succeeded
  (fn [db [e result]]
    (-> db
        (assoc :status e)
        (assoc :result result)
        (assoc :user {}))))

;;subscriptions

(reg-sub
  :page
  (fn [db _] (:page db)))

(reg-sub
  :docs
  (fn [db _] (:docs db)))

(reg-sub
  :status
  (fn [db _] (:status db)))

(reg-sub
  :result
  (fn [db _] (:result db)))

(reg-sub
  :user/email
  (fn [db _] (get-in db [:user :email])))

(reg-sub
  :user/first-name
  (fn [db _] (get-in db [:user :first-name])))

(reg-sub
  :user/last-name
  (fn [db _] (get-in db [:user :last-name])))

(reg-sub
  :user/nickname
  (fn [db _] (get-in db [:user :nickname])))

(reg-sub
  :buffer/email
  (fn [db _] (get-in db [:buffer :email])))

(defn ajax-test []
  (ajax/ajax-request
    #_{:uri "http://www.google.com" :method :get :handler #(def x %) :response-format (ajax/text-response-format)}
    {:uri "/user/mick@jagger.com" :method :get :handler #(def x %) :response-format (ajax/json-response-format)})
  )

(comment
  (ajax/ajax-request {:uri "http://localhost:3000/user/mick@jones.com" :method :get :handler println :response-format (ajax/json-response-format)})
  (ajax/ajax-request {:uri "http://localhost:3000/user/mick@jagger.com" :method :get :handler println :response-format (ajax/text-response-format)})
  (ajax/ajax-request {:uri "/user/mick@jagger.com" :method :get :handler println :response-format (ajax/text-response-format)})
  (println "hello")
  )