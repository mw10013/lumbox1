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
  (let [{{:keys [query variables operation-name operationName]} :params} req
        side-effects (atom {})
        context {:session (:session req) :side-effects side-effects}
        result (l/execute api/schema (or query (-> req :body slurp)) variables context
                          {:operation-name (or operation-name operationName)})
        response (if (:errors result) (response/bad-request result) (response/ok result))
        session (:session @side-effects ::not-found)]       ; nil will clear the session.
    (if (= session ::not-found) response (assoc response :session session))))

(defn home-page []
  (layout/render "home.html"))

(defn set-user! [id {session :session}]
  (-> (ring.util.response/response (str "User set to: " id))
      (assoc :session (assoc session :user id))
      (assoc :headers {"Content-Type" "text/plain"})))

(defn remove-user! [{session :session}]
  (-> (ring.util.response/response "User removed")
      (assoc :session (dissoc session :user))
      (assoc :headers {"Content-Type" "text/plain"})))

(defn clear-session! []
  (-> (response/ok "Session cleared")
      (assoc :session nil)
      (assoc :headers {"Content-Type" "text/plain"})))

(defn dump-session [{session :session}]
  (-> (ring.util.response/response (str "Session: " session))
      (assoc :headers {"Content-Type" "text/plain"})))

(defroutes home-routes
           (GET "/" [] (home-page))
           (POST "/graphql" req (graphql req))
           (GET "/graphql" req (graphql req))
           (GET "/graphiql" [] (layout/render "graphiql.html"))
           (GET "/bootstrap" [] (layout/render "bootstrap.html"))
           (GET "/login/:id" [id :as req] (set-user! id req))
           (GET "/session" req (dump-session req))
           (GET "/remove" req (remove-user! req))
           (GET "/logout" req (clear-session!))
           (GET "/docs" []
             (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                 (response/header "Content-Type" "text/plain; charset=utf-8"))))

