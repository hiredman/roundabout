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

(require '[clojure.test.check :as tc])
(require '[clojure.test.check.generators :as gen])
(require '[clojure.test.check.properties :as prop])
(require '[clojure.test.check.clojure-test :refer [defspec]])

(defspec works 1e2
  (prop/for-all [sleeps (gen/list (gen/tuple gen/nat gen/nat))]
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
                      queue-counts (atom #{})
                      a (future
                          (doseq [[sleep _] sleeps]
                            (Thread/sleep (* 10 sleep))
                            (.put queue (async/<!! sender-out))
                            (swap! queue-counts conj (count queue))))
                      b (future
                          (doseq [[_ sleep] sleeps]
                            (Thread/sleep (* 10 sleep))
                            (async/>!! receiver-in (.take queue))
                            (swap! queue-counts conj (count queue))))
                      sent (atom [])
                      received (atom [])
                      c (async/go
                          (doseq [_ sleeps]
                            (let [id (UUID/randomUUID)]
                              (swap! sent conj id)
                              (async/>! sender-in id))))
                      d (async/go
                          (doseq [_ sleeps]
                            (let [x (async/<! receiver-out)]
                              (swap! received conj x))))]
                  (try
                    @a
                    @b
                    (async/<!! c)
                    (async/<!! d)
                    (and (= @sent @received)
                         (every?
                          #(> 11 %)
                          @queue-counts))
                    (finally
                      (async/close! abort)
                      (future-cancel a)
                      (future-cancel b))))))
