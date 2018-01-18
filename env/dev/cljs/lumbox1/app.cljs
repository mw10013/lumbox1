(ns ^:figwheel-no-load lumbox1.app
  (:require [lumbox1.core :as core]
            [devtools.core :as devtools]))

(enable-console-print!)

(devtools/install!)

(core/init!)
