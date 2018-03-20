(ns com.manigfeald.roundabout.reliable
  "Builds reliable delivery via a buffer and resends over and above
  what is provided by core.async channels. This is useful when
  connecting channels to transports and don't provide reliable
  delivery."
  (:require [clojure.core.async
             :refer [alt! go-loop]
             :as async]))

(defn sender [input output retransmit ack]
  (go-loop [outstandng-messages []
            sequence-number 0N]
    (let [[val chan] (async/alts!
                      [ack
                       retransmit
                       (if (contains? outstandng-messages sequence-number)
                         [output [sequence-number
                                  (nth outstandng-messages sequence-number)]]
                         input)])]
      (cond
       (= chan output) (recur outstandng-messages (inc sequence-number))
       (= chan input) (recur (conj outstandng-messages val) sequence-number)
       (= chan ack) (if (= val (count outstandng-messages))
                      (recur (empty outstandng-messages) 0N)
                      (recur outstandng-messages sequence-number))
       (= chan retransmit) (recur outstandng-messages (bigint val)))))
  {:input input
   :output output
   :retransmit retransmit
   :ack ack})

(defn receiver [input output retransmit ack timeout]
  (go-loop [last-acked -1]
    (alt!
      input ([[n msg]]
               (if (= n (inc last-acked))
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
