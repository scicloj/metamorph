# Metamorph Tutorial

A comprehensive guide to building data transformation and machine learning pipelines in Clojure with metamorph.

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Core Concepts](#core-concepts)
4. [Getting Started](#getting-started)
5. [Creating Pipelines](#creating-pipelines)
6. [Lifting Functions](#lifting-functions)
7. [Fit and Transform Modes](#fit-and-transform-modes)
8. [Declarative Pipelines](#declarative-pipelines)
9. [Advanced Features](#advanced-features)
10. [Integration with Other Libraries](#integration-with-other-libraries)
11. [Best Practices](#best-practices)
12. [Examples](#examples)

## Introduction

Metamorph is a Clojure library that enables you to build composable data transformation and machine learning pipelines using pure functions. It provides a simple yet powerful abstraction that unifies data processing with machine learning workflows.

### Why Metamorph?

- **Pure Functions**: All pipeline operations are pure, making them predictable and testable
- **Self-Contained**: Complete pipelines with all state contained in a context map
- **Mode-Aware**: Built-in support for fit/transform patterns common in machine learning
- **Composable**: Easy to combine operations from different libraries
- **Declarative or Functional**: Build pipelines using either function composition or data structures

## Installation

Add metamorph to your `deps.edn`:

```clojure
{:deps {scicloj/metamorph {:mvn/version "0.8.0"}}}
```

Or to your `project.clj` (Leiningen):

```clojure
[scicloj/metamorph "0.8.0"]
```

## Core Concepts

### The Context Map

At the heart of metamorph is the **context map**, which flows through your pipeline. It has three reserved keys:

- **`:metamorph/data`** - The main data object being transformed (dataset, tensor, string, etc.)
- **`:metamorph/id`** - A unique identifier for each operation, automatically injected
- **`:metamorph/mode`** - Optional mode indicator (`:fit`, `:transform`, or custom modes)

```clojure
{:metamorph/data "my data"
 :metamorph/mode :fit
 ;; You can add any other keys for your use case
 :my-custom-key "some value"}
```

### Pipeline Operations

A **pipeline operation** is a function that:
1. Takes a context map as input
2. Returns a (possibly modified) context map
3. Should not have side effects outside the context
4. Should be a pure function

```clojure
(defn my-operation [ctx]
  (let [data (:metamorph/data ctx)]
    (assoc ctx :metamorph/data (transform-data data))))
```

### Metamorph Compliant Functions

For a function to work well in metamorph pipelines, it should follow these conventions:

1. Return a function that takes the context map as first parameter
2. Return the complete context map (potentially modified)
3. Not remove any existing keys from the context
4. Use namespaced keys to avoid conflicts
5. Only interact with the context map (no external state)

## Getting Started

Let's start with a simple example:

```clojure
(require '[scicloj.metamorph.core :as morph])

;; A simple function that transforms data
(defn uppercase-data []
  (fn [ctx]
    (update ctx :metamorph/data clojure.string/upper-case)))

;; Create a pipeline
(def my-pipeline
  (morph/pipeline
   (uppercase-data)))

;; Execute the pipeline
(my-pipeline {:metamorph/data "hello world"})
;; => {:metamorph/data "HELLO WORLD"}
```

## Creating Pipelines

### Using `pipeline`

The `pipeline` function creates a pipeline from a sequence of operations:

```clojure
(require '[scicloj.metamorph.core :as morph])

(defn add-prefix [prefix]
  (fn [ctx]
    (update ctx :metamorph/data #(str prefix %))))

(defn add-suffix [suffix]
  (fn [ctx]
    (update ctx :metamorph/data #(str % suffix))))

(def text-pipeline
  (morph/pipeline
   (add-prefix ">>> ")
   (add-suffix " <<<")))

(text-pipeline {:metamorph/data "important"})
;; => {:metamorph/data ">>> important <<<"}
```

### Injecting Context Values

You can inject values directly into the context by including maps:

```clojure
(def pipeline-with-mode
  (morph/pipeline
   {:metamorph/mode :training}  ; Set the mode
   (add-prefix ">>> ")
   {:custom/timestamp (System/currentTimeMillis)}  ; Add custom data
   (add-suffix " <<<")))
```

## Lifting Functions

**Lifting** converts regular functions into metamorph-compliant functions. Use `lift` for functions that take the data object as their first argument:

```clojure
(require '[clojure.string :as str])

;; Regular function: string -> string
(defn my-uppercase [s]
  (str/upper-case s))

;; Lift it to work with metamorph
(def lifted-pipeline
  (morph/pipeline
   (morph/lift my-uppercase)))

(lifted-pipeline {:metamorph/data "hello"})
;; => {:metamorph/data "HELLO"}
```

### Lifting with Parameters

You can lift functions with additional parameters:

```clojure
(defn repeat-string [s n]
  (apply str (repeat n s)))

(def repeat-pipeline
  (morph/pipeline
   (morph/lift repeat-string 3)))

(repeat-pipeline {:metamorph/data "abc"})
;; => {:metamorph/data "abcabcabc"}
```

## Fit and Transform Modes

One of metamorph's most powerful features is mode-aware operations, particularly for machine learning workflows.

### The Pattern

- **`:fit` mode** - Learn from the data (training phase)
- **`:transform` mode** - Apply learned transformations (prediction phase)

### Mode-Aware Operations

```clojure
(defn normalize-using-stats []
  (fn [{:metamorph/keys [id data mode] :as ctx}]
    (case mode
      :fit
      ;; In fit mode: calculate statistics and store them
      (let [stats {:mean (calculate-mean data)
                   :std (calculate-std data)}]
        (-> ctx
            (assoc id stats)  ; Store stats using operation id
            (assoc :metamorph/data (normalize data stats))))

      :transform
      ;; In transform mode: use previously stored statistics
      (let [stats (get ctx id)]
        (assoc ctx :metamorph/data (normalize data stats)))

      ;; For any other mode or no mode, just pass through
      ctx)))
```

### Helper Functions

Metamorph provides helper functions for common fit/transform patterns:

```clojure
;; fit: Execute pipeline in :fit mode
(def fitted-ctx
  (morph/fit "hello world" my-pipeline))

;; transform-pipe: Execute pipeline in :transform mode with fitted context
(def transformed-ctx
  (morph/transform-pipe "new data" my-pipeline fitted-ctx))

;; fit-transform-pipe: Fit and transform in one go
(def result-ctx
  (morph/fit-transform-pipe "hello world" my-pipeline))
```

### Complete Example

```clojure
(defn create-vocab []
  (fn [{:metamorph/keys [id data mode] :as ctx}]
    (case mode
      :fit
      (let [vocab (set (clojure.string/split data #"\s+"))]
        (-> ctx
            (assoc id {:vocabulary vocab})
            (assoc :metamorph/data data)))

      :transform
      (let [vocab (get-in ctx [id :vocabulary])
            words (clojure.string/split data #"\s+")
            known-words (filter vocab words)]
        (assoc ctx :metamorph/data (clojure.string/join " " known-words)))

      ctx)))

(def vocab-pipeline
  (morph/pipeline
   (create-vocab)))

;; Fit on training data
(def fitted
  (morph/fit-pipe "hello world from clojure" vocab-pipeline))

;; Transform new data using learned vocabulary
(def result
  (morph/transform-pipe "hello from python" vocab-pipeline fitted))

(:metamorph/data result)
;; => "hello from"  (only words from training vocabulary)
```

## Declarative Pipelines

You can create pipelines from data structures using `->pipeline`:

```clojure
(defn my-transform [data param]
  (str data " " param))

(def declarative-pipeline
  (morph/->pipeline
   [[:morph/lift my-transform "world"]
    [:morph/lift str/upper-case]]))

(declarative-pipeline {:metamorph/data "hello"})
;; => {:metamorph/data "HELLO WORLD"}
```

### With Configuration

Pass a configuration map to inject values:

```clojure
(def pipeline-config
  {:threshold 0.5
   :max-iterations 100})

(def configured-pipeline
  (morph/->pipeline
   pipeline-config
   [[:my-function :ctx/threshold :ctx/max-iterations]]))

;; :ctx/threshold will resolve to 0.5
;; :ctx/max-iterations will resolve to 100
```

### Namespace Resolution

Use namespaced keywords to reference functions:

```clojure
(require '[clojure.string :as str])

(def declarative-pipeline
  (morph/->pipeline
   [[:morph/lift str/upper-case]     ; Resolves to clojure.string/upper-case
    [:morph/lift str/trim]]))         ; Resolves to clojure.string/trim
```

## Advanced Features

### Debugging with `do-ctx`

Use `do-ctx` to inspect the context without modifying it:

```clojure
(def debug-pipeline
  (morph/pipeline
   (add-prefix ">>> ")
   (morph/do-ctx #(println "Context:" %))  ; Side effect for debugging
   (add-suffix " <<<")))
```

### Binding Context with `def-ctx`

For interactive development, bind the current context to a var:

```clojure
(def debug-pipeline
  (morph/pipeline
   (add-prefix ">>> ")
   (morph/def-ctx debug-ctx)  ; Binds context to #'debug-ctx
   (add-suffix " <<<")))

;; After execution, inspect debug-ctx
```

### Custom Operation IDs

You can override the automatic ID assignment:

```clojure
(def pipeline-with-custom-id
  (morph/pipeline
   {:metamorph/id :my-custom-id}
   (my-stateful-operation)))
```

### Using `pipe-it`

For simple data transformations without modes:

```clojure

(morph/pipe-it
  "hello"
  (morph/lift str/upper-case)
  (morph/lift #(str % " WORLD")))
;; => "HELLO WORLD"
```

## Integration with Other Libraries

Metamorph is designed to work seamlessly with various Clojure data science libraries:

### tablecloth (Dataset Manipulation)

```clojure
(require '[tablecloth.pipeline :as tc])

(def data-pipeline
  (morph/pipeline
   (tc/select-columns [:age :income])
   (tc/drop-missing)
   (tc/add-column :age-squared #(tech.v3.datatype.functional/pow (:age %) 2))))

(morph/fit-pipe 
  (tablecloth.api/dataset {:age [18] :income [1000]})
  data-pipeline 
)   
```

### metamorph.ml (Machine Learning)

```clojure
(require '[scicloj.metamorph.ml :as ml]
          '[tech.v3.dataset.metamorph :as ds-mm])


(def ml-pipeline
  (morph/pipeline
   (tc/select-columns [:feature1 :feature2 :target])
   (ds-mm/set-inference-target :target)
   (ml/model {:model-type :decision-tree})))

;; Train the model
(def trained (morph/fit-pipe training-data ml-pipeline))

;; Make predictions
(def predictions (morph/transform-pipe test-data ml-pipeline trained))
```

### scicloj.ml.smile (Smile ML Algorithms)

```clojure
(require '[scicloj.ml.smile.classification])

(def smile-pipeline
  (morph/pipeline
   (tc/select-columns [:features :label])
   (ml/model {:model-type :smile.classification/random-forest
              :ntrees 100})))
```

## Best Practices

### 1. Keep Operations Pure

```clojure
;; Good: Pure function
(defn scale-data [factor]
  (fn [ctx]
    (update ctx :metamorph/data #(* % factor))))

;; Bad: Side effects
(defn log-and-scale [factor]
  (fn [ctx]
    (println "Scaling...") ; Side effect!
    (update ctx :metamorph/data #(* % factor))))
```

### 2. Use Namespaced Keys

```clojure
;; Good: Namespaced keys
(defn my-operation []
  (fn [ctx]
    (assoc ctx :mylib/intermediate-result some-value)))

;; Avoid: Generic keys that might conflict
(defn my-operation []
  (fn [ctx]
    (assoc ctx :result some-value))) ; Could conflict!
```

### 3. Document Mode Behavior

```clojure
(defn my-stateful-op
  "Learns statistics in :fit mode, applies them in :transform mode.
   Stores statistics under the operation's :metamorph/id."
  []
  (fn [{:metamorph/keys [id data mode] :as ctx}]
    ;; implementation...
    ))
```

### 4. Prefer Lifting Simple Functions

```clojure
;; Simple data transformation? Use lift!
(morph/lift clojure.string/trim)

;; Complex logic with state? Write a full metamorph operation
(defn complex-operation []
  (fn [ctx] ...))
```

### 5. Test Operations in Isolation

```clojure
(deftest test-my-operation
  (let [input-ctx {:metamorph/data "test"}
        output-ctx ((my-operation) input-ctx)]
    (is (= expected-data (:metamorph/data output-ctx)))))
```

## Examples

### Example 1: Text Processing Pipeline

```clojure
(require '[clojure.string :as str])

(defn remove-punctuation []
  (fn [ctx]
    (update ctx :metamorph/data
            #(str/replace % #"[^\w\s]" ""))))

(defn count-words []
  (fn [ctx]
    (let [text (:metamorph/data ctx)
          word-count (count (str/split text #"\s+"))]
      (-> ctx
          (assoc :metamorph/data text)
          (assoc :text/word-count word-count)))))

(def text-pipeline
  (morph/pipeline
   (morph/lift str/lower-case)
   (morph/lift str/trim)
   (remove-punctuation)
   (count-words)))

(text-pipeline {:metamorph/data "  Hello, World! How are you?  "})
;; => {:metamorph/data "hello world how are you"
;;     :text/word-count 5}
```

### Example 2: Feature Scaling with Fit/Transform

```clojure
(defn min-max-scaler []
  (fn [{:metamorph/keys [id data mode] :as ctx}]
    (case mode
      :fit
      (let [min-val (apply min data)
            max-val (apply max data)
            scaled (map #(/ (- % min-val) (- max-val min-val)) data)]
        (-> ctx
            (assoc id {:min-val min-val :max-val max-val})
            (assoc :metamorph/data scaled)))

      :transform
      
      
      (let [{:keys [min-val max-val]} (get ctx id)
            scaled (map #(/ (- % min-val) (- max-val min-val)) data)]
        (assoc ctx :metamorph/data scaled)))))

(def scaler-pipeline
  (morph/pipeline
   (min-max-scaler)))

;; Fit on training data
(def fitted
  (morph/fit-pipe [1 2 3 4 5] scaler-pipeline))

(:metamorph/data fitted)
;; => (0 0.25 0.5 0.75 1.0)

;; Transform new data using learned min/max
(def transformed
  (morph/transform-pipe [0 3 6] scaler-pipeline fitted))


(:metamorph/data transformed)
;; => (-0.25 0.5 1.25)
```

### Example 3: Declarative Pipeline Configuration

```clojure
(defn filter-by-threshold [data threshold]
  (filter #(> % threshold) data))

(defn multiply-by [data factor]
  (map #(* % factor) data))

(def pipeline-spec
  [[:morph/lift filter-by-threshold :ctx/threshold]
   [:morph/lift multiply-by :ctx/factor]])

(def configured-pipeline
  (morph/->pipeline
   {:threshold 5
    :factor 10}
   pipeline-spec))

(configured-pipeline {:metamorph/data [1 3 5 7 9]})
;; => {:metamorph/data (70 90)}
```

### Example 4: Multi-Stage Pipeline

```clojure
(defn extract-features []
  (fn [ctx]
    (let [text (:metamorph/data ctx)
          features {:length (count text)
                   :word-count (count (str/split text #"\s+"))
                   :has-uppercase? (boolean (re-find #"[A-Z]" text))}]
      (assoc ctx
             :metamorph/data features
             :original/text text))))

(defn feature-to-vector []
  (fn [ctx]
    (let [features (:metamorph/data ctx)]
      (assoc ctx :metamorph/data
             [(:length features)
              (:word-count features)
              (if (:has-uppercase? features) 1 0)]))))

(def feature-pipeline
  (morph/pipeline
   (extract-features)
   (feature-to-vector)))

(feature-pipeline {:metamorph/data "Hello World"})
;; => {:metamorph/data [11 2 1]
;;     :original/text "Hello World"}
```

## Conclusion

Metamorph provides a clean, functional approach to building data transformation and machine learning pipelines in Clojure. Its simple yet powerful abstraction enables you to:

- Compose complex workflows from simple operations
- Maintain pipeline state in a single, immutable data structure
- Separate fitting and transformation logic naturally
- Integrate multiple libraries seamlessly
- Test and reason about your pipelines easily

For more examples and advanced usage, check out:
- [metamorph-examples repository](https://github.com/scicloj/metamorph-examples)
- [metamorph.ml](https://github.com/scicloj/metamorph.ml) for machine learning pipelines
- [scicloj.ml](https://github.com/scicloj/scicloj.ml) for complete ML solutions

## Additional Resources

- [Metamorph GitHub Repository](https://github.com/scicloj/metamorph)
- [Scicloj Community](https://scicloj.github.io/)
- [Clojure Data Science](https://scicloj.github.io/docs/)

Happy pipelining!
