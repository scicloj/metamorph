(ns scicloj.metamorph.core
  (:require [scicloj.metamorph.protocols :as prot]))

(defn ^:deprecated uuid
  "DEPRECATED: Use clojure.core/random-uuid instead"
  []
  (random-uuid))

(defn check-metamorph-compliant
  [ctx op]
  (cond
    (keyword? op) ctx
    (not (map? ctx)) (throw (IllegalArgumentException.  (str  "Metamorph pipe functions need to return a map, but returned: " ctx "of class: " (type ctx))))
    (not (contains? ctx :metamorph/data)) (do (println "Context after operation " op " with meta " (meta op) "does not contain :metamorph/data. This is likely as mistake.") ctx)
    :else ctx))

(defn pipeline
  "Create a metamorph pipeline function out of operators.

  `ops` are metamorph compliant functions (basicaly fn, which takle a ctx as first argument)

  This function returns a function, whcih can ve execute with a ctx as parameter.
  "
  [& ops]
  (let [ops-with-id (mapv #(vector (uuid) %) ops)] ;; add uuid to each operation
    (fn local-pipeline
      ([] (local-pipeline {})) ;; can be called without a context
      ([ctx]
       (let [ctx (if-not (map? ctx) {:metamorph/data ctx} ctx)] ;; if context is not a map, pack it to the map
         (reduce (fn [curr-ctx [id op]]         ;; go through operations
                   (assert (some? op) "op cannot be nil")
                   (if (map? op) ;; map means to be merged with following operation
                     (merge curr-ctx op) ;; set current mode
                     (if (ifn? op)
                       (-> curr-ctx
                           (assoc :metamorph/id (get curr-ctx :metamorph/id id)) ;; assoc id of the operation
                           (op)         ; call it
                           (check-metamorph-compliant op)
                           (dissoc :metamorph/id)) ;; dissoc id
                       (throw (IllegalArgumentException. (str "Cannot call a non function: " op))))))
                 ctx ops-with-id))))))

(declare process-param)

(defn- process-map
  [config params]
  (update-vals params #(process-param config %)))

(defn- process-seq
  [config params]
  (mapv #(process-param config %) params))

(defn- resolve-keyword
  "Interpret keyword as a symbol and try to resolve it."
  [k]
  ;; (println "resolve k: " k "in ns: " *ns*)
  (let [resolved-as
        (-> (if-let [n (namespace k)] ;; namespaced?
              (let [sn (symbol n)
                    n (str (get (ns-aliases *ns*) sn sn))] ;; try to find namespace in aliases
                (symbol n (name k))) ;; create proper symbol with fixed namespace
              (symbol (name k)))     ;; no namespace case
            (resolve))]
    ;; (println "resolved-as: " resolved-as)
    resolved-as))

(defn- maybe-var-get
  "If symbol can be resolved, return var, else return original keyword"
  [k]
  (or (some-> k resolve-keyword var-get)
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

(defn log-and-apply [f args]
  (if (fn? f)
    (apply f args)
    (throw (IllegalArgumentException. (str  "Cannot apply a non-function: "  f "  - args: " args)))))

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
                                            (log-and-apply f nparams))
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
      (assert (contains? ctx :metamorph/data))
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
  "Takes a data objects, executes the pipeline op(s) with it in :metamorph/data
  in mode :fit and returns content of :metamorph/data.
  Usefull to use execute a pipeline of pure data->data functions on some data"
  [data & ops]
  (let [pipe-fn (apply pipeline ops)]
    (:metamorph/data
     (pipe-fn {:metamorph/data data
               :metamorph/mode :fit}))))

(defn fit
  "Helper function which executes pipeline op(s) in mode :fit on the given data and returns the fitted ctx.

  Main use is for cases in which the pipeline gets executed ones and no model is part of the pipeline."
  [data & ops]
  (let [pipe-fn (apply pipeline ops)]
    (pipe-fn {:metamorph/data data
              :metamorph/mode :fit})))

(defn fit-pipe
  "Helper function which executes pipeline op(s) in mode :fit on the given data and returns the fitted ctx.

  Main use is for cases in which the pipeline gets executed ones and no model is part of the pipeline."
  [data pipe-fn]
  (pipe-fn {:metamorph/data data
            :metamorph/mode :fit}))

(defn transform-pipe
  "Helper functions which execute the passed `pipe-fn` on the given `data` in mode :transform.
  It merges the data into the provided `ctx` while doing so."
  [data pipe-fn ctx]

  (pipe-fn
   (merge ctx
          {:metamorph/data data
           :metamorph/mode :transform})))
