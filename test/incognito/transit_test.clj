(ns incognito.transit-test
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [incognito.transit :refer :all])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [com.cognitect.transit.impl WriteHandlers$MapWriteHandler]))

(defrecord Bar [a b])

(deftest incognito-roundtrip-test
  (testing "Test incognito transport."
    (let [bar (map->Bar {:a [1 2 3] :b {:c "Fooos"}})]
      (is (= #incognito.base.IncognitoTaggedLiteral{:tag incognito.transit_test.Bar,
                                                    :value {:a [1 2 3],
                                                            :b {:c "Fooos"}
                                                            :c "banana"}}
             (with-open [baos (ByteArrayOutputStream.)]
               (let [writer (transit/writer baos :json
                                            {:handlers {java.util.Map
                                                        (incognito-write-handler
                                                         (atom {'incognito.transit_test.Bar
                                                                (fn [foo] (assoc foo :c "banana"))}))}})]
                 (transit/write writer bar)
                 (let [bais (ByteArrayInputStream. (.toByteArray baos))
                       reader (transit/reader bais :json
                                              {:handlers {"incognito"
                                                          (incognito-read-handler (atom {}))}})]
                   (transit/read reader)))))))))

#_(defrecord DateTime [ts])

#_(deftest incognito-roundtrip-date-time-test
  (testing "Test incognito date-time transport."
    (let [now (t/now)]
      (is (= #incognito.base.IncognitoTaggedLiteral{:tag 'org.joda.time.DateTime,
                                                    :value "2017-04-17T13:11:29.977Z"}
             (with-open [baos (ByteArrayOutputStream.)]
               (let [writer (transit/writer baos :json
                                            {:handlers {org.joda.time.DateTime 
                                                        (incognito-write-handler
                                                         (atom {'org.joda.time.DateTime
                                                                (fn [r] (->DateTime (str r)))}))}})]
                 (transit/write writer now)
                 (let [bais (ByteArrayInputStream. (.toByteArray baos))
                       reader (transit/reader bais :json
                                              {:handlers {"incognito"
                                                          (incognito-read-handler (atom {}))}})]
                   (transit/read reader)))))))))

(deftest double-roundtrip-test
  (testing "Test two roundtrips, one incognito and deserialize at end."
    (let [bar (map->Bar {:a [1 2 3] :b {:c "Fooos"}})]
      (is (= bar
             (with-open [baos (ByteArrayOutputStream.)]
               (let [writer (transit/writer baos :json
                                            {:handlers {java.util.Map
                                                        (incognito-write-handler
                                                         (atom {}))}})]
                 (transit/write writer bar)
                 (let [bais (ByteArrayInputStream. (.toByteArray baos))
                       reader (transit/reader bais :json
                                              {:handlers {"incognito"
                                                          (incognito-read-handler (atom {'incognito.transit_test.Bar map->Bar}))}})]
                   (with-open [baos (ByteArrayOutputStream.)]
                     (let [writer (transit/writer baos :json
                                                  {:handlers {java.util.Map
                                                              (incognito-write-handler
                                                               (atom {}))}})]
                       (transit/write writer (transit/read reader))
                       (let [bais (ByteArrayInputStream. (.toByteArray baos))
                             reader (transit/reader bais :json
                                                    {:handlers {"incognito"
                                                                (incognito-read-handler (atom {'incognito.transit_test.Bar map->Bar}))}})]
                         (transit/read reader))))))))))))
