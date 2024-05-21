package org.edena.store.elastic.util

import akka.stream.Materializer
import org.edena.core.util.{GroupMapList, seqFutures}
import org.slf4j.LoggerFactory
import play.api.libs.json._

import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

trait ElasticUtilService {

  def showIndecesWithVersions: Future[Unit]

  def showTempIndecesSortedBySize: Future[Unit]

  def reindexAll(
    version: String,
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean
  ): Future[Unit]

  def reindexAllFromTemp(
    version: String,
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean
  ): Future[Unit]

  def reindexAllFromTempForMissingOnly(
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean,
  ): Future[Unit]

  def compareCountsAndRemoveNonmatchingIndeces: Future[Unit]

  def removeAllTempIndeces(
    tempIndecesToExclude: Seq[String] = Nil
  ): Future[Unit]

  def removeReadonlyFlagForAll: Future[Unit]
}

class ElasticUtilServiceDefaultImpl @Inject()(
  val elasticService: ElasticBaseWSService)(
  implicit materializer: Materializer, @Named("BlockingExecutionContext") val ec: ExecutionContext
) extends ElasticUtilServiceImpl

trait ElasticUtilServiceImpl extends ElasticUtilService {

  protected implicit val ec: ExecutionContext

  protected val elasticService: ElasticBaseWSService

  protected val logger = LoggerFactory getLogger getClass.getName

  override def showIndecesWithVersions = {
    for {
      allIndeces <- elasticService.getAllIndeces

      indexNameInfos = allIndeces.map(indexInfo => (indexInfo(2), indexInfo))

      indexVersions <- seqFutures(indexNameInfos) { case (indexName, indexInfo) =>
        for {
          settings <- elasticService.getSettings(indexName)

          version = getVersion(indexName, settings)

          count = indexInfo(6).toInt
        } yield
          (indexName, version, indexInfo, count)
      }
    } yield
      indexVersions.sortBy(-_._4).foreach { case (indexName, version, indexInfo, count) =>
        logger.info(s"count: ${count}: $indexName - version: ${version}, info: ${indexInfo.mkString(",")}")
      }
  }

  override def showTempIndecesSortedBySize = {
    for {
      allIndeces <- elasticService.getAllIndeces

      tempIndeces = allIndeces.map(indexInfo => (indexInfo(2), indexInfo(6).toInt)).filter(_._1.endsWith("-tempxxx")).sortBy(-_._2).map(_._1)

      tempIndecesWithNormalIndexExistFlags <- seqFutures(tempIndeces) { tempIndex =>
        val indexName = tempIndex.dropRight("-tempxxx".size)

        elasticService.getSettings(indexName).map { settings =>
          val exists = (settings \ "error").toOption.isEmpty
          val prefix = if (exists) "    " else "BAD "
          s"$prefix$indexName"
        }
      }
    } yield
      logger.info(tempIndecesWithNormalIndexExistFlags.mkString(",\n"))
  }

  override def reindexAll(
    version: String,
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean
  ) = {
    for {
      allIndeces <- elasticService.getAllIndeces

      indexNames = allIndeces.map(_(2))

      indexVersions <- seqFutures(indexNames) { indexName =>
        elasticService.getSettings(indexName).map { settings =>
          (getVersion( indexName, settings), indexName)
        }
      }

      indecesToReindex = indexVersions.toGroupMap.get(version).getOrElse(Nil).toSeq.sorted

      _ <- seqFutures(indecesToReindex) { indexName =>
        val tempIndex = s"$indexName-tempxxx"
        for {
          indexExists <- elasticService.getSettings(tempIndex)

          _ <- if ((indexExists \ "error").toOption.isDefined)
            reindex(indexName, tempIndex, adaptMappingFlag, addSingleTypeMappingFlag, useIncludeInAll)
          else {
            logger.warn(s"$tempIndex already exists. Skipping.\n")
            Future(())
          }
        } yield
          ()
      }
    } yield
      ()
  }

  override def reindexAllFromTemp(
    version: String,
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean
  ) = {
    for {
      allIndeces <- elasticService.getAllIndeces

      indexNames = allIndeces.map(_(2))

      indexVersions <- seqFutures(indexNames) { indexName =>
        elasticService.getSettings(indexName).map { settings =>
          (getVersion( indexName, settings), indexName)
        }
      }

      indecesToReindex = indexVersions.toGroupMap.get(version).getOrElse(Nil).toSeq.sorted

      _ <- seqFutures(indecesToReindex) { indexName =>
        val tempIndex = s"$indexName-tempxxx"

        for {
          tempIndexExists <- elasticService.getSettings(tempIndex)

          _ <- if ((tempIndexExists \ "error").toOption.isEmpty) {
            for {
              deleteResponse <- elasticService.deleteIndex(indexName)

              _ = logger.info(deleteResponse)

              _ <- reindex(tempIndex, indexName, adaptMappingFlag, addSingleTypeMappingFlag, useIncludeInAll)
            } yield
              ()
          } else {
            println(s"$tempIndex does not exist. Skipping.\n")
            Future(())
          }
        } yield
          ()
      }
    } yield
      ()
  }

  override def reindexAllFromTempForMissingOnly(
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean,
  ) = {
    for {
      allIndeces <- elasticService.getAllIndeces

      tempIndexNames = allIndeces.map(_(2)).filter(_.endsWith("-tempxxx")).sorted

      _ <- seqFutures(tempIndexNames) { tempIndex =>
        val indexName = tempIndex.dropRight("-tempxxx".size)

        for {
          indexExists <- elasticService.getSettings(indexName)

          _ <- if ((indexExists \ "error").toOption.isDefined) {
            logger.info(s"$indexName doesn't exist. Will reindex from temp.")
            reindex(tempIndex, indexName, adaptMappingFlag, addSingleTypeMappingFlag, useIncludeInAll)
          } else {
            logger.info(s"$indexName already exists. Skipping.\n")
            Future(())
          }
        } yield
          ()
      }
    } yield
      ()
  }

  override def compareCountsAndRemoveNonmatchingIndeces = {
    for {
      allIndeces <- elasticService.getAllIndeces

      tempIndexNames = allIndeces.map(_(2)).filter(_.endsWith("-tempxxx")).sorted

      indexNameWithCounts <- seqFutures(tempIndexNames) { tempIndexName =>
        val indexName = tempIndexName.dropRight("-tempxxx".size)

        for {
          count <- elasticService.getCount(indexName)

          tempCount <- elasticService.getCount(tempIndexName)
        } yield {
          if (!count.equals(tempCount)) {
            Some(indexName, count, tempCount)
          } else
            None
        }
      }

      _ <- seqFutures(indexNameWithCounts.flatten) { case (indexName, count, tempCount) =>
        logger.info(s"Removing $indexName: because its count ${count.getOrElse("N/A")} doesn't equals temp's ${tempCount.getOrElse("N/A")}")
        elasticService.deleteIndex(indexName).map { response =>
          logger.info(response)
        }
      }
    } yield
      ()
  }

  override def removeAllTempIndeces(tempIndecesToExclude: Seq[String]) = {
    for {
      allIndeces <- elasticService.getAllIndeces

      tempIndecesAux = allIndeces.map(_(2)).filter(_.endsWith("-tempxxx"))

      tempIndeces = tempIndecesAux.filterNot(tempIndecesToExclude.contains(_))

      _ = logger.info(tempIndeces.mkString("\n"))

      _ <- seqFutures(tempIndeces) { indexName =>
        elasticService.deleteIndex(indexName).map { response =>
          logger.info(response)
        }
      }
    } yield
      ()
  }

  override def removeReadonlyFlagForAll =
    for {
      allIndeces <- elasticService.getAllIndeces

      indexNames = allIndeces.map(indexInfo => indexInfo(2))

      _ <- seqFutures(indexNames) { indexName =>
        logger.info(indexName)
        elasticService.removeReadOnly(indexName).map { response =>
          logger.info(response)
        }
      }
    } yield
      ()

  private def reindex(
    fromIndexName: String,
    toIndexName: String,
    adaptMappingFlag: Boolean,
    addSingleTypeMappingFlag: Boolean,
    useIncludeInAll: Boolean,
    totalFieldsLimit: Option[Int] = None
  ) = {
    logger.info(s"Processing of  '${fromIndexName}' started")

    for {
      mapping <- elasticService.getMapping(fromIndexName)

      //      _ = println(Json.prettyPrint(mapping))

      newMapping = if (adaptMappingFlag) adaptMapping(mapping, useIncludeInAll) else mapping

      //      _ = println(Json.prettyPrint(adaptMapping(mapping)))

      actualNewMapping = (newMapping \ fromIndexName \ "mappings").as[JsObject]

      response <- elasticService.createIndex(toIndexName, Some(actualNewMapping), totalFieldsLimit, addSingleTypeMappingFlag)

      _ = logger.info(response)

      response2 <- elasticService.reindex(fromIndexName, toIndexName)

      _ = logger.info(response2)
    } yield
      logger.info(s"Processing of '${fromIndexName}' finished.")
  }

  private def getVersion(indexName: String, json: JsObject) = {
    val version = (json \ indexName \ "settings" \ "index" \ "version" \ "created").asOpt[String]

    version.getOrElse(
      throw new RuntimeException("Attribute version not found in: " + Json.prettyPrint(json))
    )
  }

  private def adaptMapping(
    mapping: JsObject,
    useIncludeInAll: Boolean
  ) = {
    val (indexName, mappingsJson) = mapping.fields.head

    val rootMappings = (mappingsJson \ "mappings").as[JsObject]

    val typeMappings = (rootMappings \ indexName).asOpt[JsObject].getOrElse(
      rootMappings.fields.head._2.as[JsObject]
    )

    val propertiesAsJson = (typeMappings \ "properties").as[JsObject]

    val newPropertiesJson = propertiesAsJson.fields.map { case (propertyName, propertyJson) =>
      val propertyType = (propertyJson \ "type").asOpt[String]

      val newType = propertyType.map(
        _ match {
          case "string" => "keyword"
          case "keyword" => "keyword"
          case "float" => "double"
          case "double" => "double"
          case "integer" => "long"
          case "long" => "long"
          case "nested" => "nested"
          case "boolean" => "boolean"
          case "short" => "short"
          case "text" => "text"
          case "date" => "date"
          case _ => throw new RuntimeException(s"The type ${propertyType} unknown.")
        }).getOrElse {
        logger.info(s"Property '${propertyName}' has not type. Using 'nested'")
        "nested"
      }

      def handleAddIncludeInAllFalse(jsonx: JsObject) =
        if (useIncludeInAll)
          jsonx.+("include_in_all"-> JsBoolean(false))
        else
          jsonx

      //      if (propertyName == "__id")
      //        println(Json.prettyPrint(propertyJson))

      val addPart = newType match {
        case "nested" =>
          Json.obj()
        //          Json.obj("enable"-> JsBoolean(false))

        case "double" =>
          handleAddIncludeInAllFalse(
            Json.obj(
              "store"-> JsBoolean(true),
              "coerce"-> JsBoolean(true)
            )
          )

        case "keyword" =>
          handleAddIncludeInAllFalse(
            Json.obj(
              "store"-> JsBoolean(true),
              "ignore_above" -> JsNumber(30000)
            )
          )

        case _ =>
          handleAddIncludeInAllFalse(
            Json.obj(
              "store"-> JsBoolean(true)
            )
          )
      }

      val newPropertyJson = propertyJson.as[JsObject].+("type", JsString(newType)).++(addPart)

      val afterRemovalPropertyJson = newType match {
        case "nested" =>
          newPropertyJson.-("properties")

        case "keyword" =>
          newPropertyJson.-("index")

        case _ =>
          newPropertyJson
      }

      // handle remove IncludeInAll = false
      val afterRemovalPropertyJsonFinal = if (useIncludeInAll) afterRemovalPropertyJson else afterRemovalPropertyJson.-("include_in_all")

      (propertyName, afterRemovalPropertyJsonFinal)
    }

    val newTypeMappings = typeMappings.+("properties", JsObject(newPropertiesJson))

    // handle _all mapping setting
    val containsAllFlag = (typeMappings \ "_all").asOpt[JsObject].isDefined

    val newTypeMappingsFinal = if (!useIncludeInAll && containsAllFlag) {
      logger.info(s"Removing '_all' flag from the top level mapping of the index '$indexName'")
      newTypeMappings.-("_all")
    } else
      newTypeMappings

    Json.obj(indexName ->
      Json.obj("mappings" ->
        Json.obj(indexName -> newTypeMappingsFinal)
      )
    )
  }
}