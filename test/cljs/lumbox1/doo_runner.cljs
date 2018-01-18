(ns lumbox1.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [lumbox1.core-test]))

(doo-tests 'lumbox1.core-test)

