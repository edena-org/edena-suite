package org.edena.store.mongo

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigValue}
import org.edena.core.util.ConfigImplicits._
import org.edena.core.util.seqFutures
import org.slf4j.LoggerFactory
import reactivemongo.api.bson.collection.BSONSerializationPack

import scala.util.Failure
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import reactivemongo.api.{AsyncDriver, DB, MongoConnection}
import reactivemongo.api.gridfs.GridFS

import scala.collection.convert.ImplicitConversions.`iterator asScala`

/**
 * Default implementation of ReactiveMongoApi.
 */
final class DefaultReactiveMongoApi(
  parsedUri: MongoConnection.ParsedURIWithDB,
  strictMode: Boolean,
  configuration: Config)(
  implicit actorSystem: ActorSystem
) extends ReactiveMongoApi {

  override implicit val ec = actorSystem.dispatcher

  private val logger = LoggerFactory getLogger getClass.getName

  lazy val driver = new AsyncDriver(Some(configuration), None)

  val connection: Future[MongoConnection] =
    driver.connect(parsedUri, name = None, strictMode).map { con =>
      registerDriverShutdownHook(con, driver)
      con
    }

  def database: Future[DB] = connection.flatMap { con =>
    logger.debug(s"Resolving database '${parsedUri.db}' ... ($parsedUri)")
    con.database(parsedUri.db)
  }

  def asyncGridFS: Future[GridFS[BSONSerializationPack.type]] =
    database.map(db =>
      db.gridfs[BSONSerializationPack.type](BSONSerializationPack, "fs")
    )

  private def registerDriverShutdownHook(connection: MongoConnection, mongoDriver: AsyncDriver): Unit =
    actorSystem.registerOnTermination {
      logger.info("ReactiveMongoApi stopping...")

      val future = connection.close()(10.seconds).map { _ =>
        logger.info("ReactiveMongoApi connections are stopped")
      }.andThen {
        case Failure(reason) =>
          reason.printStackTrace()
          mongoDriver.close() // Close anyway

        case _ => mongoDriver.close()
      }

      Await.ready(future, 12.seconds)
    }
}

object DefaultReactiveMongoApi {
  val DefaultPort = 27017
  val DefaultHost = "localhost:27017"

  private val logger = LoggerFactory getLogger getClass.getName

  final case class BindingInfo(
    strict: Boolean,
    uriWithDB: MongoConnection.ParsedURIWithDB
  )

  // taken from DefaultReactiveMongoApi.parseURI
  // don't like the Await.result here
  private def parseURI(
    uri: String,
    keyForLogging: Option[String] = None)(
    implicit ec: ExecutionContext
  ): Option[MongoConnection.ParsedURIWithDB] = scala.util
    .Try(Await.result(MongoConnection.fromStringWithDB(uri), 10.seconds))
    .recoverWith {
      case cause =>
        logger.warn(s"Invalid connection URI '${keyForLogging.getOrElse("N/A")}': $uri", cause)
        Failure[MongoConnection.ParsedURIWithDB](cause)
    }
    .toOption

  def parseConfiguration(
    configuration: Config)(
    implicit ec: ExecutionContext
  ): Seq[(String, BindingInfo)] =
    if (configuration.hasPath("mongodb")) {
      val subConf = configuration.getConfig("mongodb")

      val primaryConnection: Option[(String, String, String)] =
        subConf.optionalString("uri").map(
          "mongodb.uri" -> _
        ).orElse(
          subConf.optionalString("default.uri").map(
            "mongodb.default.uri" -> _
          )
        ).map { case (key, uri) =>
          ("default", key, uri)
        }

      val otherConnections: Seq[(String, String, String)] = subConf.entrySet.iterator.collect {
        case entry: java.util.Map.Entry[String, ConfigValue] if (entry.getKey.endsWith(".uri") && entry.getValue.unwrapped.isInstanceOf[String]) =>
          (entry.getKey, s"mongodb.${entry.getKey}", entry.getValue.unwrapped.asInstanceOf[String])
      }.toSeq

      val allConnections = Seq(primaryConnection).flatten ++ otherConnections


      val mongoConfigs = allConnections.flatMap { case (name, key, uri) =>
        parseURI(uri, Some(key)).map { uriWithDB =>
          val strictKey = s"${key.dropRight(4)}.connection.strictUri"

          name -> BindingInfo(
            strict = configuration.optionalBoolean(strictKey).getOrElse(false),
            uriWithDB = uriWithDB
          )
        }
      }

      if (mongoConfigs.isEmpty) {
        logger.warn("No configuration in the 'mongodb' section")
      }

      mongoConfigs
    } else {
      logger.warn("No 'mongodb' section found in the configuration")
      Seq.empty
    }
}

