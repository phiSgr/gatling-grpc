package com.github.phisgr.example.util

import java.net.{InetSocketAddress, URI}

import io.grpc.{Attributes, EquivalentAddressGroup, NameResolver}

import scala.jdk.CollectionConverters._

object ClientSideLoadBalancingResolverFactory extends NameResolver.Factory {
  override def getDefaultScheme: String = "whatever"
  override def newNameResolver(targetUri: URI, args: NameResolver.Args): NameResolver = ClientSideLoadBalancingResolver
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
