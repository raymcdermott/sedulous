(ns durable.log
  "Ensure that N side effecting functions are ran to completion."
  (:import (clojure.lang ArityException)))

(def clojure-exceptions #{ArityException
                          ClassCastException})

(defn throw-exception?
  "This should only happen when there is a bug in the code."
  [e]
  (contains? clojure-exceptions (class e)))

(defn run-effect
  "Execute the form, we assume to be side-effecting, and capture its success or failure."
  [effect]
  (try
    (let [response (effect)]
      {:status :success
       :response response})
    (catch Exception e
      {:status :failed :exception e})))

(defn effect-loop
  "Run the effect until it succeeds or must be abandoned.
  To be atomic, must be called within a ref swap! or reset!"
  [tx-log tx-key form-key]
  (when-let [f (get-in tx-log [tx-key form-key :form])]
    (prn :effect-loop :f f)
    (loop []
      ;; ADVANCED - add features such as max-retries, ignored-exceptions and
      ;; an exponential back-off strategy
      ;; FURTHER - async
      (let [{:keys [status exception]
             :as effect-result} (run-effect f)]
        (prn :effect-loop :result effect-result)
        (case status
          :success (let [updated-log (update tx-log tx-key dissoc form-key)
                         n-remaining-keys (-> (get updated-log tx-key) keys count)]
                     (cond-> updated-log
                             (zero? n-remaining-keys) (dissoc tx-key)))
          :failed (do
                    (update-in tx-log [tx-key form-key :calls] (comp vec conj) effect-result)
                    (if (throw-exception? exception)
                      (throw exception)
                      (recur))))))))

(defn process-log
  "The log is a sorted map of [ key | action ]
  that may need to be replayed or discarded."
  [log]
  (prn :process-log :start)
  (->> @log
       (reduce
         ;; we need to process per key
         (fn [the-log [k v]]
           (prn :process-log :k k :v v)
           the-log
           #_(swap! the-log effect-loop k v))
         log)))

;; TODO duratom
(def log-atom (atom {}))

;; Ensure that the log is processed when the ns is loaded
;; so that any outstanding txns are resumed / completed.
(process-log log-atom)

(defn begin-tracking
  [k]
  (swap! log-atom assoc k {}))

(defn register-form
  "Create a log entry for the form"
  [tx-key form-key form]
  (swap! log-atom update tx-key assoc form-key {:form form})
  form-key)

(defn track-form-execution
  "Invoke the form until completion"
  [tx-key form-key]
  (swap! log-atom effect-loop tx-key form-key))

(defn metadata->form-key
  [form-meta f]
  (assoc form-meta :file *file*
                   :ns *ns*
                   :form f))

(defn metadata->tx-key
  "Add ns and file metadata to the given form-meta"
  [form-meta now]
  (assoc form-meta :file *file*
                   :ns *ns*
                   :start now))

(defn do-it
  [f]
  (let [form-meta {:will-be-filled :by-macro}
        now (System/nanoTime)
        tracking-key (metadata->tx-key form-meta now)]
    (begin-tracking tracking-key)
    (let [form-key (metadata->form-key form-meta f)]
      (register-form tracking-key form-key f)
      (track-form-execution tracking-key form-key))))

(defn log-run-effect
  [tracking-key form-meta effect]
  (let [form-key (metadata->form-key form-meta effect)]
    (register-form tracking-key form-key effect)
    (track-form-execution tracking-key form-key)))

(defn log-run-effects
  [tracking-key form-meta effects]
  (if-not (coll? effects)
    (log-run-effect tracking-key form-meta effects)
    (doseq [effect effects]
      (log-run-effect tracking-key form-meta effect))))

(defmacro with-tx
  ;; TODO ...
  ;:opts args to configure behaviour
  [fn-list]
  `(let [form-meta# ~(meta &form)
         now# (System/nanoTime)
         tracking-key# (metadata->tx-key form-meta# now#)]
     (begin-tracking tracking-key#)
     (log-run-effects tracking-key# form-meta# ~fn-list)))

(comment

  ;; base cases - 1 function
  (with-tx #(prn :effect (rand-int 10)))

  ;; base cases - n functions
  (with-tx [#(prn :effect0 (rand-int 10))
            #(prn :effect1 (rand-int 10))])

  ;; function that throws but will (in all likelihood succeed)
  (with-tx #(let [x (rand-int 10)]
              (if (even? x)
                (throw (ex-info "I can't even" {:x x}))
                (prn :effect1 (str "I fuck with " x)))))

  ;; mix of above
  (with-tx [#(prn :effect0 (rand-int 10))
            #(let [x (rand-int 10)]
               (if (even? x)
                 (throw (ex-info "I can't even" {:x x}))
                 (prn :effect1 (str "I fuck with " x))))])

  )