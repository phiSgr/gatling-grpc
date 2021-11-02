package com.github.phisgr.example.util

import java.net.{InetSocketAddress, URI}
import io.grpc.{Attributes, EquivalentAddressGroup, NameResolver, NameResolverProvider}

import scala.jdk.CollectionConverters._

object ClientSideLoadBalancingResolverProvider extends NameResolverProvider {
  override def getDefaultScheme: String = "my-resolver"
  override def newNameResolver(targetUri: URI, args: NameResolver.Args): NameResolver = ClientSideLoadBalancingResolver
  override def isAvailable: Boolean = true
  override def priority(): Int = 0
}

object ClientSideLoadBalancingResolver extends NameResolver {
  private val addresses = ports.map { port =>
    new EquivalentAddressGroup(new InetSocketAddress("localhost", port))
  }.asJava

  override def getServiceAuthority: String = "dummy"
  override def shutdown(): Unit = ()

  override def start(listener: NameResolver.Listener): Unit = {
    listener.onAddresses(addresses, Attributes.EMPTY)
  }
}
