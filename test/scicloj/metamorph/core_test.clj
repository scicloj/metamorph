(ns scicloj.metamorph.core-test
  (:require [scicloj.metamorph.core :as sut]
            [clojure.test :as t]))

(defn context-operator
  [ctx]
  (let [id (:metamorph/id ctx)        
        mode (:metamorph/mode ctx)
        my-data (-> (ctx id)
                    (update :modes conj mode))]
    (assoc ctx id my-data)))

(defn operator-creator
  [par1 par2]
  (fn [ctx]
    (let [id (:metamorph/id ctx)]
      (-> (assoc ctx id (+ par1 par2))
          (update :metamorph/data conj [par1 par2])))))

(def local-value1 123.1)
(def local-value2 432.1)

(def pipeline-declaration
  [:context-operator ;; existing symbol, will be resolved
   [:operator-creator 3 4] ;; explicit data
   [:operator-creator ::local-value1 ::local-value2] ;; access local variables
   :new-mode ;; this is changed mode
   :context-operator
   [:operator-creator :ctx/a :ctx/b]]) ;; optional parameters

(def dpipeline-1 (sut/->pipeline {:a -1 :b -2} pipeline-declaration))
(def dpipeline-2 (sut/->pipeline {:a 100 :b 1000} pipeline-declaration))

(defn make-pipeline
  [a b]
  (sut/pipeline
   context-operator
   (operator-creator 3 4)
   (operator-creator local-value1 local-value2)
   :new-mode
   context-operator
   (operator-creator a b)))

(def pipeline-1 (make-pipeline -1 -2))
(def pipeline-2 (make-pipeline 100 1000))

(def res11 {:metamorph/data [[3 4] [123.1 432.1] [-1 -2]]
            0 {:modes '(nil)}
            1 7
            2 555.2
            4 {:modes '(:new-mode)}
            5 -3})

(def res12 {:metamorph/data [[3 4] [123.1 432.1] [-1 -2] [3 4] [123.1 432.1] [-1 -2]]
            0 {:modes '(:some-mode nil)}
            1 7
            2 555.2
            4 {:modes '(:new-mode :new-mode)}
            5 -3})

(def res21 {:metamorph/data [[3 4] [123.1 432.1] [100 1000]]
            0 {:modes '(nil)}
            1 7
            2 555.2
            4 {:modes '(:new-mode)}
            5 1100})

(def res22 {:metamorph/data [[3 4] [123.1 432.1] [100 1000] [3 4] [123.1 432.1] [100 1000]]
            0 {:modes '(:some-mode nil)}
            1 7
            2 555.2
            4 {:modes '(:new-mode :new-mode)}
            5 1100})

(t/deftest whole-process
  (t/is (= (pipeline-1 []) res11))
  (t/is (= (pipeline-1 (assoc (pipeline-1 []) :metamorph/mode :some-mode)) res12))
  (t/is (= (pipeline-2 []) res21))
  (t/is (= (pipeline-2 (assoc (pipeline-2 []) :metamorph/mode :some-mode)) res22))
  (t/is (= (dpipeline-1 []) res11))
  (t/is (= (dpipeline-1 (assoc (dpipeline-1 []) :metamorph/mode :some-mode)) res12))
  (t/is (= (dpipeline-2 []) res21))
  (t/is (= (dpipeline-2 (assoc (dpipeline-2 []) :metamorph/mode :some-mode)) res22)))

;; lifting

(defn regular-function-to-be-lifted
  [_main-object par1 par2]
  (str "Hey, I'm regular function! (pars: " par1 ", " par2 ")"))

(def lifted-pipeline
  (sut/pipeline
   :anymode
   (sut/lift regular-function-to-be-lifted 1 2)))

(def declarative-lifted-pipeline
  (sut/->pipeline
   [:anymode
    [:sut/lift ::regular-function-to-be-lifted 1 2]]))

(def expected-result
  {:metamorph/data "Hey, I'm regular function! (pars: 1, 2)"})

(t/deftest lift-function
  (t/is (= ((sut/lift regular-function-to-be-lifted 1 2) {}) expected-result))
  (t/is (= (lifted-pipeline) expected-result))
  (t/is (= (declarative-lifted-pipeline) expected-result)))
