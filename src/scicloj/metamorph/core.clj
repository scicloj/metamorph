(ns scicloj.metamorph.core
  (:require [scicloj.metamorph.protocols :as prot]))


(defn uuid []
  (java.util.UUID/randomUUID))

(defn pipeline
  [& ops]
  (let [ops-with-id (mapv #(vector (uuid) %) ops)] ;; add uuid to each operation
    (fn local-pipeline
      ([] (local-pipeline {})) ;; can be called without a context
      ([ctx]
       (let [ctx (if-not (map? ctx)
                   {:metamorph/data ctx} ctx)] ;; if context is not a map, pack it to the map
         (reduce (fn [curr-ctx [id op]]         ;; go through operations
                   (if (map? op) ;; map means to be merged with following operation
                     (merge curr-ctx op) ;; set current mode
                     (-> curr-ctx
                         (assoc :metamorph/id (get curr-ctx :metamorph/id id)) ;; assoc id of the operation
                         (op)                       ;; call it
                         (dissoc :metamorph/id))))  ;; dissoc id
                 ctx ops-with-id) )))))

(declare process-param)

(defn- process-map
  [config params]
  (into {} (map (fn [[k v]]
                  [k (process-param config v)]) params)))

(defn- process-seq
  [config params]
  (mapv (fn [p] (process-param config p)) params))

(defn- resolve-keyword
  "Interpret keyword as a symbol and try to resolve it."
  [k]
  (-> (if-let [n (namespace k)] ;; namespaced?
        (let [sn (symbol n)
              n (str (get (ns-aliases *ns*) sn sn))] ;; try to find namespace in aliases
          (symbol n (name k))) ;; create proper symbol with fixed namespace
        (symbol (name k))) ;; no namespace case
      (resolve)))

(defn- maybe-var-get
  "If symbol can be resolved, return var, else return original keyword"
  [k]
  (if-let [rk (resolve-keyword k)]
    (var-get rk)
    k))

(defn- process-param
  "Recursively process parameters and try to resolve symbols for namespaced keywords.

  Special case for namespaced keyword is `ctx` namespace. It means that we should look up in `config` map."
  [config p]
  (cond
    (and (keyword? p) ;; 
         (let [n (namespace p)]
           (and n (or (= n "ctx")
                      (let [sn (symbol n)]
                        (find-ns (get (ns-aliases *ns*) sn sn))))))) (let [n (namespace p)]
                                                                       (if (= n "ctx")
                                                                         (config (keyword (name p)))
                                                                         (maybe-var-get p)))
    (map? p) (process-map config p)
    (sequential? p) (process-seq config p)
    :else p))

(defn ->pipeline
  "Create pipeline from declarative description."
  ([ops] (->pipeline {} ops))
  ([config ops]
   (apply pipeline (for [line ops]
                     (cond
                       ;; if it's a sequence, resolve function, process parameters and call it.
                       (sequential? line) (let [[op & params] line
                                                nparams (process-param config params)
                                                f (cond
                                                    (keyword? op) (maybe-var-get op)
                                                    (symbol? op) (var-get (resolve op))
                                                    :else op)]
                                            (apply f nparams))
                       (keyword? line) (maybe-var-get line)
                       :else line))))) ;; leave untouched otherwise

;; lifting

(defn lift
  "Create context aware version of the given `op` function. `:metamorph/data` will be used as a first parameter.

  Result of the `op` function will be stored under `:metamorph/data`"
  [op & params]
  (if (satisfies? prot/MetamorphProto op)
    (prot/lift op params)
     (fn [ctx]
       (assoc ctx :metamorph/data (apply op (:metamorph/data ctx) params)))))


(defn do-ctx
  "Apply f:: ctx -> any, ignore the result, leaving
   pipeline unaffected.  Akin to using doseq for side-effecting
   operations like printing, visualization, or binding to vars
   for debugging."
  [f]
  (fn [ctx] (f ctx) ctx))

(defmacro def-ctx
  "Convenience macro for defining pipelined operations that
   bind the current value of the context to a var, for simple
   debugging purposes."
  [varname]
  `(do-ctx (fn [ctx#] (def ~varname ctx#))))

(defn pipe-it
  "Given a pipeline specification ops  - a sequence of operations that
   would be compatible with metamorph/pipeline, and an initial
   dataset, applies the pipeline and returns the context."
  ([data ops config]
   ((->pipeline ops) (merge config {:metamorph/data data})))
  ([data ops]
   (pipe-it data ops {:metamorph/mode :fit}))
  )

(comment
  (pipe-it
   "hello"
   [(lift clojure.string/upper-case)]
   ;; {:metamorph/mode :fit}
   )
  (pipe-it
   "hello"
   [(lift clojure.string/upper-case)]
   {:metamorph/mode :test}
   )

  )
