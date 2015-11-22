(ns com.manigfeald.roundabout
  (:require [clojure.core.async :as async]))

(defn sender
  "Takes 4 channels as arguments. Reads data from the input
  channel. Writes data to the output channel. Stops if the abort
  channel is closed, or anything is written to it. Reads a number from
  the feedback channel, copies that many items from the input channel
  to the output channel, then reads another number from the feed back
  channel. Returns a map of the arguments."
  [input output feedback abort]
  (async/go-loop [n 0]
    (let [[val chan] (async/alts! (concat [feedback
                                           abort]
                                          (when (pos? n)
                                            [input])))]
      (cond
       (= chan abort) nil
       (= chan feedback) (when val
                           (assert (number? val))
                           (assert (not (neg? val)))
                           (recur val))
       (= chan input) (when val
                        (async/alt!
                          [[output val]] ([_] (recur (dec n)))
                          abort nil)))))
  {:input input
   :output output
   :feedback feedback
   :abort abort})

;; TODO: timeout
(defn receiver
  "Takes 4 channels and a number. Sends window-size to the feedback
  channel. Reads window-size items from the input channel and writes
  them to the output channel, then sends window-size to feedback
  again, in a loop. Returns a map of arguments minus
  window-size. window-size has an effect similar to buffer size for a
  core.async channel."
  [input output feedback abort window-size timeout]
  (async/go-loop [n window-size]
    (if (> n (dec window-size))
      (async/alt!
        abort nil
        [[feedback window-size]] ([_] (recur 0)))
      (async/alt!
        input ([item]
                 (when item
                   (async/alt!
                     [[output item]] ([_] (recur (inc n)))
                     abort nil)))
        (async/timeout timeout) (async/alt!
                                  abort nil
                                  [[feedback window-size]] ([_] (recur n)))
        abort nil)))
  {:input input
   :output output
   :feedback feedback
   :abort abort})
