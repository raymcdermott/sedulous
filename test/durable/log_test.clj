(ns durable.log-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [durable.log :as sut]))


(defn form-keys
  [tracking-key]
  (keys (get @sut/log-atom tracking-key)))

(defn form-key
  [tracking-key]
  (-> tracking-key form-keys first))

(defn key-path->result
  [tracking-key form-key]
  (let [call-id (get-in @sut/log-atom [tracking-key form-key :call-id])]
    (get-in @sut/log-atom [tracking-key form-key :call call-id :result])))

(defn effect-io []
  (prn (rand-int 10)))

(defn effect-io-maybe-exception
  "Without options maybe throws, with an option can be predicted."
  ([]
   (effect-io-maybe-exception (rand-int 10)))
  ([x]
   (if (even? x)
     (throw (ex-info "I can't even" {:x x}))
     (prn (str "Oddly " x)))))

(use-fixtures :each (fn [f]
                      (sut/reset-log! sut/log-atom)
                      (f)))

(deftest base-case-test
  (testing "An effect that will very likely work"
    (sut/with-tx effect-io)
    (let [tracking-key (first (keys @sut/log-atom))
          result (->> (form-key tracking-key)
                      (key-path->result tracking-key))]
      (is (= {:status :success :response nil}
             result)))))

(deftest base-cases-test
  (testing "Several effects that will most likely work"
    (sut/with-tx [effect-io effect-io])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (form-keys tracking-key)
              :let [result (key-path->result tracking-key form-key)]]
        (is (= {:status :success :response nil}
               result))))))

(deftest maybe-failing-case-test
  (testing "An effect that can fail but most likely work after a few attempts"
    (sut/with-tx effect-io-maybe-exception)
    (let [tracking-key (first (keys @sut/log-atom))
          result (->> (form-key tracking-key)
                      (key-path->result tracking-key))]
      (is (= {:status :success :response nil}
             result)))))

(deftest maybe-failing-cases-test
  (testing "Effects that can fail but most likely work after a few attempts"
    (sut/with-tx [effect-io-maybe-exception
                  effect-io-maybe-exception])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (form-keys tracking-key)
              :let [result (key-path->result tracking-key form-key)]]
        (is (= {:status :success :response nil}
               result))))))

(deftest mix-cases-test
  (testing "Mix of effects that can and will most likely work"
    (sut/with-tx [effect-io
                  effect-io-maybe-exception])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (form-keys tracking-key)
              :let [result (key-path->result tracking-key form-key)]]
        (is (= {:status :success :response nil}
               result))))))