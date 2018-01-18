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

(reg-event-db
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

(reg-event-db
  :set-buffer-user-email
  (fn [db [_ s]] (assoc-in db [:buffer :user/email] s)))

(reg-event-db
  :set-buffer-user-first-name
  (fn [db [_ s]] (assoc-in db [:buffer :user/first-name] s)))

(reg-event-db
  :set-buffer-user-last-name
  (fn [db [_ s]] (assoc-in db [:buffer :user/last-name] s)))

(rf/reg-event-db
  :http-xhrio-failed
  (fn [db [_ result]]
    (-> db
        (assoc :status "http xhrio failed")
        (assoc :result result))))

(rf/reg-event-fx
  :get-user
  (fn [cofx [_ email]]
    (let [email (or email (-> cofx :db :user :email))]
      #_(println ":get-user: email: " email)
      {:http-xhrio {:method          :get
                    :uri             (str "/user/" email)
                    :response-format (ajax/transit-response-format)
                    :on-success      [:get-user-succeeded]
                    :on-failure      [:http-xhrio-failed]}})))

(rf/reg-event-db
  :get-user-succeeded
  (fn [db [_ result]]
    #_(def debug-result result)
    (-> db
        (assoc :status ":get-user-succeeded")
        (assoc :result result)
        (assoc :user result))))

(rf/reg-event-fx
  :upsert-user
  (fn [cofx [_]]
    {:http-xhrio {:method :post
                  :uri "/upsert-user"
                  :params (-> cofx :db :user)
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success [:upsert-user-succeeded]
                  :on-failure [:http-xhrio-failed]}}))

(rf/reg-event-db
  :upsert-user-succeeded
  (fn [db [_ result]]
    (-> db
        (assoc :status ":upsert-user-succeeded")
        (assoc :result result))))

(rf/reg-event-db
  :query-succeeded
  (fn [db [_ result]]
    (-> db
        (assoc :status ":query-succeeded")
        (assoc :result result)
        (assoc :user result) )))

(rf/reg-event-fx
  :query-hello
  (fn [cofx [_]]
    {:http-xhrio {:method          :get
                  :uri             "/graphql"
                  :params          {:query "{ hello }"}
                  :format          (ajax/url-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:query-succeeded]
                  :on-failure      [:http-xhrio-failed]}}))

(rf/reg-event-fx
  :query-user-by-email
  (fn [_ [_ email]]
    {:http-xhrio {:method          :post
                  :uri             "/graphql"
                  :params          {:query "query UserByEmail($email: String!) { user_by_email(email: $email) { id first_name last_name email }}"
                                    :variables {:email email}
                                    :operationName "UserByEmail"}
                  :format          (ajax/json-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:query-user-by-email-succeeded]
                  :on-failure      [:http_xhrio-failed]}}))

(defn ^:private graphql-to-clj
  "Transform graphql map to clj."
  [m]
  (into {} (map (fn [[k v]]
                  [(-> k name (clojure.string/replace \_ \-) keyword) v]) m)))

(rf/reg-event-db
  :query-user-by-email-succeeded
  (fn [db [e result]]
    (-> db
        (assoc :status e)
        (assoc :result result)
        (update :user merge (-> result :data :user_by_email graphql-to-clj)))))

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