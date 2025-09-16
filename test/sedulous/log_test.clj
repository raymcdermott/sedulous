(ns sedulous.log-test
  (:require
    [clojure.edn :as edn]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [sedulous.log :as sut])
  (:import (com.sun.net.httpserver Headers HttpServer SimpleFileServer SimpleFileServer$OutputLevel)
           (java.io File)
           (java.net InetSocketAddress URI)
           (java.net.http HttpClient HttpRequest HttpResponse HttpResponse$BodyHandlers)))

(set! *warn-on-reflection* true)

(defn effect-io-prn []
  (prn (rand-int 10)))

(defn effect-io-prn-maybe-exception
  "Without options maybe throws, with an option can be predicted."
  ([]
   (effect-io-prn-maybe-exception (rand-int 10)))
  ([n]
   (if (even? n)
     (throw (ex-info "I can't even" {:n n}))
     (prn (str "Oddly " n)))))

(defn free-port []
  (InetSocketAddress. 0))

(defn stop-http-server
  [^HttpServer server]
  (.stop server 0))

(defn start-http-server
  ^HttpServer [^InetSocketAddress address ^File directory]
  (let [path (.toPath directory)
        noise-level SimpleFileServer$OutputLevel/INFO
        server (SimpleFileServer/createFileServer address path noise-level)]
    (.start server)
    server))

(defn send-http-request
  ^HttpResponse [^HttpClient client uri]
  (let [request (-> (HttpRequest/newBuilder)
                    (.uri (URI. uri))
                    (.GET)
                    .build)]
    (.send client request (HttpResponse$BodyHandlers/ofString))))

(defn ->tmp-file
  (^File []
   (->tmp-file (File. (System/getProperty "java.io.tmpdir"))))
  (^File [directory]
   (File/createTempFile "test-" ".tmp" directory)))

(defn ->tmp-dir
  ^File []
  (let [dir-name (File/new (System/getProperty "java.io.tmpdir") (str (random-uuid)))]
    (.mkdir dir-name)
    dir-name))

(defn effect-io-file
  ([]
   (effect-io-file (File. (System/getProperty "java.io.tmpdir"))))
  ([directory]
   (let [file (->tmp-file directory)]
     (spit file (rand-int 10))
     (edn/read-string (slurp file)))))

(defn effect-io-file-maybe-exception
  ([]
   (effect-io-file-maybe-exception (File. (System/getProperty "java.io.tmpdir"))))
  ([directory]
   (effect-io-file-maybe-exception directory (rand-int 10)))
  ([directory n]
   (let [file (and (odd? n) (->tmp-file directory))]
     (spit file n)
     (edn/read-string (slurp file)))))

(defn http-effect
  ([]
   (http-effect "content"))
  ([test-contents]
   (http-effect test-contents 0))
  ([test-contents n]
   (let [dir (->tmp-dir)
         server (start-http-server (free-port) dir)
         file (->tmp-file dir)
         host (-> server .getAddress .getPort)
         uri (str "http://localhost:" host "/" (.getName file))]
     (spit file test-contents)
     (let [response (cond
                      (even? n)
                      (send-http-request (HttpClient/newHttpClient) uri)
                      (= 7 n)
                      (send-http-request (HttpClient/newHttpClient) "bad-uri")
                      (odd? n)
                      (send-http-request (HttpClient/newHttpClient) (str uri n)))]
       (stop-http-server server)
       {:body (.body response)
        :status-code (.statusCode response)
        :headers (.headers response)}))))

(defn http-effect-maybe-failing
  [test-contents]
  (http-effect test-contents (rand-int 10)))

(use-fixtures :each (fn [f]
                      (sut/reset-log! sut/log-atom)
                      (f)))

(deftest base-case-prn-test
  (testing "An effect that will very likely work"
    (sut/sedulously effect-io-prn)
    (let [tracking-key (first (keys @sut/log-atom))
          result (->> (sut/form-key @sut/log-atom tracking-key)
                      (sut/key-path->result @sut/log-atom tracking-key))]
      (is (= {:status :success :response nil}
             result)))))

(deftest base-case-file-test
  (testing "An effect that will very likely work"
    (sut/sedulously effect-io-file)
    (let [tracking-key (first (keys @sut/log-atom))
          {:keys [status response]} (->> (sut/form-key @sut/log-atom tracking-key)
                                         (sut/key-path->result @sut/log-atom tracking-key))]
      (is (= :success status))
      (is (int? response)))))

(deftest base-cases-prn-test
  (testing "Several effects that will most likely work"
    (sut/sedulously [effect-io-prn effect-io-prn])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (sut/form-keys @sut/log-atom tracking-key)
              :let [result (sut/key-path->result @sut/log-atom tracking-key form-key)]]
        (is (= {:status :success :response nil}
               result))))))

(deftest base-cases-file-test
  (testing "Several effects that will most likely work"
    (sut/sedulously [effect-io-file effect-io-file])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (sut/form-keys @sut/log-atom tracking-key)
              :let [{:keys [status response]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
        (is (= :success status))
        (is (int? response))))))

(deftest maybe-failing-prn-test
  (testing "An effect that can fail but most likely work after a few attempts"
    (sut/sedulously effect-io-prn-maybe-exception)
    (let [tracking-key (first (keys @sut/log-atom))
          result (->> (sut/form-key @sut/log-atom tracking-key)
                      (sut/key-path->result @sut/log-atom tracking-key))]
      (is (= {:status :success :response nil}
             result)))))

(deftest maybe-failing-file-test
  (testing "An effect that can fail but most likely work after a few attempts"
    (sut/sedulously effect-io-file-maybe-exception)
    (let [tracking-key (first (keys @sut/log-atom))
          {:keys [status response]} (->> (sut/form-key @sut/log-atom tracking-key)
                                         (sut/key-path->result @sut/log-atom tracking-key))]
      (is (= :success status))
      (is (int? response)))))

(deftest maybe-failing-cases-prn-test
  (testing "Effects that can fail but most likely work after a few attempts"
    (sut/sedulously [effect-io-prn-maybe-exception
                     effect-io-prn-maybe-exception])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (sut/form-keys @sut/log-atom tracking-key)
              :let [result (sut/key-path->result @sut/log-atom tracking-key form-key)]]
        (is (= {:status :success :response nil}
               result))))))

(deftest maybe-failing-cases-file-test
  (testing "Effects that can fail but most likely work after a few attempts"
    (sut/sedulously [effect-io-file-maybe-exception
                     effect-io-file-maybe-exception])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (sut/form-keys @sut/log-atom tracking-key)
              :let [{:keys [status response]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
        (is (= :success status))
        (is (int? response))))))

(deftest mix-cases-test
  (testing "Mix of effects that can and will most likely work"
    (sut/sedulously [effect-io-prn
                     effect-io-prn-maybe-exception
                     effect-io-file
                     effect-io-file-maybe-exception])
    (let [tracking-key (first (keys @sut/log-atom))]
      (doseq [form-key (take 2 (sut/form-keys @sut/log-atom tracking-key))
              :let [result (sut/key-path->result @sut/log-atom tracking-key form-key)]]
        (is (= {:status :success :response nil}
               result)))
      (doseq [form-key (drop 2 (sut/form-keys @sut/log-atom tracking-key))
              :let [{:keys [status response]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
        (is (= :success status))
        (is (int? response))))))

(deftest base-case-http-test
  (testing "An effect that can fail but most likely work after a few attempts"
    (let [content (str (random-uuid))]
      (sut/sedulously (partial http-effect content))
      (let [tracking-key (first (keys @sut/log-atom))
            {{:keys [body status-code]} :response
             :keys [status]} (->> (sut/form-key @sut/log-atom tracking-key)
                                  (sut/key-path->result @sut/log-atom tracking-key))]
        (is (= :success status))
        (is (= 200 status-code))
        (is (= content body))))))

(deftest base-cases-http-test
  (testing "Several effects that will most likely work"
    (let [content (str (random-uuid))]
      (sut/sedulously [(partial http-effect content)
                       (partial http-effect content)])
      (let [tracking-key (first (keys @sut/log-atom))]
        (doseq [form-key (sut/form-keys @sut/log-atom tracking-key)
                :let [{:keys [status response]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
          (is (= :success status))
          (is (= content (:body response))))))))

(deftest maybe-failing-http-test
  (testing "An effect that can fail but most likely work after a few attempts"
    (let [content (str (random-uuid))]
      (sut/sedulously (partial http-effect-maybe-failing content))
      (let [tracking-key (first (keys @sut/log-atom))
            {{:keys [body status-code]} :response
             :keys [status]} (->> (sut/form-key @sut/log-atom tracking-key)
                                  (sut/key-path->result @sut/log-atom tracking-key))]
        (is (= :success status))
        (condp = status-code
          200 (is (= content body))
          404 (is (= "<h1>File not found</h1>" (re-find #".*File not found.*" body))))))))

(deftest maybe-failing-cases-file-test
  (testing "Effects that can fail but most likely work after a few attempts"
    (let [content (str (random-uuid))]
      (sut/sedulously [(partial http-effect-maybe-failing content)
                       (partial http-effect-maybe-failing content)])
      (let [tracking-key (first (keys @sut/log-atom))]
        (doseq [form-key (sut/form-keys @sut/log-atom tracking-key)
                :let [{{:keys [body status-code]} :response
                       :keys [status]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
          (is (= :success status))
          (condp = status-code
            200 (is (= content body))
            404 (is (= "<h1>File not found</h1>" (re-find #".*File not found.*" body)))))))))

(deftest mix-cases-test
  (testing "Mix of effects that can and will most likely work"
    (let [content (str (random-uuid))]
      (sut/sedulously [effect-io-prn
                       effect-io-prn-maybe-exception
                       effect-io-file
                       effect-io-file-maybe-exception
                       (partial http-effect content)
                       (partial http-effect-maybe-failing content)])
      (let [tracking-key (first (keys @sut/log-atom))]
        (doseq [form-key (take 2 (sut/form-keys @sut/log-atom tracking-key))
                :let [result (sut/key-path->result @sut/log-atom tracking-key form-key)]]
          (is (= {:status :success :response nil}
                 result)))
        (doseq [form-key (drop 2 (take 4 (sut/form-keys @sut/log-atom tracking-key)))
                :let [{:keys [status response]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
          (is (= :success status))
          (is (int? response)))
        (doseq [form-key (drop 4 (sut/form-keys @sut/log-atom tracking-key))
                :let [{{:keys [body status-code]} :response
                       :keys [status]} (sut/key-path->result @sut/log-atom tracking-key form-key)]]
          (is (= :success status))
          (condp = status-code
            200 (is (= content body))
            404 (is (= "<h1>File not found</h1>" (re-find #".*File not found.*" body)))))))))


