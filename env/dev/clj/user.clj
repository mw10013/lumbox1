(ns user
  (:require 
            [mount.core :as mount]
            [lumbox1.figwheel :refer [start-fw stop-fw cljs]]
            lumbox1.core))

(defn start []
  (mount/start-without #'lumbox1.core/repl-server))

(defn stop []
  (mount/stop-except #'lumbox1.core/repl-server))

(defn restart []
  (stop)
  (start))


