#? (:clj
    (ns com.manigfeald.roundabout.cluster
      (:require [clojure.core.async
                 :refer [alt! go-loop go]
                 :as async]))
    :cljs
    (ns com.manigfeald.roundabout.cluster
      (:require [cljs.core.async :as async])
      (:require-macros [cljs.core.async.macros :refer [alt! go-loop go]])))


;; (require '[clojure.spec.alpha :as s])

;; (s/def ::address any?)

;; (s/def msg (s/keys :req-un [::other ::type ::seq-n ::from ::to]))
;; (s/def ::other (s/tuple ::address #{:alive :suspect :dead}))
;; (s/def ::type #{:ping :indirect-ping :response})
;; (s/def ::seq-n int?)
;; (s/def ::from ::address)
;; (s/def ::to ::address)

;; (s/fdef jitter
;;         :args (s/cat :rtt pos-int?)
;;         :ret pos-int?)

(defn jitter [rtt]
  (long (* rtt (min 0.85 (max 0.5 (rand))))))

;; (s/fdef step
;;         :args (s/cat :state (s/nilable #{:alive :suspect :dead})
;;                      :event #{:ping :timeout})
;;         :ret #{:alive :suspect :dead})

(defn step [state event]
  (case [state event]
    [nil :ping] :alive
    [nil :timeout] :suspect
    [:alive :ping] :alive
    [:alive :timeout] :suspect
    [:suspect :timeout] :dead
    [:suspect :ping] :alive
    [:dead :timeout] :dead
    [:dead :ping] :alive))

(defonce n (atom 0))

(defn next-s! []
  (swap! n unchecked-inc))

;; (s/fdef indirect-ping
;;         :args (s/cat :peer ::address
;;                      :peers #(instance? clojure.lang.Atom %)
;;                      :responders-pub #(satisfies? async/Pub %)
;;                      :k pos-int?
;;                      :out #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                      :me ::address
;;                      :to-gossip #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                      :rtt pos-int?)
;;         :ret #(satisfies? clojure.core.async.impl.protocols/Channel %))

(defn indirect-ping [peer peers responders-pub k out me to-gossip rtt]
  (go
    (when (seq @peers)
      (let [seq-n (next-s!)
            c (async/sub responders-pub seq-n (async/chan k))]
        (doseq [ipeer (async/<! (async/into [] (async/take k to-gossip)))]
          (async/>! out
                    {:from me :to ipeer :type :indirect-ping
                     :seq-n seq-n :r peer :other (find @peers (async/<! to-gossip))}))
        (alt!
          (async/timeout rtt)
          ([_]
             (async/close! c)
             (async/unsub-all responders-pub seq-n)
             (swap! peers update peer step :timeout))
          c
          ([_]
             (async/close! c)
             (async/unsub-all responders-pub seq-n)
             (swap! peers update peer step :ping)))))))

;; (s/fdef failure-detector
;;         :args (s/cat :peers #(instance? clojure.lang.Atom %)
;;                      :close #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                      :responders-pub #(satisfies? async/Pub %)
;;                      :out #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                      :me ::address
;;                      :to-gossip #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                      :rtt pos-int?
;;                      :k pos-int?)
;;         :ret #(satisfies? clojure.core.async.impl.protocols/Channel %))

(defn failure-detector
  [peers close responders-pub out me to-gossip rtt k]
  (go-loop []
    (alt!
      close
      ([_])
      to-gossip
      ([peer]
         (if (= peer me)
           (swap! peers update peer step :ping)
           (let [seq-n (next-s!)
                 c (async/sub responders-pub seq-n (async/chan 1))]
             (async/>! out {:from me :to peer :type :ping
                            :seq-n seq-n :other (find @peers (async/<! to-gossip))})
             (alt!
               (async/timeout rtt)
               ([_]
                  (async/close! c)
                  (async/unsub-all responders-pub seq-n)
                  (swap! peers update peer step :timeout)
                  (when (= (get @peers peer) :suspect)
                    (async/<! (indirect-ping peer peers responders-pub k out me to-gossip rtt))))
               c
               ([_]
                  (async/close! c)
                  (async/unsub-all responders-pub seq-n)
                  (swap! peers update peer step :ping)))))))
    (async/<! (async/timeout (jitter rtt)))
    (recur)))

;; (s/fdef not-swim
;;         :args (s/cat
;;                :in #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                :out #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                :close #(satisfies? clojure.core.async.impl.protocols/Channel %)
;;                :me ::address
;;                :seed (s/coll-of :address :into [])
;;                :rtt pos-int?
;;                :k pos-int?
;;                :peers #(instance? clojure.lang.Atom %))
;;         :ret #(satisfies? clojure.core.async.impl.protocols/Channel %))

(defn not-swim [in out close me seed rtt k peers]
  (doseq [peer seed]
    (swap! peers assoc peer :alive))
  (let [to-gossip (async/chan (count seed))
        n (atom 0)
        responders (async/chan 1)
        responders-pub (async/pub responders :seq-n)
        cluster-view (async/chan (async/sliding-buffer 1))]
    (go-loop []
      (if (seq @peers)
        ;; TODO: put in just peer not peer and state, lookup state of peer as needed
        (doseq [item (shuffle (keys @peers))]
          (async/>! to-gossip item))
        (async/<! (async/timeout (long (/ rtt 3)))))
      (recur))
    (go-loop []
      (async/<! (async/timeout (* 10 rtt)))
      (swap! peers (fn [m] (into {} (for [[k v] m :when (not= v :dead)] [k v]))))
      (recur))
    (go-loop []
      (async/>! cluster-view @peers)
      (alt!
        close
        ([_] (async/close! cluster-view))
        in
        ([msg]
           (when (:other msg)
             (swap! peers conj (:other msg)))
           (swap! peers assoc (:from msg) :alive)
           (async/put! to-gossip (:from msg))
           (case (:type msg)
             :ping
             (async/>! out {:from me :type :response :seq-n (:seq-n msg) :to (:from msg)
                            :other (find @peers (async/<! to-gossip))})
             :indirect-ping
             (let [n-s (next-s!)
                   gossip (find @peers (async/<! to-gossip))
                   c (async/sub responders-pub n-s (async/chan 1))]
               (go
                 (alt!
                   (async/timeout rtt)
                   ([_]
                      (async/close! c)
                      (async/unsub-all responders-pub n-s))
                   c
                   ([_]
                      (async/close! c)
                      (async/unsub-all responders-pub n-s)
                      (async/>! out {:from me
                                     :to (:from msg)
                                     :type :response
                                     :other gossip
                                     :seq-n (:seq-n msg)}))))
               (async/>! out {:from me :to (:r msg) :type :ping
                              :seq-n n-s
                              :other (find @peers (async/<! to-gossip))}))
             :response (async/>! responders msg))))
      (recur))
    (failure-detector peers close responders-pub out me to-gossip rtt k)
    cluster-view))
