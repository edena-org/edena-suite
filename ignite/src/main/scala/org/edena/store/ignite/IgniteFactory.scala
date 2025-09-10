package org.edena.store.ignite

import javax.inject.{Inject, Provider, Singleton}
import org.apache.ignite.binary.BinaryTypeConfiguration
import org.apache.ignite.configuration.{BinaryConfiguration, IgniteConfiguration}
import org.apache.ignite.marshaller.jdk.JdkMarshaller
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder
import org.apache.ignite.{Ignite, Ignition}

import scala.jdk.CollectionConverters._

@Singleton
class IgniteFactory @Inject() (
  lifecycleBean: IgniteLifecycleBean,
  discoverySpi: TcpDiscoverySpi,
  ipFinder: TcpDiscoveryVmIpFinder
//    serializer: BSONObjectIDBinarySerializer,
) extends Provider[Ignite] {

  override def get(): Ignite = {
    val binaryTypeCfg = new BinaryTypeConfiguration()

    // TODO: there should be a way to pass a serializer here...
    // as a result this factory is not used in Ada

//    binaryTypeCfg.setTypeName(classOf[BSONObjectID].getName)
//    binaryTypeCfg.setSerializer(serializer)

    val binaryCfg = new BinaryConfiguration()
    binaryCfg.setTypeConfigurations(Seq(binaryTypeCfg).asJava)
    binaryCfg.setCompactFooter(false)

    val cfg = new IgniteConfiguration()
    cfg.setBinaryConfiguration(binaryCfg)
    // cfg.setMarshaller(new JdkMarshaller())  // Disable marshaller to use default binary
    cfg.setLifecycleBeans(lifecycleBean)
    cfg.setClassLoader(Thread.currentThread().getContextClassLoader())

    ipFinder.setAddresses(Seq("127.0.0.1").asJava)
    discoverySpi.setIpFinder(ipFinder)
    cfg.setDiscoverySpi(discoverySpi)

    Ignition.getOrStart(cfg)
  }
}