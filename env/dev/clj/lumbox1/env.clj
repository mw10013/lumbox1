(ns lumbox1.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [lumbox1.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[lumbox1 started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[lumbox1 has shut down successfully]=-"))
   :middleware wrap-dev})
