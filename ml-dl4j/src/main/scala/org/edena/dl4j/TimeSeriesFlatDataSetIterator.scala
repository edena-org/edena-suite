package org.edena.dl4j

import java.util
import java.util.{ArrayList, Collections, List}

import org.datavec.api.records.reader.impl.csv.{CSVRecordReader, CSVSequenceRecordReader}
import org.datavec.api.records.reader.{RecordReader, SequenceRecordReader}
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator.AlignmentMode
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.dataset.api.{DataSetPreProcessor, MultiDataSet}

private class TimeSeriesFlatDataSetIterator(
  batchSize: Int,
  outputSize: Int,
  inputReader: SequenceRecordReader,
  labelReader: RecordReader
) extends DataSetIterator {

  private val mdsIterator: RecordReaderMultiDataSetIterator = new RecordReaderMultiDataSetIterator.Builder(batchSize)
    .sequenceAlignmentMode(AlignmentMode.EQUAL_LENGTH)
    .addSequenceReader("input", inputReader)
    .addReader("output", labelReader)
    .addInput("input")
    .addOutputOneHot("output", 0, outputSize)
    .build()

  private var preProcessor: DataSetPreProcessor = null

  protected var last: DataSet = null
  protected var useCurrent = false

  override def getPreProcessor = preProcessor

  override def setPreProcessor(preProcessor: DataSetPreProcessor) =
    this.preProcessor = preProcessor

  override def getLabels: util.List[String] =
    labelReader.getLabels

  override def inputColumns: Int =
    if (last == null) {
      val nextDS = next()
      last = nextDS
      useCurrent = true
      nextDS.numInputs
    } else
      last.numInputs

  override def totalOutcomes: Int =
    if (last == null) {
      val nextDS = next()
      last = nextDS
      useCurrent = true
      nextDS.numOutcomes
    } else
      last.numOutcomes

  override def batch =
    batchSize

  override def resetSupported =
    mdsIterator.resetSupported()

  override def asyncSupported = true // ??

  override def reset = {
    mdsIterator.reset()
    last = null
    useCurrent = false
  }

  override def hasNext =
    mdsIterator.hasNext

  override def next = next(batchSize)

  override def next(num: Int): DataSet = {
    val ds = if (useCurrent) {useCurrent = false; last} else mdsToDataSet(mdsIterator.next(num))

    if (preProcessor != null) preProcessor.preProcess(ds)
    ds
  }

  private def mdsToDataSet(mds: MultiDataSet): DataSet = {
    var f: INDArray = getOrNull(mds.getFeatures, 0)
    var fm: INDArray = getOrNull(mds.getFeaturesMaskArrays, 0)

    // [minibatchSize, channels, time series length] -> [minibatchSize, channels * time series length]

    val oldShape = f.shape()

    val newF = f.reshape(oldShape(0), oldShape(1) * oldShape(2))

    val l = getOrNull(mds.getLabels, 0)
    val lm = getOrNull(mds.getLabelsMaskArrays, 0)
    new DataSet(newF, l, fm, lm)
  }

  private def getOrNull(arr: Array[INDArray], idx: Int) =
    if (arr == null || arr.length == 0) null else arr(idx)
}

object TimeSeriesFlatDataSetIterator {

  def apply(
    batchSize: Int,
    outputSize: Int,
    inputReader: SequenceRecordReader,
    labelReader: RecordReader
  ): DataSetIterator = new TimeSeriesFlatDataSetIterator(batchSize, outputSize, inputReader, labelReader)

  def apply(
    batchSize: Int,
    outputSize: Int,
    featureBaseDir: String,
    outputBaseDir: String,
    startIndex: Int,
    endIndex: Int,
    featuresSkipLines: Int
  ): DataSetIterator = {
    // create and shuffle indeces
    val indeces = new util.ArrayList[Integer]
    for (i <- startIndex to endIndex) indeces.add(i)
    Collections.shuffle(indeces)

    // create a features reader
    val features = new CSVSequenceRecordReader(featuresSkipLines, ",")
    val featuresSplit = new IndexInputSplit(featureBaseDir + "/%d.csv", indeces)
    features.initialize(featuresSplit)

    // create a labels reader
    val labels = new CSVRecordReader
    val labelsSplit = new IndexInputSplit(outputBaseDir + "/%d.csv", indeces)
    labels.initialize(labelsSplit)

    // combine both readers and retrieve a time series flat data set iterator
    apply(batchSize, outputSize, features, labels)
  }
}