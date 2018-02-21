(ns lumbox1.db.core
  (:require [datomic.api :as d]
            [mount.core :refer [defstate]]
            [lumbox1.config :refer [env]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [buddy.hashers :as hashers]))

(declare create-schema create-fn-schema populate)

(defstate ^{:on-reload :noop} conn
          :start (let [uri (:database-url env)]
                   (d/create-database uri)
                   (d/connect uri))
          :stop (-> conn .release))

(defstate ^{:on-reload :noop} schema
          :start (do (create-schema) (create-fn-schema) (populate)))

(defn db
  []
  (d/db conn))

(defn create-schema []
  (let [schema [{:db/ident :user.role/user}
                {:db/ident :user.role/support}
                {:db/ident :user.role/admin}

                {:db/ident       :user/email
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one
                 :db/unique      :db.unique/identity}
                {:db/ident       :user/encrypted-password
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one}
                {:db/ident       :user/roles
                 :db/valueType   :db.type/ref
                 :db/cardinality :db.cardinality/many}
                {:db/ident       :user/first-name
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one}
                {:db/ident       :user/last-name
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one}
                {:db/ident       :user/nickname
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one}
                {:db/ident       :user/friends
                 :db/valueType   :db.type/ref
                 :db/cardinality :db.cardinality/many}

                {:db/ident       :msg/content
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one}
                {:db/ident       :msg/author
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one}
                ]]
    @(d/transact conn schema)))

(defn create-fn-schema []
  (->> "schema/fn-schema.edn"
       io/resource
       slurp
       (edn/read-string {:readers (select-keys *data-readers* ['db/id 'db/fn])})
       (d/transact conn)
       deref))

(defn create-user
  [{:keys [:user/email :user/encrypted-password :user/roles] :as user}]
  @(d/transact conn [{:db/id "tempid" :user/encrypted-password encrypted-password :user/roles roles}
                     [:add-identity "tempid" :user/email email]]))

(defn retract-stale-many-refs
  [db eid a new-refs]
  (let [old-refs (->> eid (d/entity db) a (map #(if (keyword? %) (d/entity db %) %)) (map :db/id) set)
        _ (println "old-refs:" old-refs)
        new-refs (->> new-refs (map (comp :db/id (partial d/entity db))) set)
        _ (println "new-refs:" new-refs)
        stale-refs (clojure.set/difference old-refs new-refs)]
    (map #(vector :db/retract eid a %) stale-refs)))

(comment
  (users (db))
  (create-user {:user/email "foo@email.com" :user/encrypted-password (hashers/encrypt "letmein") :user/roles #{:user.role/user}})
  (create-user {:user/email "foo@email.com" :user/encrypted-password (hashers/encrypt "letmein") :user/roles #{:user.role/user :user.role/support}})
  (upsert-user {:user/email "foo@email.com" :user/roles #{:user.role/user}})
  (upsert-user {:user/email "foo@email.com" :user/roles #{:user.role/user :user.role/support}})
  (upsert-user {:user/email "foo@email.com" :user/friends #{[:user/email "thomas@dolby.com"]}})
  (upsert-user {:user/email "foo@email.com" :user/friends #{}})
  (upsert-user {:user/email "foo@email.com" :user/roles #{}})
  (user (db) [:user/email "foo@email.com"])
  (retract-stale-many-refs (db) [:user/email "foo@email.com"] :user/roles #{:user.role/admin})
  (retract-stale-many-refs (db) [:user/email "foo@email.com"] :user/roles #{})
  (retract-stale-many-refs (db) [:user/email "foo@email.com"] :user/friends #{[:user/email "thomas@dolby.com"]})
  (delete-user [:user/email "foo@email.com"])
  )

(defn upsert-user
  [{:keys [:db/id :user/email :user/roles :user/friends] :as user}]
  (let [eid (or id [:user/email email])
        t (concat [user]
                  (when roles [[:retract-stale-many-refs eid :user/roles roles]])
                  (when friends [[:retract-stale-many-refs eid :user/friends friends]]))]
    @(d/transact conn t)))

(def user-pattern '[* {[:user/roles] [:db/ident]} {[:user/friends] [:db/id :user/email]}])

(defn users
  [db]
  (d/q '[:find [(pull ?e pattern) ...]
         :where [?e :user/email _]
         :in $ pattern] db user-pattern))

(defn user
  [db eid]
  (let [x (d/pull db user-pattern eid)]
    (when (:user/email x) x)))

(defn delete-user
  [eid]
  @(d/transact conn [[:db.fn/retractEntity eid]]))

(comment
  (user (db) [:user/email "thomas@dolby.com"])
  (upsert-user {:user/first-name "Sting" :user/last-name "" :user/email "sting@sting.com" :user/friends #{}})
  (user (db) [:user/email "sting@sting.com"])
  (delete-user [:user/email "sting@sting.com"])
  )

(defn get-message
  [id]
  (d/entity (d/db conn) id))

(defn create-message
  [content author]
  (let [tx @(d/transact conn [{:db/id       "tempid"
                               :msg/content content
                               :msg/author  author}])]
    #_(d/entity (:db-after tx) (d/resolve-tempid (:db-after tx) (:temptids tx) "tempid"))
    (d/entity (:db-after tx) (get-in tx [:tempids "tempid"]))))

(defn update-message
  [id content author]
  (let [tx @(d/transact conn [{:db/id       id
                               :msg/content content
                               :msg/author  author}])]
    (d/entity (:db-after tx) id)))

(defn populate []
  (upsert-user {:user/email      "mick@jones.com" :user/encrypted-password (hashers/encrypt "letmein")
                :user/roles      #{:user.role/user}
                :user/first-name "Mick" :user/last-name "Jones" :user/nickname "Mickey"})
  (upsert-user {:user/email      "mick@jagger.com" :user/encrypted-password (hashers/encrypt "letmein")
                :user/roles      #{:user.role/user :user.role/support}
                :user/first-name "Mick" :user/last-name "Jagger"})
  (upsert-user {:user/email      "thomas@dolby.com" :user/encrypted-password (hashers/encrypt "letmein")
                :user/roles      #{:user.role/user :user.role/support :user.role/admin}
                :user/first-name "Thomas" :user/last-name "Dolby"
                :user/friends    [[:user/email "mick@jones.com"]]})
  (upsert-user {:user/email "mick@jones.com" :user/friends [[:user/email "mick@jagger.com"] [:user/email "thomas@dolby.com"]]}))

(comment
  (create-schema)
  (create-fn-schema)
  (def x (create-message "Hello message" "author1"))
  (upsert-user {:user/first-name "Mick" :user/last-name "Jones" :user/email "mick@jones.com"})
  (upsert-user {:user/first-name "Mickey" :user/last-name "Jonesy" :user/email "mick@jones.com"})
  (upsert-user {:user/first-name "Mickey" :user/last-name "" :user/email "mick@jones.com"})
  (upsert-user {:user/first-name "Sting" :user/last-name "" :user/email "sting@sting.com" :user/friends #{}})
  (user (db) [:user/email "mick@jones.com"])
  (user (db) [:user/email "mick@jagger.com"])
  (user (db) [:user/email "thomas@dolby.com"])
  (user (db) [:user/email "sting@sting.com"])
  (d/q '[:find ?e ?email :in $ :where [?e :user/email ?email]] (d/db conn))
  (d/q '[:find ?e ?email ?friends :in $ :where [?e :user/email ?email] [?e :user/friends ?friends]] (d/db conn))
  (user (db) [:user/email "mick@jones.com"])
  (upsert-user {:user/email   "mick@jones.com" :user/first-name "Mick" :user/last-name "Jones"
                :user/friends #{}})
  (upsert-user {:user/email   "mick@jones.com" :user/first-name "Mick" :user/last-name "Jones"
                :user/friends #{[:user/email "mick@jagger.com"]}})
  (upsert-user {:user/email   "mick@jones.com" :user/first-name "Mick" :user/last-name "Jones"
                :user/friends #{[:user/email "mick@jagger.com"] [:user/email "thomas@dolby.com"]}})
  (upsert-user {:user/email   "mick@jones.com" :user/first-name "Mick" :user/last-name "Jones"
                :user/friends #{[:user/email "thomas@dolby.com"]}})

  (d/invoke (d/db conn) :retract-stale-many-refs (d/db conn) [:user/email "mick@jones.com"] :user/friends [])
  (d/invoke (d/db conn) :foo-fn (d/db conn))
  (d/invoke (d/db conn) :bar-fn (d/db conn))

  (d/pull (db) '[{[:user/roles] [:db/ident]}] [:user/email "thomas@dolby.com"])
  (d/pull (db) '[* {[:user/roles] [:db/ident]} {[:user/friends] [:db/id :user/email]}] [:user/email "thomas@dolby.com"])

  (users (db))

  )
