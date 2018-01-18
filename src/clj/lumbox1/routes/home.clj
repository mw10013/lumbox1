(ns lumbox1.routes.home
  (:require [lumbox1.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [lumbox1.db.core :as db]
            [com.walmartlabs.lacinia :as l]
            [lumbox1.api :as api]))

(defn upsert-user! [{:keys [params]}]
  (db/upsert-user! params)
  (response/ok (assoc params :result "upsert-user succeeded")))

(defn graphql
  [req]
  (let [{{:keys [query variables operation-name operationName]} :params} req]
    (def r req)
    (response/ok (l/execute api/schema query variables nil {:operation-name (or operation-name operationName)}))))

(defn home-page []
  (layout/render "home.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
           (GET "/user/:email" [email] (response/ok (db/user [:user/email email])))
           (POST "/upsert-user" req (upsert-user! req))
           (POST "/graphql" req (graphql req))
           (GET "/graphql" req (graphql req))
  (GET "/docs" []
       (-> (response/ok (-> "docs/docs.md" io/resource slurp))
           (response/header "Content-Type" "text/plain; charset=utf-8"))))

