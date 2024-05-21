package org.edena.ada.server.services.ml

import com.typesafe.config.Config
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.edena.core.util.ConfigImplicits._

import javax.inject.Inject
import scala.collection.JavaConversions._

trait SparkApp {
  def session: SparkSession

  def sc = session.sparkContext

  def sqlContext = session.sqlContext
}

private[services] class SparkAppImpl @Inject() (
  configuration: Config
) extends SparkApp {

  private val reservedKeys = Set("spark.master.url", "spark.driver.jars")

  private val settings = configuration.entrySet().map(_.getKey).filter(key =>
    key.startsWith("spark.") && !reservedKeys.contains(key)
  ).flatMap { key =>
    println(key)
    configuration.optionalString(key).map((key, _))
  }

  private val jars = configuration.optionalStringSeq("spark.driver.jars").getOrElse(Nil)

  private lazy val conf = new SparkConf(false)
    .setMaster(configuration.optionalString("spark.master.url").getOrElse("local[*]"))
    .setAppName("Ada")
    .set("spark.logConf", "true")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.worker.cleanup.enabled", "true")
    .set("spark.worker.cleanup.interval", "900")
    .setJars(jars)
    .setAll(settings)
    .registerKryoClasses(Array(
      classOf[scala.collection.mutable.ArraySeq[_]]
    ))

  override lazy val session = SparkSession
    .builder()
    .config(conf)
    .getOrCreate()
}