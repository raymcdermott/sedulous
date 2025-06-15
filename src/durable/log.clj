(ns durable.log
  (:import (clojure.lang ArityException)))

(def clojure-exceptions #{ArityException
                          ClassCastException})

(defn throw-exception?
  "This should only happen when there is a bug in the code."
  [e]
  (contains? clojure-exceptions (class e)))

(defn invoke
  "Execute the form, then remove its key and, on the final form, the tx-key.
  To be atomic, must be called within a ref swap! or reset!"
  [tx-log tx-key form-key]
  (when-let [f (get-in tx-log [tx-key form-key :form])]
    ;; TODO (ADVANCED) put some extra data on the form-key map ... not just the function
    ;; eg call count, call time, return vals, exceptions and then check with opts
    ;; such as max-retries, ignored-exceptions and whether we should sleep as part
    ;; of a back-off strategy
    (let [call-info (atom {})]
      (try
        (f)
        (catch Exception e
          (reset! call-info {:failed? true :exception e})
          (when (true? (throw-exception? e))
            (clojure.pprint/pprint [:error e])
            (throw e)))
        (finally
          (when (:failed? @call-info)
            (prn :call-failed (merge (get-in tx-log [tx-key form-key])
                                     @call-info)))
          (if (:failed? @call-info)
            (update-in tx-log [tx-key form-key :calls] (comp vec conj) @call-info)
            (let [updated-log (update tx-log tx-key dissoc form-key)
                  n-remaining-keys (-> (get updated-log tx-key) keys count)]
              (cond-> updated-log
                      (zero? n-remaining-keys) (dissoc tx-key)))))))))

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
           #_(swap! the-log invoke k v))
         log)))

(def log-atom (atom {}))

;; Ensure that the log is processed when the ns is loaded
;; so that any outstanding txns are resumed / completed.
(process-log log-atom)

(defn begin-tracking
  [k]
  (swap! log-atom assoc k {}))

(defn track-form-execution
  [tx-key form-key form]
  ;; create a log entry for the form
  (swap! log-atom update tx-key assoc form-key {:form form})
  ;; invoke the form and then remove the log entry
  (swap! log-atom invoke tx-key form-key))

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

(defmacro with-tx
  ;; TODO ...
  ;n forms
  ;:opts args to configure behaviour
  "So far ... one form only"
  [f]
  `(let [form-meta# ~(meta &form)
         now# (System/nanoTime)
         tracking-key# (metadata->tx-key form-meta# now#)]
     (begin-tracking tracking-key#)
     (let [form-key# (metadata->form-key form-meta# ~f)]
       (track-form-execution tracking-key# form-key# ~f))))

(comment

  (with-tx #(let [x (first (shuffle (range 10)))]
              (if (= x (rand-int 10))
                (throw (ex-info "Random" {:x x}))
                (prn x))))

  (defn form-1 [] (rand-int))
  (defn form-2 [i] (* 2 i))

  (let [file-meta-magic (str "File:foo#line123")
        tx-k (begin-tracking *ns* file-meta-magic)
        form-k1 (form-reference tx-k form-1)
        form-k2 (form-reference tx-k form-2)
        x (track-form-execution tx-k form-k1 form-1)]
    (track-form-execution tx-k form-k2 form-2 x)))