(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]))

(def lib 'scicloj/metamorph)
; alternatively, use MAJOR.MINOR.COMMITS:
;; (def version (format "7.0.%s" (b/git-count-revs nil)))
(def version (format "0.2.4"))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))




(defn test "Run the tests." [opts]
  (-> opts
      (assoc :lib lib :version version
             :aliases [:run-tests])
      (bb/run-tests)))


  

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-pom "template/pom.xml"
                :scm {:connection "scm:git:https://github.com/scicloj/metamorph.git"
                      :url "https://github.com/scicloj/metamorph"}
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version
             :aliases [:run-tests])
             
      (bb/run-tests)
      (bb/clean)
      (jar)))


(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
