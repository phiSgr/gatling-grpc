package com.github.phisgr.gatling.grpc.protocol

import java.io.{ByteArrayInputStream, InputStream}
import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.util.Throwables._
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.{Session, SessionPrivateAttributes}
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.stub.ClientCalls
import io.grpc.{CallOptions, ManagedChannel, ManagedChannelBuilder, MethodDescriptor}

import scala.util.control.NonFatal

object GrpcProtocol extends StrictLogging {
  private val DefaultChannelAttributeName: String = SessionPrivateAttributes.PrivateAttributePrefix + "grpc.channel"

  type WarmUp = (MethodDescriptor[T, _], T) forSome {type T}

  private[gatling] def defaultWarmUp: WarmUp = {
    val emptyMarshaller = new Marshaller[Unit] {
      override def stream(value: Unit): InputStream = new ByteArrayInputStream(Array())
      override def parse(stream: InputStream): Unit = {}
    }
    val method = MethodDescriptor.newBuilder()
      .setFullMethodName("grpc.health.v1.Health/Check")
      .setType(MethodDescriptor.MethodType.UNARY)
      .setRequestMarshaller(emptyMarshaller)
      .setResponseMarshaller(emptyMarshaller)
      .build()
    (method, ())
  }

  private[this] var warmedUp = false

  class GrpcComponent private(
    channelBuilder: ManagedChannelBuilder[_],
    shareChannel: Boolean,
    channelAttributeName: String
  ) extends ProtocolComponents {
    private val channel = if (shareChannel) channelBuilder.build() else null

    def this(
      channelBuilder: ManagedChannelBuilder[_],
      shareChannel: Boolean,
      id: Option[String],
      warmUp: Option[WarmUp]
    ) {
      this(channelBuilder, shareChannel, id.fold(DefaultChannelAttributeName)(DefaultChannelAttributeName + "." + _))
      warmUp.filter(_ => !warmedUp).foreach { case (method, req) =>
        logger.info(s"Making warm up call with method ${method.getFullMethodName}")
        var tempChannel: ManagedChannel = null
        try {
          val warmUpChannel = if (shareChannel) channel else {
            tempChannel = channelBuilder.build()
            tempChannel
          }
          ClientCalls.blockingUnaryCall(warmUpChannel.newCall(method, CallOptions.DEFAULT), req)
          warmedUp = true
          logger.debug(s"Warm up request successful")
        } catch {
          case NonFatal(e) =>
            if (logger.underlying.isDebugEnabled)
              logger.debug(s"Couldn't execute warm up request", e)
            else
              logger.info(s"Couldn't execute warm up request: ${e.rootMessage}")
        } finally {
          if (tempChannel ne null) tempChannel.shutdownNow()
        }
      }
    }

    private[gatling] def getChannel(session: Session): ManagedChannel = {
      if (shareChannel) channel else session(channelAttributeName).as[ManagedChannel]
    }

    override val onStart: Session => Session = if (shareChannel) identity else { session =>
      session.set(channelAttributeName, channelBuilder.build())
    }

    override val onExit = { s =>
      s(channelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
    }
  }

  val GrpcProtocolKey = new ProtocolKey[GrpcProtocol, GrpcComponent] {
    override def protocolClass: Class[Protocol] = classOf[GrpcProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration): GrpcProtocol =
      throw new UnsupportedOperationException()

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { protocol =>
      protocol.createComponents(id = None)
    }
  }
}

import GrpcProtocol._

case class GrpcProtocol(
  private val channelBuilder: ManagedChannelBuilder[_],
  private val _shareChannel: Boolean = false,
  private val warmUp: Option[WarmUp] = Some(GrpcProtocol.defaultWarmUp)
) extends Protocol {
  def shareChannel: GrpcProtocol = copy(_shareChannel = true)

  def disableWarmUp: GrpcProtocol = copy(warmUp = None)

  def warmUpCall[T](method: MethodDescriptor[T, _], req: T): GrpcProtocol =
    copy(warmUp = Some((method, req)))

  private def createComponents(id: Option[String]): GrpcComponent = {
    new GrpcComponent(channelBuilder, _shareChannel, id, warmUp)
  }

  private[gatling] lazy val overridingKey = new ProtocolKey[GrpcProtocol, GrpcComponent] with StrictLogging {
    override def protocolClass: Class[Protocol] = GrpcProtocolKey.protocolClass

    override def defaultProtocolValue(configuration: GatlingConfiguration): GrpcProtocol =
      GrpcProtocolKey.defaultProtocolValue(configuration)

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { _ =>
      val id = UUID.randomUUID().toString
      logger.info(s"Creating a new non-default GrpcComponent with ID $id.")
      createComponents(id = Some(id))
    }
  }

}
