# incognito

Different Clojure(Script) serialization protocols like `edn`, `fressian` or
`transit` offer different ways to serialize custom types. In general
they fall back to maps for unknown record types, which is a reasonable
default in many situations. But when you build a distributed data
management system parts of your system might not care about the record
types while others do. This library safely wraps unknown record types
and therefore allows to unwrap them later. It also unifies record
serialization between `fressian` and `transit` as long as you can
express your serialization format in Clojure's default datastructures.

The general idea is that most custom Clojure datatypes (either records or
deftypes) can be expressed in Clojure datastructures if you do not need a custom
binary format, e.g. for efficiency or performance. With incognito you do not
need custom handlers for every serialization format. But you can still provide
them of course, once you hit efficiency problems. Incognito is at the moment not
supposed to provide serialization directly to storage, so you have to be able to
serialize your custom types in memory.

Incognito falls back to a `pr-str->read-string` roundtrip which is a reasonable,
but inefficient and will only work if the type has proper Clojure print+read
support. 

We use it for instance to carry the custom type of
a [datascript db](https://github.com/tonsky/datascript)
in [topiq](https://github.com/replikativ/topiq). You don't need to provide a
write handler for incognito except for efficiency reasons or if your type is not
pr-strable (in which case you should make it then first).

## Usage

Add this to your project dependencies:
[![Clojars Project](http://clojars.org/io.replikativ/incognito/latest-version.svg)](http://clojars.org/io.replikativ/incognito)

Include all serialization libraries you need, e.g. for edn support only:
```clojure
[org.clojure/data.fressian com.cognitect/transit-clj "0.8.297"]
[io.replikativ/incognito "0.2.5"]
```

In general you can control serialization by `write-handlers` and `read-handlers`:

```clojure
(defrecord Bar [a b])

(def write-handlers (atom {'user.Bar (fn [bar] bar)}))
(def read-handlers (atom {'user.Bar map->Bar}))
```

To handle custom non-record types you have to transform it into a readable
data-structure. You can test the roundtrip directly with incognito-reader and
writer:

```clojure
(require '[clj-time.core :as t])
(require '[clj-time.format :as tf])

(incognito-writer 
  ;; read-handlers
  {'org.joda.time.DateTime (fn [r] (str r))}

  (t/now))

(incognito-reader 
  ;; write-handlers
  {'org.joda.time.DateTime (fn [r] (t/date-time r))}

  {:tag 'org.joda.time.DateTime, :value "2017-04-17T13:11:29.977Z"})

```
*NOTE*: The syntax quote for the read handler is necessary so you can 
deserialize unknown classes.

A write-handler has to return an associative datastructure which is
internally stored as an untyped map together with the tag information.


## Plugging incognito into your serializer

You need to wrap the base map handler in the different serializers so incognito
can wrap the serialization for its own handlers. 

(Extracted from the tests):

### edn

```clojure
(require '[incognito.edn :refer [read-string-safe]])

(let [bar (map->Bar {:a [1 2 3] :b {:c "Fooos"}})]
  (= bar (->> bar
              pr-str
              (read-string-safe {})
              pr-str
              (read-string-safe read-handlers))))
```


### transit
```clojure
(require '[incognito.transit :refer [incognito-write-handler incognito-read-handler]]
         '[cognitect.transit :as transit])

(let [bar (map->Bar {:a [1 2 3] :b {:c "Fooos"}})]
  (= (assoc bar :c "banana")
     (with-open [baos (ByteArrayOutputStream.)]
       (let [writer (transit/writer baos :json
                                    {:handlers {java.util.Map
                                                (incognito-write-handler
                                                 write-handlers)}})]
         (transit/write writer bar)
         (let [bais (ByteArrayInputStream. (.toByteArray baos))
               reader (transit/reader bais :json
                                      {:handlers {"incognito"
                                                  (incognito-read-handler read-handlers)}})]
           (transit/read reader))))))
```

### fressian

```clojure
(require '[clojure.data.fressian :as fress]
         '[incognito.fressian :refer [incognito-read-handlers
                                      incognito-write-handlers]])

(let [bar (map->Bar {:a [1 2 3] :b {:c "Fooos"}})]
  (= (assoc bar :c "banana")
     (with-open [baos (ByteArrayOutputStream.)]
       (let [w (fress/create-writer baos
                                    :handlers
                                    (-> (merge fress/clojure-write-handlers
                                               (incognito-write-handlers write-handlers))
                                        fress/associative-lookup
                                        fress/inheritance-lookup))] ;
         (fress/write-object w bar)
         (let [bais (ByteArrayInputStream. (.toByteArray baos))]
           (fress/read bais
                       :handlers
                       (-> (merge fress/clojure-read-handlers
                                  (incognito-read-handlers read-handlers))
                           fress/associative-lookup)))))))
```

## ClojureScript

For dashed namespace names you need a custom printer to be
ClojureScript conform.

```clojure
(defmethod print-method some_namespace.Bar [v ^java.io.Writer w]
  (.write w (str "#some-namespace.Bar" (into {} v))))
```


## TODO

- move serialization dependencies into dev profile


## Changelog

### 0.2.5
- ClojureScript fressian support (for konserve filestore on node.js). Thanks to
  Ferdinand Kühne!


## License

Copyright © 2015-2019 Christian Weilbach, Ferdinand Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
