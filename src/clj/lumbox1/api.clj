(ns lumbox1.api
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [com.walmartlabs.lacinia :as l]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.util :as util]
            [lumbox1.db.core :as db]
            [clojure.string :as str]
            [buddy.hashers :as hashers]
            [cognitect.anomalies :as anom]
            [lumbox1.validation :as v]))

(defn ^:private datomic-to-graphql
  "Transform datomic map to graphql."
  [m]
  (into {} (map (fn [[k v]]
                  [(-> k name (str/replace \- \_) keyword)
                   (if (= k :db/id) (str v) v)]) m)))

(defn users
  [_ _ _]
  (map datomic-to-graphql (db/users (db/db))))

(defn user-roles
  "TODO: Map - to _."
  [_ _ {roles :roles}]
  (map (comp keyword clojure.string/upper-case name) roles))

(defn user-friends
  [_ _ {friends :friends}]
  (map #(-> % :db/id (partial db/user (db/db)) datomic-to-graphql) friends))

(defn user-by-email
  [_ {email :email} _]
  (if-let [x (db/user (db/db) [:user/email email])]
    (datomic-to-graphql x)
    (resolve-as nil {:message "User not found."
                     :status  404})))

(defn upsert-user
  [_ args _]
  (let [{:keys [first_name last_name email]} args]
    (db/upsert-user {:user/first-name first_name :user/last-name last_name :user/email email})
    (user-by-email nil {:email email} nil)))

(defn delete-user-by-email
  [_ {email :email} _]
  (db/delete-user [:user/email email])
  email)

(defn ->error
  [t]
  (let [anomaly (or (ex-data t) (some-> t .getCause ex-data) {::anom/category ::anom/fault})]
    {:message (or (::anom/message anomaly) (.getMessage t))
     :anomaly anomaly}))

(defn register-user
  [_ {:keys [input]} _]
  (let [[errors {:keys [email password]}] (v/validate-register-user-input input)]
    (if errors
      (resolve-as nil {:message "Invalid input." :anomaly {::anom/category ::anom/incorrect} :input-errors errors})
      (try
        (let [encrypted-password (hashers/encrypt password)]
          (db/create-user {:user/email email :user/encrypted-password encrypted-password :user/roles #{:user.role/user}})
          {:user (user-by-email nil {:email email} nil)})
        (catch Throwable t
          (let [error (->error t)]
            (if (= (get-in error [:anomaly ::anom/category]) ::anom/conflict)
              (resolve-as nil (assoc error :input-errors {:email "Email already used."}))
              (resolve-as nil error))))))))

(defn login
  [context {:keys [input]} _]
  (if (get-in context [:session :identity :user/email])
    (resolve-as nil {:message "Already logged in." :anomaly {::anom/category ::anom/fault}})
    (let [[errors {:keys [email password]}] (v/validate-login-input input)]
      (if errors
        (resolve-as nil {:message "Invalid input." :anomaly {::anom/category ::anom/incorrect} :input-errors errors})
        (if-let [user (db/user (db/db) [:user/email email])]
          (if (hashers/check password (:user/encrypted-password user))
            (let [session (or (-> context :side-effects deref :session) (:session context {}))
                  session (assoc session :identity (select-keys user [:user/email :user/roles]))]
              (swap! (:side-effects context) assoc :session session)
              {:user (datomic-to-graphql user)})
            (resolve-as nil {:message "Invalid login credentials" :anomaly {::anom/category ::anom/forbidden}}))
          (resolve-as nil {:message "Invalid login credentials." :anomaly {::anom/category ::anom/forbidden}}))))))

(defn logout
  [context _ _]
  (let [email (get-in context [:session :identity :user/email])]
    (swap! (:side-effects context) assoc :session nil)      ; always clear session.
    (if email
      {:user (datomic-to-graphql (db/user (db/db) [:user/email email]))}
      (resolve-as nil {:message "Not logged in." :anomaly {::anom/category ::anom/fault}}))))

(defstate schema
          :start (-> (io/resource "schema/api-schema.edn")
                     slurp
                     edn/read-string
                     (util/attach-resolvers {:User/roles                    user-roles
                                             :User/friends                  user-friends

                                             :query/users                   users
                                             :query/user-by-email           user-by-email

                                             :mutation/register-user        register-user
                                             :mutation/login                login
                                             :mutation/logout               logout
                                             :mutation/upsert-user          upsert-user
                                             :mutation/delete-user-by-email delete-user-by-email})
                     schema/compile))

(comment
  (mount.core/start #'lumbox1.api/schema)
  (l/execute schema "{user_by_email(email: \"mick@jones.com\") { first_name }}" nil nil)
  (l/execute schema "{user_by_email(email: \"mick@jones.com\") { first_name last_name email id }}" nil nil)
  (l/execute schema "query UserByEmail($email: String!) { user_by_email(email: $email) { id first_name last_name email __typename}}"
             {:email "mick@jones.com"} nil)
  (l/execute schema "query UserByEmail($email: String!) { user_by_email(email: $email) { id first_name last_name email __typename}}"
             nil nil)
  (user-by-email nil {:email "mick@jones.com"} nil)
  (l/execute schema "mutation UpsertUser($email: String! $first_name: String $last_name: String) { upsert_user(email: $email, first_name: $first_name, last_name: $last_name) { id first_name last_name email}}"
             {:email "howard@jones.com" :first_name "Howard" :last_name "Jones"} nil)
  (l/execute schema "mutation UpsertUser($email: String!, $first_name: String = \"\", $last_name: String = \"\") { upsert_user(email: $email, first_name: $first_name, last_name: $last_name) { id first_name last_name email}}"
             {:email "howard@jones.com" :first_name "Howie"} nil)
  (l/execute schema "{ user_by_email(email: \"howard@jones.com\") { id email first_name last_name  }}" nil nil)
  (l/execute schema "mutation M {delete_user_by_email(email: \"howard@jones.com\")}  " nil nil)
  )

(comment
  (l/execute schema
             "{ users {
               id email friends {
                 email }}}" nil nil)
  (user-by-email nil {:email "mick@jones.com"} nil)
  (db/user (db/db) [:user/email "mick@jones.com"])
  (mount.core/start #'lumbox1.api/schema)

  (l/execute schema
             "query UserByEmail($email: String!) {
               user_by_email(email: $email) {
                 id first_name last_name email friends {
                   id email friends {
                     id email}}}}"
             {:email "mick@jones.com"} nil)
  (l/execute schema "{ user_by_email(email: \"non-existent@email.com\") { id email}}" nil nil)
  )

;; curl -X POST -H "Content-Type: application/json" -d '{"query": "{user_by_email(email: \"mick@jones.com\") {id first_name last_name }}"}' http://localhost:3000/graphql

