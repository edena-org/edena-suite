package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.InputFutureRunnableExt
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveUnreferencedFilters @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnableExt[RemoveUnreferencedFiltersSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(
    input: RemoveUnreferencedFiltersSpec
  ) =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

      // get all the views for a given data set
      views <- dsa.dataViewStore.find()

      // get all the classification results for a given data set
      classificationResults <- dsa.classificationResultStore.find()

      // get all the regression results for a given data set
      regressionResults <- dsa.regressionResultStore.find()

      // get all the filters for a given data set
      allFilters <- dsa.filterStore.find()

      // remove unreferenced filters
      _ <- {
        val refFilterIds1 = views.flatMap(_.filterOrIds.collect{ case Right(filterId) => filterId }).toSet
        val refFilterIds2 = classificationResults.flatMap(result => Seq(result.filterId, result.ioSpec.replicationFilterId).flatten).toSet
        val refFilterIds3 = regressionResults.flatMap(result => Seq(result.filterId, result.ioSpec.replicationFilterId).flatten).toSet

        val refFilterIds = refFilterIds1 ++ refFilterIds2 ++ refFilterIds3

        val allFilterIds = allFilters.flatMap(_._id).toSet

        val unreferencedFilterIds = allFilterIds.filterNot(refFilterIds.contains(_))

        logger.info(s"Removing ${unreferencedFilterIds.size} unreferenced filters for the data set ${input.dataSetId}.")

        dsa.filterStore.delete(unreferencedFilterIds)
      }
    } yield
      ()
}

case class RemoveUnreferencedFiltersSpec(
  dataSetId: String
)
