package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.grpc.stream.StreamCall._
import io.gatling.core.session.Expression

trait StatusSupport {
  implicit def server(e: Expression[ServerStreamState]): ServerStatusSupport = new ServerStatusSupport(e)

  implicit def bidi(e: Expression[BidiStreamState]): BidiStatusSupport = new BidiStatusSupport(e)

  implicit def client(e: Expression[ClientStreamState]): ClientStatusSupport = new ClientStatusSupport(e)
}

class ServerStatusSupport(private val e: Expression[ServerStreamState]) extends AnyVal {
  def isCompleted: Expression[Boolean] = e.map(_.isInstanceOf[Completed])
  def isOpen: Expression[Boolean] = e.map(_ == Receiving)
}
class BidiStatusSupport(private val e: Expression[BidiStreamState]) extends AnyVal {
  def isCompleted: Expression[Boolean] = e.map(_.isInstanceOf[Completed])
  def isHalfClosed: Expression[Boolean] = e.map(_ == Receiving)
  def isBothOpen: Expression[Boolean] = e.map(_ == BothOpen)
}
class ClientStatusSupport(private val e: Expression[ClientStreamState]) extends AnyVal {
  def isCompleted: Expression[Boolean] = e.map(_.isInstanceOf[Completed])
  def isOpen: Expression[Boolean] = e.map(_ == BothOpen)
}
