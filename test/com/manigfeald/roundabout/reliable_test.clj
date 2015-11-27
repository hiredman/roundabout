(ns com.manigfeald.roundabout.reliable-test
  (:require [clojure.test :refer :all]
            [com.manigfeald.roundabout.reliable :refer :all]
            [clojure.core.async :as async]
            [clojure.core.async.impl.buffers :as buffers]
            [clojure.core.async.impl.protocols :as impl])
  (:import (java.util UUID)))


(defn unreliable-buffer [n]
  (let [b (buffers/fixed-buffer 1)]
    (reify
      impl/Buffer
      (full? [this]
        (impl/full? b))
      (remove! [this]
        (impl/remove! b))
      (add!* [this itm]
        (when (< n (rand))
          (impl/add!* b itm))
        this)
      (close-buf! [this])
      clojure.lang.Counted
      (count [this]
        (count b)))))

(deftest a-test
  (let [{sender-in :input
         sender-out :output
         retransmit :retransmit
         ack :ack} (sender (async/chan)
                           (async/chan (unreliable-buffer 0.9))
                           (async/chan (unreliable-buffer 0.9))
                           (async/chan (unreliable-buffer 0.9)))
         {receiver-output :output} (receiver sender-out
                                             (async/chan)
                                             retransmit
                                             ack
                                             10)
         sent (atom #{})
         received (atom #{})]
    (async/go
      (dotimes [i 1e2]
        (swap! sent conj i)
        (async/>! sender-in i)))
    (dotimes [i 1e2]
      (swap! received conj (async/<!! receiver-output)))
    (is (= @sent @received))))
