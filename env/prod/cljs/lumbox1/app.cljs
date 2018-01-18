(ns lumbox1.app
  (:require [lumbox1.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
