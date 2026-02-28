[![Clojars Project](https://img.shields.io/clojars/v/scicloj/metamorph.svg)](https://clojars.org/scicloj/metamorph)

# metamorph

A Clojure library for building composable data transformation and machine learning pipelines using pure functions.

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Quick Start](#quick-start)
4. [Core Concepts](#core-concepts)
5. [Creating Pipelines](#creating-pipelines)
6. [Lifting Functions](#lifting-functions)
7. [Fit and Transform Modes](#fit-and-transform-modes)
8. [Declarative Pipelines](#declarative-pipelines)
9. [Metamorph Compliant Libraries](#metamorph-compliant-libraries)
10. [Advanced Features](#advanced-features)
11. [Best Practices](#best-practices)
12. [Examples](#examples)
13. [License](#license)

## Introduction

Metamorph enables you to express any data transformation and machine learning pipeline as a simple sequence of pure functions:

```clojure
(def pipe
  (pipeline
   (select-columns [:Text :Score])
   (count-vectorize :Text :bow nlp/default-text->bow {})
   (bow->sparse-array :bow :bow-sparse #(nlp/->vocabulary-top-n % 1000))
   (set-inference-target :Score)
   (ds/select-columns [:bow-sparse :Score])
   (model {:p 1000
           :model-type :maxent-multinomial
           :sparse-column :bow-sparse})))
```

### Why Metamorph?

- **Pure Functions**: All pipeline operations are pure, making them predictable and testable
- **Self-Contained**: Complete pipelines with all state contained in a context map
- **Mode-Aware**: Built-in support for fit/transform patterns common in machine learning
- **Composable**: Easy to combine operations from different libraries
- **Declarative or Functional**: Build pipelines using either function composition or data structures

Several code examples for metamorph are available in the [metamorph-examples](https://github.com/scicloj/metamorph-examples) repository.

## Installation

Add metamorph to your `deps.edn`:

```clojure
{:deps {scicloj/metamorph {:mvn/version "0.2.4"}}}}
```

Or to your `project.clj` (Leiningen):

```clojure
[scicloj/metamorph "0.2.4"]
```

## Quick Start

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

## Core Concepts

### The Context Map

At the heart of metamorph is the **context map**, which flows through your pipeline. It has three reserved keys:

* **`:metamorph/data`** - The main data object being transformed (dataset, tensor, string, etc.)
* **`:metamorph/id`** - A unique identifier for each operation, automatically injected before the operation is called. This allows operations to store and retrieve private data in the context.
* **`:metamorph/mode`** - Optional mode indicator (`:fit`, `:transform`, or custom modes) that can be added explicitly during pipeline creation.

Example context:

```clojure
{:metamorph/data "my data"
 :metamorph/mode :fit
 ;; You can add any other keys for your use case
 :my-custom-key "some value"}
```

#### Understanding Modes

Different pipeline functions can work together if they agree on a common set of modes and act accordingly. The main use case for this are pipelines which include statistical models. We define two standard modes:

* **`:fit`** - While the pipeline has this mode, a model-containing function should fit its model from the data (also called "train"). It should write the fitted model to the key in `:metamorph/id` so that on the next pipeline run in mode `:transform` it can be used.
* **`:transform`** - While the pipeline is in this mode, the fitted model should be read from the key in `:metamorph/id` and applied to the data.

In machine learning terminology, these 2 modes are typically called train and predict. In metamorph we use the fit/transform terms as the generalization.

Functions which only manipulate the data should simply behave the same in any mode, ignoring `:metamorph/mode`.

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

1. **Return a function** that takes the context map as first parameter
2. **Return the complete context map** (potentially modified)
3. **Not remove any existing keys** from the context
4. **Use namespaced keys** to avoid conflicts
5. **Only interact with the context map** (no external state or side effects)
6. **Be a pure function**

The object under `:metamorph/data` is considered to be the main data object, which nearly all functions will interact with. A function which only interacts with this main data object needs nevertheless to return the whole context map with the data at key `:metamorph/data`.

Any pipeline function should **only** interact with the context map. It should neither read nor write anything outside the context. This is important, as it makes the whole pipeline completely self-contained, and it can be re-executed anywhere, for example on new data.

#### Typical Function Skeleton

```clojure
(defn my-data-transform-function [any number of options]
  (fn [{:metamorph/keys [id data mode] :as ctx}]
    ;; do something with data and potentially with id and mode
    ;; and write it back somewhere in the ctx, often to key `:metamorph/data`, but could be any key
    ;; the assoc makes sure that other data in ctx is left unchanged
    (assoc ctx :metamorph/data ......)
      ))
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

Functions in [tablecloth](https://github.com/scicloj/tablecloth) take a dataset as input in first position, and return a dataset. This means they can be used with `metamorph.core/lift` to be converted (lifted) into a metamorph compliant function. (Tablecloth has lifted versions of its functions in namespace `tablecloth.pipeline`)

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

### Complete Fit/Transform Example

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

Pipelines can be constructed from functions or as pure data. Metamorph pipelines can be either constructed from a sequence of function calls via the function `metamorph.core/pipeline` or declaratively as a sequence of maps.

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

This should allow advanced use cases, like the **generation** of pipelines, which gives large flexibility for hyper parameter tuning in machine learning.

See here for examples: https://github.com/scicloj/tablecloth/blob/pipelines/src/tablecloth/pipeline.clj

## Metamorph Compliant Libraries

The following libraries provide metamorph compliant functions in a recent version:

|library          |   purpose              |  link                                             |
|-----------------|------------------------|-------------------------------------------------- |
|tablecloth       |  dataset manipulation  |  https://github.com/scicloj/tablecloth            |
|tech.ml.dataset  |  dataset manipulation  |  https://github.com/techascent/tech.ml.dataset    |
|metamorph.ml     |  machine learning    |  https://github.com/scicloj/metamorph.ml |
|sklearn-clj      |  sklearn estimators as metamorph functions |  https://github.com/scicloj/sklearn-clj |
|scicloj.ml.smile | ML algorithms from [Smile](https://haifengl.github.io/)   |  https://github.com/scicloj/scicloj.ml.smile|
|scicloj.ml.tribuo | ML algorithms from [Tribuo](https://tribuo.org/)   |  https://github.com/scicloj/scicloj.ml.tribuo|

Other libraries which do "data transformations" can decide to make their functions metamorph compliant. This does not require any dependency on metamorph, just the usage of the standard keys.

Functions can easily be lifted to become metamorph compliant using the function `metamorph/lift`.

### Integration with Other Libraries

#### tablecloth (Dataset Manipulation)

```clojure
(require '[tablecloth.pipeline :as tc])

(def data-pipeline
  (morph/pipeline
   (tc/select-columns [:age :income])
   (tc/drop-missing)
   (tc/add-column :age-squared #(tech.v3.datatype.functional/pow (:age %) 2))))

(morph/fit-pipe
  (tablecloth.api/dataset {:age [18] :income [1000]})
  data-pipeline)
```

#### metamorph.ml (Machine Learning)

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

### Related Projects

#### metamorph.ml
A sister project [metamorph.ml](https://github.com/scicloj/metamorph.ml) allows to evaluate machine learning pipelines based on metamorph.


### Comparison with sklearn Pipelines
The `metamorph` concept is similar to the `pipeline` concept of sklearn, which allows running a given pipeline in `fit` and `transform`. But metamorph allows combining models with arbitrary transform functions, which don't need to be models.

## Advanced Features

### Two Types of Functions in Pipeline

We foresee that mainly 2 types of functions get added to a pipeline:

1. **Mode independent functions**: They only manipulate the main data object and ignore all other information in contexts. Neither will they use `:metamorph/mode` nor the `:metamorph/id` in the context map.
2. **Mode dependent functions**: These functions behave differently depending on the `:mode` and will likely store data in the context map, which can be used by the same function in another mode or by other functions later in the pipeline.

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

### Using `pipe-it`

For simple data transformations without modes:

```clojure
(morph/pipe-it
  "hello"
  (morph/lift str/upper-case)
  (morph/lift #(str % " WORLD")))
;; => "HELLO WORLD"
```

### Advantages of the Metamorph Concept

* A complete (machine learning) pipeline becomes self-contained. All information (data and "state" of models) is inside the pipeline context
* All steps of the pipeline are pure functions, whose outcome depends only on its context map parameter (containing the data) and eventual options
* It unifies the data processing pipeline idea of `tablecloth` with the concept of fitted models and machine learning
* It uses only pure Clojure data structures
* It has no dependency to any concrete data manipulation library, but all can be integrated easily based on a small number of agreed map keys

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

### Example 3: Simple Lifting Example

In this short example, the main data object in the context is a simple string:

```clojure
(require '[scicloj.metamorph.core :as morph])

;; a regular function which takes and returns a main object
(defn regular-function-to-be-lifted
  [main-object par1 par2]
  (str "Hey, " (clojure.string/upper-case main-object) " , I'm regular function! (pars: " par1 ", " par2 ")"))

;; we make a pipeline-fn using `lift` and the regular function
(def lifted-pipeline
  (morph/pipeline
   :anymode
   (morph/lift regular-function-to-be-lifted 1 2)))

;; lifted-pipeline is a regular Clojure function, taking the context in first place
(lifted-pipeline {:metamorph/data "main data project"})
;;->
;; {:metamorph/data "Hey, MAIN DATA PROJECT , I'm regular function! (pars: 1, 2)"}
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

## Additional Resources

- [Metamorph Examples Repository](https://github.com/scicloj/metamorph-examples)
- [Scicloj Community](https://scicloj.github.io/)
- [Clojure Data Science](https://scicloj.github.io/docs/)

## License

Copyright © 2021 Scicloj

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
