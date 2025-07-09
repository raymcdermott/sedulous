(ns sedulous.log
  "Ensure that N side effecting functions are ran to completion."
  (:import (clojure.lang ArityException)))

(def clojure-exceptions
  #{ArityException
    ClassCastException})

(defn clojure-exception?
  "This should only happen when there is a bug in the code."
  [e]
  (contains? clojure-exceptions (class e)))

(defn run-effect
  "Execute the form, we assume to be side-effecting, and capture its response.
  When the invocation is complete set the :status as success. If an exception is thrown
  set the :status as failed. The return is a map of `:status` and `:response`."
  [effect]
  (try
    (let [response (effect)]
      {:status :success :response response})
    (catch Exception e
      {:status :failed :exception e})))

;; ADVANCED - add features such as max-retries, ignored-exceptions and
;; an exponential back-off strategy
;; FURTHER - async

(defn effect!
  "Run the effect."
  [log txn-key form-key call-id]
  (let [f (get-in @log [txn-key :forms form-key :form])
        effect-result (run-effect f)]
    (swap! log assoc-in [txn-key :forms form-key :call call-id :result] effect-result)))

(defn register-form
  "Create an entry in `log` for the `form` within the transaction under `txn-key`
  using the `form-key`."
  [log txn-key form-key form]
  (swap! log update txn-key assoc-in [:forms form-key] {:form form}))

(defn- next-call-id [log txn-key form-key]
  (let [update-path [txn-key :forms form-key :call-id]]
    (-> (swap! log update-in update-path (fnil inc 0))
        (get-in update-path))))

(def try-forever -1)

(defn track-form-execution
  "Invoke the form until completion"
  [log txn-key form-key]
  (let [{:keys [max-tries]
         :or {max-tries try-forever}} (get-in @log [txn-key :options])]
    (loop [call-id (next-call-id log txn-key form-key)]
      (let [updated-log (effect! log txn-key form-key call-id)
            {:keys [status exception] :as result} (get-in updated-log [txn-key :forms form-key :call call-id :result])]
        (cond
          (= max-tries call-id)
          (prn :track-form-execution :bailing result)

          (= :success status)
          (prn :track-form-execution :success :data result)

          (and exception (clojure-exception? exception))
          (prn :track-form-execution :clojure-exception :data result)

          :else
          (do (prn :track-form-execution :retrying status (and exception (ex-message exception)))
              (recur (next-call-id log txn-key form-key))))))))

(defn form+metadata->form-key
  [form-meta f]
  (assoc form-meta :file *file*
                   :ns *ns*
                   :form f))

(defn metadata->txn-key
  "Add ns and file info to the given form-meta"
  [form-meta now]
  (assoc form-meta :file *file*
                   :ns *ns*
                   :start now))

(defn log-run-effects
  [log txn-key form-meta effects]
  (let [effect-list (if (coll? effects) effects [effects])
        form-keys (->> effect-list
                       (mapv (fn [effect]
                               (let [form-key (form+metadata->form-key form-meta effect)]
                                 (register-form log txn-key form-key effect)
                                 form-key))))]
    (doseq [form-key form-keys]
      (track-form-execution log txn-key form-key))

    #_(evaluate-completion log txn-key)
    ;; TODO evaluate and mark the success of tx-key
    ))

(defn reset-log! [log]
  (reset! log {}))

(defn begin-tracking [log tx-key options]
  (swap! log assoc tx-key {:options options
                           :forms {}}))

(defn form-keys
  [log tracking-key]
  (keys (get-in log [tracking-key :forms])))

(defn form-key
  [log tracking-key]
  (first (form-keys log tracking-key)))

(defn key-path->result
  [log tracking-key form-key]
  (let [call-id (get-in log [tracking-key :forms form-key :call-id])]
    (get-in log [tracking-key :forms form-key :call call-id :result])))

;; TODO duratom
(defonce log-atom (atom {}))


;; TODO fn to permit clean out of a completed tx-key

;; TODO fn to permit clean out of all completed tx-keys

;; TODO fn to permit clean out of an incomplete tx-key

;; TODO
;; Ensure that the log is processed when the ns is loaded
;; so that any outstanding txns are resumed / completed.

(defn recovery-execution
  [log txn-key]
  (let [options (get-in @log [txn-key :options])]
    (doseq [tx-data (form-keys @log txn-key)]
      (prn :recovery-execution :form-key form-key tx-data :options options))))

(def recover-log
  (doseq [txn-key (keys @log-atom)]
    (recovery-execution log-atom txn-key)))

(defmacro sedulously
  "Side effects as data.
  Executes one or more functions until completion."
  [fn-or-list-fns & {:keys [_to-pass-on-map] :as options}]
  `(let [form-meta# ~(meta &form)
         now# (System/nanoTime)
         txn-key# (metadata->txn-key form-meta# now#)]
     (begin-tracking log-atom txn-key# ~options)
     (log-run-effects log-atom txn-key# form-meta# ~fn-or-list-fns)))
