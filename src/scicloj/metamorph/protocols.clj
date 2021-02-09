(ns scicloj.metamorph.protocols)

(defprotocol MetamorphProto
  (lift [obj args] "Create pipeline operator."))
