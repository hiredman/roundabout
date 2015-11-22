(ns com.manigfeald.roundabout-test
  (:require [clojure.test :refer :all]
            [com.manigfeald.roundabout :refer :all]
            [clojure.core.async :as async])
  (:import (java.util UUID)
           (java.util.concurrent LinkedBlockingQueue)))

(deftest a-test
  (let [{sender-in :input
         sender-out :output
         feedback :feedback
         abort :abort} (sender (async/chan)
                               (async/chan)
                               (async/chan)
                               (async/chan))
         {receiver-in :input
          receiver-out :output} (receiver (async/chan)
                                          (async/chan)
                                          feedback
                                          abort
                                          32
                                          1000)
          queue (LinkedBlockingQueue.)
          a (future
              (while true
                (.put queue (async/<!! sender-out))))
          b (future
              (while true
                (async/>!! receiver-in (.take queue))))]
    (try
      (async/go-loop []
        (let [id (UUID/randomUUID)]
          (async/>! sender-in id))
        (recur))
      (async/go-loop []
        (let [x (async/<! receiver-out)]
          (async/<! (async/timeout (* (rand) 1000))))
        (recur))
      (dotimes [i 100]
        (Thread/sleep 100)
        (is (>= 32 (count queue))
            (count queue)))
      (finally
        (async/close! abort)
        (future-cancel a)
        (future-cancel b)))))
