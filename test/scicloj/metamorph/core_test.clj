(ns scicloj.metamorph.core-test
  (:require [scicloj.metamorph.core :as sut]
            [clojure.test :as t]
            [scicloj.metamorph.protocols :as prot]))
            


(defn gen-rand
  [s]
  (let [x (atom s)]
    #(let [n (first @x)]
       (swap! x rest)
       n)))



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
   {:metamorph/mode :new-mode} ;; this is changed mode
   :context-operator
   [:operator-creator :ctx/a :ctx/b]]) ;; optional parameters

(with-redefs
  [scicloj.metamorph.core/uuid
   (gen-rand (range 10))])
   

  

(def dpipeline-1
  (with-redefs
    [scicloj.metamorph.core/uuid
     (gen-rand (range 10))]
    (sut/->pipeline {:a -1 :b -2} pipeline-declaration)))

(def dpipeline-2
 (with-redefs
   [scicloj.metamorph.core/uuid
    (gen-rand (range 10))]
   (sut/->pipeline {:a 100 :b 1000} pipeline-declaration)))

(defn make-pipeline
  [a b]
  (sut/pipeline
   context-operator
   (operator-creator 3 4)
   (operator-creator local-value1 local-value2)
   {:metamorph/mode :new-mode}
   context-operator
   (operator-creator a b)))

(def pipeline-1
  (with-redefs
    [scicloj.metamorph.core/uuid
     (gen-rand (range 10))]
    (make-pipeline -1 -2)))

(def pipeline-2
  (with-redefs
    [scicloj.metamorph.core/uuid
     (gen-rand (range 10))]
    (make-pipeline 100 1000)))

;; (pipeline-1 [])
;; => {:metamorph/data [[3 4] [123.1 432.1] [-1 -2]],
;;     0 {:modes (nil)},
;;     1 7,
;;     2 555.2,
;;     :metamorph/mode :new-mode,
;;     4 {:modes (:new-mode)},
;;     5 -3}

(def res11 {:metamorph/data [[3 4] [123.1 432.1] [-1 -2]]
            0 {:modes '(nil)}
            1 7
            2 555.2 :metamorph/mode :new-mode
            4 {:modes '(:new-mode)}
            5 -3})

(def res12 {:metamorph/data [[3 4] [123.1 432.1] [-1 -2] [3 4] [123.1 432.1] [-1 -2]]
            0 {:modes '(:some-mode nil)}
            1 7
            2 555.2 :metamorph/mode :new-mode
            4 {:modes '(:new-mode :new-mode)}
            5 -3})

(def res21 {:metamorph/data [[3 4] [123.1 432.1] [100 1000]]
            0 {:modes '(nil)}
            1 7
            2 555.2 :metamorph/mode :new-mode
            4 {:modes '(:new-mode)}
            5 1100})

(def res22 {:metamorph/data [[3 4] [123.1 432.1] [100 1000] [3 4] [123.1 432.1] [100 1000]]
            0 {:modes '(:some-mode nil)}
            1 7
            2 555.2 :metamorph/mode :new-mode
            4 {:modes '(:new-mode :new-mode)}
            5 1100})


(def pipeline-3
  (with-redefs
    [scicloj.metamorph.core/uuid
     (gen-rand (range 10))]
   (sut/pipeline
     {:metamorph/id :test-id}
     (operator-creator 1 2))))



(t/deftest ovewrite-id
  (t/is (= 3
           (:test-id (pipeline-3 [])))))

(def dpipeline-3
  (with-redefs
    [scicloj.metamorph.core/uuid
     (gen-rand (range 10))]
    (sut/->pipeline
     [
      {:metamorph/id :test-id}
      [:operator-creator 1 2]])))


(t/deftest ovewrite-id-d
  (t/is (= 3
           (:test-id (dpipeline-3 [])))))



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

(def object-that-can-be-lifted
  (reify prot/MetamorphProto
    (lift [_ args]
      (apply sut/lift regular-function-to-be-lifted args))))

(def lifted-pipeline
  (sut/pipeline
   :anymode
   (sut/lift regular-function-to-be-lifted 1 2)))

(def declarative-lifted-pipeline
  (sut/->pipeline
   [:anymode
    [:sut/lift ::regular-function-to-be-lifted 1 2]]))

(def object-pipeline
  (sut/pipeline
   (sut/lift object-that-can-be-lifted 1 2)))

(def declarative-object-pipeline
  (sut/->pipeline
   [:anymode
    [:sut/lift ::object-that-can-be-lifted 1 2]]))


(def expected-result
  {:metamorph/data "Hey, I'm regular function! (pars: 1, 2)"})

(t/deftest lift-function
  (t/is (= ((sut/lift regular-function-to-be-lifted 1 2) {:metamorph/data nil}) expected-result))
  (t/is (= (lifted-pipeline) expected-result))
  (t/is (= (declarative-lifted-pipeline) expected-result))
  (t/is (= (object-pipeline) expected-result))
  (t/is (= (declarative-object-pipeline) expected-result)))


(t/deftest fit-transform

  (let [pipe-fn
        (sut/pipeline
         (sut/lift clojure.string/upper-case))

        fitted
        (sut/fit
         "hello"
         pipe-fn)

        transformed
        (sut/transform-pipe "world" pipe-fn fitted)]
    (t/is (= "HELLO" (:metamorph/data fitted)))
    (t/is (= :fit (:metamorph/mode fitted)))

    (t/is (= "WORLD" (:metamorph/data transformed)))
    (t/is (= :transform (:metamorph/mode transformed)))))


(comment
  (defn do-on-string [s f]
    (apply f s))

  (def pip
    (sut/->pipeline
     [
      [sut/lift ::do-on-string 'clojure.string/upper-case]]))


  (pip {:metamorph/data  "a"}))
