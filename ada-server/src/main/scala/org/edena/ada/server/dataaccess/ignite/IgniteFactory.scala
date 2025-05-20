package org.edena.ada.server.dataaccess.ignite

import javax.inject.{Inject, Provider, Singleton}
import org.apache.ignite.binary.BinaryTypeConfiguration
import org.apache.ignite.configuration.{BinaryConfiguration, IgniteConfiguration}
import org.apache.ignite.lifecycle.LifecycleBean
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder
import org.apache.ignite.{Ignite, Ignition}
import reactivemongo.api.bson.BSONObjectID

import scala.jdk.CollectionConverters._

@Singleton
class IgniteFactory @Inject() (
  serializer: BSONObjectIDBinarySerializer,
  lifecycleBean: LifecycleBean,
  discoverySpi: TcpDiscoverySpi,
  ipFinder: TcpDiscoveryVmIpFinder
) extends Provider[Ignite] {

  override def get(): Ignite = {
    val binaryTypeCfg1 = new BinaryTypeConfiguration()
    binaryTypeCfg1.setTypeName(classOf[BSONObjectID].getName)
    binaryTypeCfg1.setSerializer(serializer)

    val binaryTypeCfg2 = new BinaryTypeConfiguration()
    binaryTypeCfg2.setTypeName(classOf[BSONObjectID].getName + "$$anon$4")
    binaryTypeCfg2.setSerializer(serializer)

    val binaryCfg = new BinaryConfiguration()
    binaryCfg.setTypeConfigurations(Seq(binaryTypeCfg1, binaryTypeCfg2).asJava)

    val cfg = new IgniteConfiguration()
    cfg.setBinaryConfiguration(binaryCfg)
    cfg.setLifecycleBeans(lifecycleBean)
    cfg.setClassLoader(Thread.currentThread().getContextClassLoader())

    ipFinder.setAddresses(Seq("127.0.0.1").asJava)
    discoverySpi.setIpFinder(ipFinder)
    cfg.setDiscoverySpi(discoverySpi)
    // cfg.setWorkDirectory("/tmp/ignite-work")

    Ignition.getOrStart(cfg)
  }
}