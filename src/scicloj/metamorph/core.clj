(ns scicloj.metamorph.core)

(defn pipeline
  [& ops]
  (let [ops-with-id (map-indexed vector ops)] ;; add consecutive number to each operation
    (fn [ctx]
      (let [ctx (if-not (map? ctx)
                  {:metamorph/data ctx} ctx)] ;; if context is not a map, pack it to the map
        (reduce (fn [curr-ctx [id op]] ;; go through operations
                  (if (keyword? op) ;; bare keyword means a mode!
                    (assoc curr-ctx :metamorph/mode op) ;; set current mode
                    (-> curr-ctx 
                        (assoc :metamorph/id id) ;; assoc id of the operation
                        (op) ;; call it
                        (dissoc :metamorph/id)))) ;; dissoc id
                ctx ops-with-id)))))

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
                     (if (sequential? line) ;; if it's a sequence, resolve function, process parameters and call it.
                       (let [[op & params] line
                             nparams (process-param config params)
                             f (cond
                                 (keyword? op) (maybe-var-get op)
                                 (symbol? op) (var-get (resolve op))
                                 :else op)]
                         (apply f nparams))
                       line))))) ;; leave untouched otherwise
