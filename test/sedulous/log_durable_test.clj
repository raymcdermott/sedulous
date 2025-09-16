(ns sedulous.log-durable-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [sedulous.log :as sut]))

(set! *warn-on-reflection* true)

(defn effect-io-prn-maybe-exception
  "Without options maybe throws, with an option can be predicted."
  ([]
   (effect-io-prn-maybe-exception (rand-int 10)))
  ([n]
   (if (even? n)
     (throw (ex-info "I can't even" {:n n}))
     (prn (str "Oddly " n)))))

(use-fixtures :each (fn [f]
                      (sut/reset-log! sut/log-atom)
                      (f)))

(deftest durable-base-test
  (testing "we re-try after interruption"
    (sut/sedulously
      (partial effect-io-prn-maybe-exception 2)
      {:max-tries 3})
    (let [tracking-key (first (keys @sut/log-atom))
          result (->> (sut/form-key @sut/log-atom tracking-key)
                      (sut/key-path->result @sut/log-atom tracking-key))]
      (is (= [:status :exception] (keys result)))
      (is (= :failed
             (:status result)))
      (is (= {:n 2}
             (ex-data (:exception result)))))))




