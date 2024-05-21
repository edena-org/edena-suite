package org.edena.dl4j

import java.util
import java.util.Collections

import org.datavec.api.records.reader.{RecordReader, SequenceRecordReader}
import org.datavec.api.records.reader.impl.csv.{CSVRecordReader, CSVSequenceRecordReader}

object CsvFeaturesAndLabelReader {

  def apply(
    featureBaseDir: String,
    labelBaseDir: String,
    startIndex: Int,
    endIndex: Int,
    featuresSkipLines: Int
  ): (SequenceRecordReader, RecordReader) = {
    // create and shuffle indeces
    val indeces = new util.ArrayList[Integer]
    for (i <- startIndex to endIndex) indeces.add(i)
    Collections.shuffle(indeces)

    apply(
      featureBaseDir,
      labelBaseDir,
      indeces,
      featuresSkipLines
    )
  }

  def apply(
    featureBaseDir: String,
    labelBaseDir: String,
    indeces: java.util.List[Integer],
    featuresSkipLines: Int
  ): (SequenceRecordReader, RecordReader) = {
    // create a features reader
    val features = new CSVSequenceRecordReader(featuresSkipLines, ",")
    val featuresSplit = new IndexInputSplit(featureBaseDir + "/%d.csv", indeces)
    features.initialize(featuresSplit)

    // create a labels reader
    val labels = new CSVRecordReader
    val labelsSplit = new IndexInputSplit(labelBaseDir + "/%d.csv", indeces)
    labels.initialize(labelsSplit)

    (features, labels)
  }
}
