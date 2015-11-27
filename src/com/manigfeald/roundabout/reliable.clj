(ns com.manigfeald.roundabout.reliable
  (:require [clojure.core.async
             :refer [alt! go-loop]
             :as async]))

(defn sender [input output retransmit ack]
  (go-loop [outstandng-messages []
            sequence-number 0N
            acked -1
            to-write nil]
    (let [[val chan] (async/alts!
                      [ack
                       retransmit
                       (if (contains? outstandng-messages sequence-number)
                         [output [sequence-number (nth outstandng-messages sequence-number)]]
                         (if to-write
                           [output to-write]
                           input))])]
      (cond
       (= chan output) (recur outstandng-messages (inc sequence-number) acked nil)
       (= chan input) (recur (conj outstandng-messages val)
                             sequence-number
                             acked
                             [sequence-number val])
       (= chan ack) (if (= val (count outstandng-messages))
                      (recur (empty outstandng-messages) 0N -1 to-write)
                      (recur outstandng-messages sequence-number val to-write))
       (= chan retransmit) (recur outstandng-messages (bigint val) acked to-write))))
  {:input input
   :output output
   :retransmit retransmit
   :ack ack})

(defn receiver [input output retransmit ack timeout]
  (go-loop [last-acked -1]
    (alt!
      input ([[n msg]]
               (if (or (zero? n)
                       (= n (inc last-acked)))
                 (do
                   (async/>! output msg)
                   (async/>! ack n)
                   (recur (bigint n)))
                 (do
                   (async/>! retransmit (inc last-acked))
                   (recur last-acked))))
      (async/timeout timeout) ([_]
                                 (async/>! retransmit (inc last-acked))
                                 (recur last-acked))))
  {:input input
   :output output
   :retransmit retransmit
   :ack ack})
