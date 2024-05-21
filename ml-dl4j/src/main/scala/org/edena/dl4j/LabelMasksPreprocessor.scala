package org.edena.dl4j

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.api.{DataSet, DataSetPreProcessor}

class LabelMasksPreprocessor(labelMaskArray: INDArray) extends DataSetPreProcessor {

  override def preProcess(toPreProcess: DataSet) = {
    toPreProcess.setLabelsMaskArray(labelMaskArray)
  }
}
