package com.github.phisgr.gatling.grpc.protocol

import java.util.UUID

import com.github.phisgr.gatling.grpc.action.{DisposeDynamicChannel, SetDynamicChannelBuilder}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.util.Throwables._
import io.gatling.core.CoreComponents
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.{Session, SessionPrivateAttributes}
import io.gatling.core.structure.ScenarioContext
import io.gatling.netty.util.Transports
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.ClientCalls
import io.grpc.{CallOptions, ClientCall, ManagedChannel, ManagedChannelBuilder, MethodDescriptor}

import scala.util.control.NonFatal

object GrpcProtocol extends StrictLogging {
  private[gatling] val DefaultChannelAttributeName: String = SessionPrivateAttributes.PrivateAttributePrefix + "grpc.channel"

  type WarmUp = (MethodDescriptor[T, _], T) forSome {type T}

  private[gatling] def defaultWarmUp: WarmUp = {
    val method = MethodDescriptor.newBuilder()
      .setFullMethodName("grpc.health.v1.Health/Check")
      .setType(MethodDescriptor.MethodType.UNARY)
      .setRequestMarshaller(EmptyMarshaller)
      .setResponseMarshaller(EmptyMarshaller)
      .build()
    (method, ())
  }

  private[this] var warmedUp = false

  class GrpcComponent(
    sharedChannel: ManagedChannel,
    shareChannel: Boolean,
    channelAttributeName: String,
    private[gatling] val lazyParsing: Boolean,
    override val onStart: Session => Session
  ) extends ProtocolComponents {

    def this(
      channelBuilder: ManagedChannelBuilder[_],
      shareChannel: Boolean,
      channelAttributeName: String,
      warmUp: Option[WarmUp],
      lazyParsing: Boolean
    ) = {
      this(
        sharedChannel = if (shareChannel) channelBuilder.build() else null,
        shareChannel = shareChannel,
        channelAttributeName = channelAttributeName,
        lazyParsing = lazyParsing,
        onStart = if (shareChannel) {
          Session.Identity
        } else { session =>
          session.set(channelAttributeName, channelBuilder.build())
        }
      )
      warmUp.filter(_ => !warmedUp).foreach { case (method, req) =>
        logger.info(s"Making warm up call with method ${method.getFullMethodName}")
        var tempChannel: ManagedChannel = null
        try {
          val warmUpChannel = if (shareChannel) sharedChannel else {
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

    private[gatling] def newCall[Req, Res](session: Session, methodDescriptor: MethodDescriptor[Req, Res], callOptions: CallOptions): ClientCall[Req, Res] = {
      val channel = if (shareChannel) sharedChannel else session.attributes.get(channelAttributeName) match {
        case Some(mc: ManagedChannel) => mc
        case _ => throw new IllegalStateException(
          s"ManagedChannel not found in attribute '$channelAttributeName' in session: $session. " +
            "If you are using `dynamicChannel`, you have to `.exec(dynamicProtocol.setChannel)`"
        )
      }
      channel.newCall(methodDescriptor, callOptions)
    }

    override def onExit: Session => Unit = { session =>
      session(channelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
    }
  }

  type Key = ProtocolKey[P, GrpcComponent] forSome {type P <: GrpcProtocol}

  val GrpcProtocolKey: Key = new ProtocolKey[StaticGrpcProtocol, GrpcComponent] {
    override def protocolClass: Class[Protocol] = classOf[StaticGrpcProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration): StaticGrpcProtocol =
      throw new UnsupportedOperationException()

    override def newComponents(coreComponents: CoreComponents): StaticGrpcProtocol => GrpcComponent =
      _.createComponents(id = None, coreComponents)
  }

  def setEventLoopGroup(builder: ManagedChannelBuilder[_], coreComponents: CoreComponents): Unit = {
    builder match {
      case nettyBuilder: NettyChannelBuilder =>
        nettyBuilder
          .eventLoopGroup(coreComponents.eventLoopGroup)
          .channelFactory(Transports.newSocketChannelFactory(coreComponents.configuration.netty.useNativeTransport))
      case _ =>
    }
  }
}

sealed trait GrpcProtocol extends Protocol {
  private[gatling] val overridingKey: GrpcProtocol.Key
}

case class StaticGrpcProtocol(
  private val channelBuilder: ManagedChannelBuilder[_],
  private val _shareChannel: Boolean = false,
  private val warmUp: Option[GrpcProtocol.WarmUp] = Some(GrpcProtocol.defaultWarmUp),
  private val lazyParsing: Boolean = true
) extends GrpcProtocol {
  import GrpcProtocol._

  def shareChannel: StaticGrpcProtocol = copy(_shareChannel = true)

  def disableWarmUp: StaticGrpcProtocol = copy(warmUp = None)

  /**
   * By default, if nothing inspects the response body, the body is ignored.
   * This option forces the parsing.
   */
  def forceParsing: StaticGrpcProtocol = copy(lazyParsing = false)

  def warmUpCall[T](method: MethodDescriptor[T, _], req: T): StaticGrpcProtocol =
    copy(warmUp = Some((method, req)))

  def createComponents(id: Option[String], coreComponents: CoreComponents): GrpcComponent = {
    setEventLoopGroup(channelBuilder, coreComponents)
    new GrpcComponent(
      channelBuilder = channelBuilder,
      shareChannel = _shareChannel,
      channelAttributeName = id.fold(DefaultChannelAttributeName)(DefaultChannelAttributeName + "." + _),
      warmUp = warmUp,
      lazyParsing = lazyParsing
    )
  }

  private[gatling] lazy val overridingKey: Key = new ProtocolKey[GrpcProtocol, GrpcComponent] with StrictLogging {
    override def protocolClass: Class[Protocol] = GrpcProtocolKey.protocolClass

    override def defaultProtocolValue(configuration: GatlingConfiguration): GrpcProtocol =
      StaticGrpcProtocol.this

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { _ =>
      val id = UUID.randomUUID().toString
      logger.info(s"Creating a new non-default GrpcComponent with ID $id")
      createComponents(id = Some(id), coreComponents)
    }
  }

}

case class DynamicGrpcProtocol(
  private val channelAttributeName: String,
  private val lazyParsing: Boolean = true
) extends GrpcProtocol {
  import GrpcProtocol._

  /** See [[StaticGrpcProtocol.forceParsing]] */
  def forceParsing: DynamicGrpcProtocol = copy(lazyParsing = false)

  override private[gatling] val overridingKey: Key = new ProtocolKey[GrpcProtocol, GrpcComponent] {
    override def protocolClass: Class[Protocol] = classOf[DynamicGrpcProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration): GrpcProtocol =
      DynamicGrpcProtocol.this

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { _ =>
      new GrpcComponent(
        sharedChannel = null,
        shareChannel = false,
        channelAttributeName = channelAttributeName,
        lazyParsing = lazyParsing,
        onStart = Session.Identity
      )
    }
  }

  def setChannel(createBuilder: Session => ManagedChannelBuilder[_]): ActionBuilder =
    new SetDynamicChannelBuilder(channelAttributeName, createBuilder)

  /**
   * This is necessary only if you want to close the `ManagedChannel` early,
   * possibly for swapping another dynamic channel in.
   *
   * On exit of the virtual user, we attempt to shutdown the channel anyway.
   *
   * @return an ActionBuilder to be `exec`ed.
   */
  lazy val disposeChannel: ActionBuilder = new ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action =
      new DisposeDynamicChannel(channelAttributeName, next)
  }
}
