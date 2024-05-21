package examples

import org.apache.spark.sql.SparkSession
import org.edena.spark_ml.SparkUtil._
import org.edena.spark_ml.models.TreeCore
import org.edena.spark_ml.models.regression._
import org.edena.spark_ml.models.result.RegressionResultsHolder
import org.edena.spark_ml.models.setting.RegressionLearningSetting
import org.edena.spark_ml.{MLResultUtil, SparkMLApp, SparkMLService}

import scala.concurrent.ExecutionContext.Implicits.global

object SimpleRegression extends SparkMLApp((session: SparkSession, mlService: SparkMLService) => {

  object Column extends Enumeration {
    val Sex, Length, Diameter, Height, WholeWeight, ShuckedWeight, VisceraWeight, ShellWeight, Rings = Value
  }

  val columnNames = Column.values.toSeq.sortBy(_.id).map(_.toString)
  val outputColumnName = Column.Rings.toString
  val featureColumnNames = columnNames.filter(_ != outputColumnName)

  // read a csv and create a data frame with given column names
  val url = "https://archive.ics.uci.edu/ml/machine-learning-databases/abalone/abalone.data"
  val df = remoteCsvToDataFrame(url, false)(session).toDF(columnNames :_*)

  // index the sex column since it's of the string type
  val df2 = indexStringCols(Seq((Column.Sex.toString, Nil)))(df)

  // turn the data frame into ML-ready one with features and a label
  val finalDf = prepFeaturesDataFrame(featureColumnNames.toSet, Some(outputColumnName))(df2)

  // linear regression spec
  val linearRegressionSpec = LinearRegression(
    maxIteration = Left(Some(200)),
    regularization = Left(Some(0.3)),
    elasticMixingRatio = Left(Some(0.8))
  )

  // Gaussian linear regression spec
  val generalizedLinearRegressionSpec = GeneralizedLinearRegression(
    family = Some(GeneralizedLinearRegressionFamily.Gaussian),
    link = Some(GeneralizedLinearRegressionLinkType.Identity),
    maxIteration = Left(Some(200)),
    regularization = Left(Some(0.3))
  )

  // random regression forest spec
  val randomRegressionForestSpec = RandomRegressionForest(
    core = TreeCore(maxDepth = Left(Some(10)))
  )

  // gradient-boost regression tree spec
  val gradientBoostRegressionTreeSpec = GradientBoostRegressionTree(
    maxIteration = Left(Some(50))
  )

  // learning setting
  val learningSetting = RegressionLearningSetting(repetitions = Some(10))

  // aux function to get a mean training and test RMSE
  def calcMeanRMSE(results: RegressionResultsHolder) = {
    val metricStatsMap = MLResultUtil.calcMetricStats(results.performanceResults)
    val (trainingRMSE, Some(testRMSE), _) = metricStatsMap.get(RegressionEvalMetric.rmse).get
    (trainingRMSE.mean, testRMSE.mean)
  }

  for {
    // run the linear regression and get results
    linearRegressionResults <- mlService.regress(finalDf, linearRegressionSpec, learningSetting)

    // run the generalized linear regression and get results
    generalizedLinearRegressionResults <- mlService.regress(finalDf, generalizedLinearRegressionSpec, learningSetting)

    // run the random regression forest and get results
    randomRegressionForestResults <- mlService.regress(finalDf, randomRegressionForestSpec, learningSetting)

    // run the gradient-boost regression and get results
    gradientBoostRegressionTreeResults <- mlService.regress(finalDf, gradientBoostRegressionTreeSpec, learningSetting)
  } yield {
    val (lrTrainingRMSE, lrTestRMSE) = calcMeanRMSE(linearRegressionResults)
    val (glrTrainingRMSE, glrTestRMSE) = calcMeanRMSE(generalizedLinearRegressionResults)
    val (rrfTrainingRMSE, rrfTestRMSE) = calcMeanRMSE(randomRegressionForestResults)
    val (gbrtTrainingRMSE, gbrtTestRMSE) = calcMeanRMSE(gradientBoostRegressionTreeResults)

    println(s"Linear regression (RMSE): $lrTrainingRMSE / $lrTestRMSE")
    println(s"Generalized linear regression (RMSE): $glrTrainingRMSE / $glrTestRMSE")
    println(s"Random regression forest (RMSE): $rrfTrainingRMSE / $rrfTestRMSE")
    println(s"Gradient-boost regression tree (RMSE): $gbrtTrainingRMSE / $gbrtTestRMSE")
  }
})