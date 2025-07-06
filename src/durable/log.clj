(ns durable.log
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
  "Execute the form, we assume to be side-effecting, and capture its response,
  success or failure which is returned as a map of data."
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
  [log tx-key form-key call-id]
  (let [f (get-in @log [tx-key form-key :form])
        effect-result (run-effect f)]
    (swap! log assoc-in [tx-key form-key :call call-id :result] effect-result)))

(defn register-form
  "Create an entry in `log` for the `form` within the transaction under `tx-key`
  using the `form-key`."
  [log tx-key form-key form]
  (swap! log update tx-key assoc form-key {:form form}))

(defn- next-call-id [log tx-key form-key]
  (let [update-path [tx-key form-key :call-id]]
    (-> (swap! log update-in update-path (fnil inc 0))
        (get-in update-path))))

(defn track-form-execution
  "Invoke the form until completion"
  [log tx-key form-key {:keys [max-tries]}]
  (loop [call-id (next-call-id log tx-key form-key)]
    (let [updated-log (effect! log tx-key form-key call-id)
          {:keys [status exception] :as result} (get-in updated-log [tx-key form-key :call call-id :result])]
      (cond
        (= call-id max-tries)
        (prn :track-form-execution :bailing result)

        (= :success status)
        (prn :track-form-execution :success :data result)

        (and exception (clojure-exception? exception))
        (prn :track-form-execution :clojure-exception :data result)

        :else
        (do (prn :track-form-execution :retrying status (and exception (ex-message exception)))
            (recur (next-call-id log tx-key form-key)))))))

(defn metadata->form-key
  [form-meta f]
  (assoc form-meta :file *file*
                   :ns *ns*
                   :form f))

(defn metadata->tx-key
  "Add ns and file info to the given form-meta"
  [form-meta now]
  (assoc form-meta :file *file*
                   :ns *ns*
                   :start now))

(defn log-run-effects
  [log tracking-key form-meta effects & {:keys [max-tries]
                                         :or {max-tries Long/MAX_VALUE}
                                         :as options}]
  (let [effect-list (if (coll? effects) effects [effects])
        form-keys (->> effect-list
                       (mapv (fn [effect]
                              (let [form-key (metadata->form-key form-meta effect)]
                                (register-form log tracking-key form-key effect)
                                form-key))))]
    (doseq [form-key form-keys]
      (track-form-execution log tracking-key form-key options))))

(defn reset-log! [log]
  (reset! log {}))

(defn begin-tracking [log tx-key]
  (swap! log assoc tx-key {}))

;; TODO duratom
(def log-atom (atom {}))

;; TODO
;; Ensure that the log is processed when the ns is loaded
;; so that any outstanding txns are resumed / completed.

(defmacro with-tx [fn-list & {:keys [_to-pass-on-map] :as options}]
  `(let [form-meta# ~(meta &form)
         now# (System/nanoTime)
         tracking-key# (metadata->tx-key form-meta# now#)]
     (begin-tracking log-atom tracking-key#)
     (log-run-effects log-atom tracking-key# form-meta# ~fn-list ~options)))

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