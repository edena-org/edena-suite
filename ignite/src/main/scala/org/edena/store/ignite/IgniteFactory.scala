package org.edena.store.ignite

import javax.inject.{Inject, Provider, Singleton}
import org.apache.ignite.binary.BinaryTypeConfiguration
import org.apache.ignite.configuration.{BinaryConfiguration, ClientConnectorConfiguration, IgniteConfiguration}
import org.apache.ignite.marshaller.jdk.JdkMarshaller
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder
import org.apache.ignite.{Ignite, Ignition}

import java.util
import scala.jdk.CollectionConverters._

/**
 * Ignite node factory - local node only, no clustering
 *
 * @param lifecycleBean
 * @param discoverySpi
 * @param ipFinder
 */
@Singleton
class IgniteFactory @Inject() (
  lifecycleBean: IgniteLifecycleBean,
  discoverySpi: TcpDiscoverySpi,
  ipFinder: TcpDiscoveryVmIpFinder
//    serializer: BSONObjectIDBinarySerializer,
) extends Provider[Ignite] {

  override def get(): Ignite = {
    println("Starting Ignite node (pure Ignite)...")

    val binaryTypeCfg = new BinaryTypeConfiguration()

    val binaryCfg = new BinaryConfiguration()
    binaryCfg.setTypeConfigurations(Seq(binaryTypeCfg).asJava)
    binaryCfg.setCompactFooter(false)

    val disco = new TcpDiscoverySpi()
      .setLocalAddress("127.0.0.1")
      .setLocalPort(47500)
      .setLocalPortRange(0)
      .setIpFinder(new TcpDiscoveryVmIpFinder().setAddresses(java.util.Arrays.asList("127.0.0.1:47500")))

    val comm = new TcpCommunicationSpi()
      .setLocalAddress("127.0.0.1")
      .setLocalPort(47100)
      .setLocalPortRange(0)

    val cc = new ClientConnectorConfiguration()
    cc.setHost("127.0.0.1"); cc.setPort(10800); cc.setPortRange(0)

    val cfg = new IgniteConfiguration()
    cfg.setBinaryConfiguration(binaryCfg)
    // cfg.setMarshaller(new JdkMarshaller())  // Disable marshaller to use default binary
    cfg.setLifecycleBeans(lifecycleBean)
    cfg.setClassLoader(Thread.currentThread().getContextClassLoader())
    cfg.setIgniteInstanceName("solo")
    cfg.setLocalHost("127.0.0.1")
    cfg.setDiscoverySpi(disco)
    cfg.setCommunicationSpi(comm)
    cfg.setClientConnectorConfiguration(cc)

//    ipFinder.setAddresses(Seq("128.0.0.6").asJava)
//    discoverySpi.setIpFinder(ipFinder)
//    cfg.setDiscoverySpi(discoverySpi)

    Ignition.getOrStart(cfg)
  }
}