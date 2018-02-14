(ns lumbox1.db.sandbox
  (:require [datomic.api :as d]))


(def uri "datomic:mem://lubox1_sandbox")
(d/create-database uri)
(def conn (d/connect uri))

(let [schema [{:db/ident :biz.type/user
               :db/doc "The user business type."}
              {:db/ident :biz.user/email
               :db/valueType :db.type/string
               :db/cardinality :db.cardinality/one
               :db/unique :db.unique/identity}
              {:db/ident :biz.user/encrypted-password
               :db/valueType :db.type/string
               :db/cardinality :db.cardinality/one}
              {:db/ident :biz/type
               :db/doc "The business type of the entity (biz.type/*)."
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/one}]]
  @(d/transact conn schema))

(d/pull (d/db conn) '[*] :biz.type/user)
(d/pull (d/db conn) '[*] [:biz.user/email "user@email.com"])

(d/q '[:find [(pull ?e [*]) ...] :where [?e :biz/type _]] (d/db conn))
(d/q '[:find [(pull ?e [*]) ...] :where [?e :biz/type :biz.type/user]] (d/db conn))
(d/q '[:find [(pull ?e [*]) ...] :where [?e :biz/type :biz.type/user]] (d/db conn))
(d/q '[:find ?e :where [?e :biz.user/email "user@email.com"]] (d/db conn))
(d/q '[:find ?e . :where [?e :biz.user/email "user@email.com"]] (d/db conn))

@(d/transact conn [[:db.fn/cas [:biz.user/email "user@email.com"] :biz/type nil :biz.type/user]])

