package examples

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.BooleanType
import org.edena.spark_ml.SparkUtil._
import org.edena.spark_ml.models.{TreeCore, VectorScalerType}
import org.edena.spark_ml.models.classification._
import org.edena.spark_ml.models.result.ClassificationResultsHolder
import org.edena.spark_ml.models.setting.{ClassificationLearningSetting, TemporalClassificationLearningSetting}
import org.edena.spark_ml.transformers.BooleanLabelIndexer
import org.edena.spark_ml.{MLResultUtil, SparkMLApp, SparkMLService}

import scala.concurrent.ExecutionContext.Implicits.global

// Warning: Might take > 30 mins or so to run, adjust the number of param values to reduce the time
object TemporalClassificationWithSlidingWindow extends SparkMLApp((session: SparkSession, mlService: SparkMLService) => {

  object Column extends Enumeration {
    val index,AF3,F7,F3,FC5,T7,P7,O1,O2,P8,T8,FC6,F4,F8,AF4,eyeDetection,eyeDetectionBool = Value
  }

  val columnNames = Column.values.toSeq.sortBy(_.id).map(_.toString)
  val outputColumnName = Column.eyeDetectionBool.toString
  val orderColumnName = Column.index.toString
  val featureColumnNames = columnNames.filter(name => name != outputColumnName && name != orderColumnName)

  // read a csv and create a data frame with given column names
  val url = "https://bit.ly/2yhOJ5y" // EEG Eye State
  val df = remoteCsvToDataFrame(url, true)(session)

  val df2 = df.withColumn(outputColumnName, df(Column.eyeDetection.toString).cast(BooleanType))

  // turn the data frame into ML-ready one with features and a label
  val df3 = prepFeaturesDataFrame(featureColumnNames.toSet, Some(outputColumnName))(df2)
  val finalDf = BooleanLabelIndexer(Some(outputColumnName)).transform(df3)

  // logistic regression spec
  val logisticRegressionSpec = LogisticRegression(
    family = Some(LogisticModelFamily.Binomial),
    regularization = Right(Seq(10, 1, 0.1, 0.01, 0.001)),
    elasticMixingRatio = Right(Seq(0, 0.5, 1))
  )

  // random forest spec
  val randomForestSpec = RandomForest(
    core = TreeCore(maxDepth = Right(Seq(5,6,7)))
  )

  // linear SVM spec
  val linearSupportVectorMachineSpec = LinearSupportVectorMachine(
    regularization = Right(Seq(10, 1, 0.1, 0.01, 0.001))
  )

  // learning setting
  val classificationLearningSetting = ClassificationLearningSetting(
    trainingTestSplitRatio = Some(0.75),
    featuresNormalizationType = Some(VectorScalerType.StandardScaler),
    crossValidationEvalMetric = Some(ClassificationEvalMetric.areaUnderROC),
    crossValidationFolds = Some(5)
  )

  val temporalLearningSetting = TemporalClassificationLearningSetting(
    core = classificationLearningSetting,
    predictAhead = 100, // roughly 0.78 sec ahead
    slidingWindowSize = Right(Seq(4,5,6))
  )

  // aux function to get a mean training and test accuracy and AUROC
  def calcMeanAccuracyAndAUROC(results: ClassificationResultsHolder) = {
    val metricStatsMap = MLResultUtil.calcMetricStats(results.performanceResults)
    val (trainingAccuracy, Some(testAccuracy), _) = metricStatsMap.get(ClassificationEvalMetric.accuracy).get
    val (trainingAUROC, Some(testAUROC), _) = metricStatsMap.get(ClassificationEvalMetric.areaUnderROC).get

    ((trainingAccuracy.mean, testAccuracy.mean), (trainingAUROC.mean, testAUROC.mean))
  }

  for {
    // run the logistic regression and get results
    lrResults <- mlService.classifyTimeSeries(finalDf, logisticRegressionSpec, temporalLearningSetting)

    // run the random forest and get results
    rfResults <- mlService.classifyTimeSeries(finalDf, randomForestSpec, temporalLearningSetting)

    // run the linear svm and get results
    lsvmResults <- mlService.classifyTimeSeries(finalDf, linearSupportVectorMachineSpec, temporalLearningSetting)
  } yield {
    val ((lrTrainingAccuracy, lrTestAccuracy), (lrTrainingAUROC, lrTestAUROC)) = calcMeanAccuracyAndAUROC(lrResults)
    val ((rfTrainingAccuracy, rfTestAccuracy), (rfTrainingAUROC, rfTestAUROC)) = calcMeanAccuracyAndAUROC(rfResults)
    val ((lsvmTrainingAccuracy, lsvmTestAccuracy), (lsvmTrainingAUROC, lsvmTestAUROC)) = calcMeanAccuracyAndAUROC(lsvmResults)


    println(s"Logistic Regression Accuracy: $lrTrainingAccuracy / $lrTestAccuracy")
    println(s"Logistic Regression    AUROC: $lrTrainingAUROC / $lrTestAUROC")
    println(s"Random Forest       Accuracy: $rfTrainingAccuracy / $rfTestAccuracy")
    println(s"Random Forest          AUROC: $rfTrainingAUROC / $rfTestAUROC")
    println(s"Linear SVM          Accuracy: $lsvmTrainingAccuracy / $lsvmTestAccuracy")
    println(s"Linear SVM             AUROC: $lsvmTrainingAUROC / $lsvmTestAUROC")
  }
})