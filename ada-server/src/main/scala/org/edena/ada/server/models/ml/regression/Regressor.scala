package org.edena.ada.server.models.ml.regression

import org.edena.store.json.BSONObjectIdentity
import org.edena.json.EitherFormat
import org.edena.json._
import play.api.libs.json.{Format, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.spark_ml.models.regression._
import org.edena.spark_ml.models.TreeCore

object Regressor {

  implicit val regressionSolverEnumTypeFormat = EnumFormat(RegressionSolver)
  implicit val generalizedLinearRegressionFamilyEnumTypeFormat = EnumFormat(GeneralizedLinearRegressionFamily)
  implicit val generalizedLinearRegressionLinkTypeEnumTypeFormat = EnumFormat(GeneralizedLinearRegressionLinkType)
  implicit val generalizedLinearRegressionSolverEnumTypeFormat = EnumFormat(GeneralizedLinearRegressionSolver)
  implicit val featureSubsetStrategyEnumTypeFormat = EnumFormat(RandomRegressionForestFeatureSubsetStrategy)
  implicit val regressionTreeImpurityEnumTypeFormat = EnumFormat(RegressionTreeImpurity)
  implicit val gbtRegressionLossTypeEnumTypeFormat = EnumFormat(GBTRegressionLossType)

  def eitherFormat[T: Format] = {
    implicit val optionFormat = new OptionFormat[T]
    EitherFormat[Option[T], Seq[T]]
  }

  implicit val doubleEitherFormat = eitherFormat[Double]
  implicit val intEitherFormat = eitherFormat[Int]

  private implicit val treeCoreFormat = Json.format[TreeCore]

  implicit val regressorFormat: Format[Regressor] = new SubTypeFormat[Regressor](
    Seq(
      RuntimeClassFormat(Json.format[LinearRegression]),
      RuntimeClassFormat(Json.format[GeneralizedLinearRegression]),
      RuntimeClassFormat(Json.format[RegressionTree]),
      RuntimeClassFormat(Json.format[RandomRegressionForest]),
      RuntimeClassFormat(Json.format[GradientBoostRegressionTree])
    )
  )

  implicit object RegressorIdentity extends BSONObjectIdentity[Regressor] {
    def of(entity: Regressor): Option[BSONObjectID] = entity._id

    protected def set(entity: Regressor, id: Option[BSONObjectID]) =
      entity match {
        case x: LinearRegression => x.copy(_id = id)
        case x: GeneralizedLinearRegression => x.copy(_id = id)
        case x: RegressionTree => x.copy(_id = id)
        case x: RandomRegressionForest => x.copy(_id = id)
        case x: GradientBoostRegressionTree => x.copy(_id = id)
      }
  }
}