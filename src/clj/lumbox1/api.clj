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
            [clojure.string :as str]))

(defn ^:private datomic-to-graphql
  "Transform datomic map to graphql."
  [m]
  (into {} (map (fn [[k v]]
                  [(-> k name (str/replace \- \_) keyword)
                   (if (= k :db/id) (str v) v)]) m)))

(defn users
  [_ _ _]
  (map datomic-to-graphql (db/users)))

(comment
  (l/execute schema
             "{ users {
               id email friends {
                 email }}}" nil nil)
  )

(defn user-friends
  [_ _ {friends :friends}]
  (map #(-> % :db/id db/user datomic-to-graphql) friends))

(defn user-by-email
  [_ {email :email} _]
  (if-let [x (db/user [:user/email email])]
    (datomic-to-graphql x)
    (resolve-as nil {:message "User not found."
                     :status 404})))

(comment
  (user-by-email nil {:email "mick@jones.com"} nil)
  (db/user [:user/email "mick@jones.com"])
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

(defn upsert-user
  [_ args _]
  (let [{:keys [first_name last_name email]} args]
    (db/upsert-user! {:user/first-name first_name :user/last-name last_name :user/email email})
    (user-by-email nil {:email email} nil)))

(defn delete-user-by-email
  [_ {email :email} _]
  (db/delete-user! [:user/email email])
  email)

(defn random-die-roll-once
  [_ _ {:keys [num_sides]}]
  (-> num_sides rand-int inc))

(defn random-die-rolls
  [_ {:keys [num_rolls]} die]
  (repeatedly num_rolls #(random-die-roll-once nil nil die)))

(defn get-message
  [_ {id :id} _]
  (if-let [e (db/get-message (Long. id))]
    {:id id :content (:msg/content e) :author (:msg/author e)}
    (throw (Exception. (str "Message with ID " id " does not exist.")))))

(defn create-message
  [_ {{:keys [content author]} :input} _]
  (let [e (db/create-message content author)]
    {:id (-> e :db/id str) :content (:msg/content e) :author (:msg/author e)}))

(defn update-message
  [_ {id :id {:keys [content author]} :input} _]
  (let [e (db/update-message (Long. id) content author)]
    {:id (-> e :db/id str) :content (:msg/content e) :author (:msg/author e)}))

(def hello-schema (schema/compile
                    {:queries {:hello
                               ;; String is quoted here; in EDN the quotation is not required
                               {:type    'String
                                :resolve (constantly "world")}}}))

(defstate schema
          :start (-> (io/resource "schema/api-schema.edn")
                     slurp
                     edn/read-string
                     (util/attach-resolvers {:User/friends user-friends
                                             :random-die/roll-once random-die-roll-once
                                             :random-die/rolls random-die-rolls
                                             :query/hello            (constantly "world!")
                                             :query/quote-of-the-day (fn [& _] (if (< (rand) 0.5) "Easy does it." "Salvation lies within."))
                                             :query/random           (fn [& _] (rand))
                                             :query/roll-three-dice  (fn [& _] (repeatedly 3 (comp inc (partial rand-int 6))))
                                             :query/roll-dice        (fn [_ {:keys [num_dice num_sides]} _]
                                                                       (repeatedly num_dice #(-> num_sides (or 6) rand-int inc)))
                                             :query/get-die (fn [_ {:keys [num_sides] :or {:num_sides 6}} _] {:num_sides num_sides})
                                             :query/get-message get-message
                                             :query/users users
                                             :query/user-by-email    user-by-email
                                             :mutation/create-message create-message
                                             :mutation/update-message update-message
                                             :mutation/upsert-user   upsert-user
                                             :mutation/delete-user-by-email delete-user-by-email})
                     schema/compile))

(comment
   (mount.core/start #'lumbox1.api/schema)
  (l/execute schema "{hello}" nil nil)
  (l/execute schema "{quote_of_the_day}" nil nil)
  (l/execute schema "{random}" nil nil)
  (l/execute schema "{roll_three_dice}" nil nil)
  (l/execute schema "{roll_dice(num_dice: 10, num_sides: 7)}" nil nil)
  (l/execute schema "{roll_dice(num_dice: 10)}" nil nil)
  (l/execute schema "{roll_dice(num_sides: 10)}" nil nil)
  (l/execute schema "{get_die(num_sides: 10) {num_sides roll_once rolls(num_rolls: 10)}}" nil nil)
  (l/execute schema "{get_message(id: \"17592186045435\") { id content author }}" nil nil)
  (l/execute schema "mutation CreateMessage($input: MessageInput) {create_message(input: $input) {id content author}}"
             {:input {:content "new content" :author "new author"}} nil)
  (l/execute schema "mutation UpdateMessage($id: ID!, $input: MessageInput) {update_message(id: $id, input: $input) {id content author}}"
             {:id "17592186045435" :input {:content "modified content" :author "modified author"}} nil)
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

;; curl -X POST -H "Content-Type: application/json" -d '{"query": "{user_by_email(email: \"mick@jones.com\") {id first_name last_name }}"}' http://localhost:3000/graphql

