(ns lumbox1.routes.home
  (:require [lumbox1.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [lumbox1.db.core :as db]
            [com.walmartlabs.lacinia :as l]
            [lumbox1.api :as api]))

(defn graphql
  [req]
  (let [{{:keys [query variables operation-name operationName]} :params} req]
    (def r req)
    #_(def body (slurp (:body req)))
    (response/ok (l/execute api/schema (or query (-> req :body slurp)) variables nil
                            {:operation-name (or operation-name operationName)}))))

(defn home-page []
  (layout/render "home.html"))

(defroutes home-routes
           (GET "/" [] (home-page))
           (POST "/graphql" req (graphql req))
           (GET "/graphql" req (graphql req))
           (GET "/graphiql" [] (layout/render "graphiql.html"))
           (GET "/docs" []
             (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                 (response/header "Content-Type" "text/plain; charset=utf-8"))))

