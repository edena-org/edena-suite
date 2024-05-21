package org.edena.dl4j

import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.dataset.api.{DataSet, DataSetPreProcessor}

class CompositePreprocessor(preProcessors: DataSetPreProcessor*) extends DataSetPreProcessor {

  override def preProcess(dataSet: DataSet) =
    preProcessors.foreach( preProcessor =>
      preProcessor.preProcess(dataSet)
    )
}

object CompositePreprocessor {

  implicit class CompositePreprocessorInfix(val dataSetIterator: DataSetIterator) extends AnyVal {
    def setOrAddPreProcessor(dataSetPreProcessor: DataSetPreProcessor) = {
      if (dataSetIterator.getPreProcessor != null) {
        println("Setting a composite pre-processor")
        dataSetIterator.setPreProcessor(new CompositePreprocessor(
          dataSetIterator.getPreProcessor, // existing pre processor
          dataSetPreProcessor
        ))
      } else {
        println("Setting a normal pre-processor")
        dataSetIterator.setPreProcessor(dataSetPreProcessor)
      }
    }
  }
}
