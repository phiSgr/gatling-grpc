@file:JvmMultifileClass
@file:JvmName("GrpcDsl")
@file:Suppress("UNCHECKED_CAST")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.action.StreamCancelBuilder
import com.github.phisgr.gatling.grpc.action.`StreamMessageAction$`
import com.github.phisgr.gatling.grpc.action.StreamReconciliateBuilder
import com.github.phisgr.gatling.grpc.stream.BidiStreamCall
import com.github.phisgr.gatling.grpc.stream.ClientStreamCall
import com.github.phisgr.gatling.grpc.stream.ServerStreamCall
import com.github.phisgr.gatling.grpc.stream.StreamCall
import com.github.phisgr.gatling.kt.getOrThrow
import com.github.phisgr.gatling.kt.grpc.action.*
import com.github.phisgr.gatling.kt.internal.ActionBuilderWrapper
import com.github.phisgr.gatling.kt.internal.elString
import com.github.phisgr.gatling.kt.internal.toScalaF
import com.github.phisgr.gatling.kt.javapb.asScala
import com.github.phisgr.gatling.kt.javapb.fromSession
import com.google.protobuf.Message
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.ActionBuilder
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions.toExpression
import io.gatling.javaapi.core.internal.Expressions.toStaticValueExpression
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import scala.Function1
import scala.reflect.ClassTag
import java.util.function.Function
import io.gatling.core.session.Session as SessionS

private typealias ExpressionS<T> = Function1<SessionS, Validation<T>>

fun dynamicChannel(channelAttributeName: String): DynamicGrpcProtocol =
    DynamicGrpcProtocol(Predef.dynamicChannel(channelAttributeName))

fun grpc(managedChannelBuilder: ManagedChannelBuilder<*>) = StaticGrpcProtocol(Predef.grpc(managedChannelBuilder))
fun grpc(requestName: String): Grpc = Grpc(requestName.elString)

class Grpc(@PublishedApi internal val requestName: ExpressionS<String>) {
    fun <Req, Res> rpc(method: MethodDescriptor<Req, Res>) =
        Unary(requestName, method)

    fun <Req, Res> serverStream(method: MethodDescriptor<Req, Res>, streamName: String) =
        ServerStream(requestName, method, streamName)

    fun <Req, Res> bidiStream(method: MethodDescriptor<Req, Res>, streamName: String, clazz: Class<Req>) =
        BidiStream(requestName, method, streamName, clazz)

    fun <Req, Res> clientStream(method: MethodDescriptor<Req, Res>, streamName: String, clazz: Class<Req>) =
        ClientStream(requestName, method, streamName, clazz)


    inline fun <reified Req, Res> bidiStream(method: MethodDescriptor<Req, Res>, streamName: String) =
        bidiStream(method, streamName, Req::class.java)

    inline fun <reified Req, Res> clientStream(method: MethodDescriptor<Req, Res>, streamName: String) =
        clientStream(method, streamName, Req::class.java)
}

inline fun <reified Req, Res> Unary<Req, Res>.payload(el: String): GrpcCallActionBuilder<Req, Res> =
    payload(el, Req::class.java)

@JvmSynthetic
inline fun <Req : Message, ReqBuilder : Message.Builder, Res> Unary<Req, Res>.payload(
    crossinline newBuilder: () -> ReqBuilder,
    crossinline f: ReqBuilder.(Session) -> Req,
): GrpcCallActionBuilder<Req, Res> =
    payload(fromSession(newBuilder, f))

class Unary<Req, Res>(
    val requestName: ExpressionS<String>,
    val method: MethodDescriptor<Req, Res>,
) {

    @PublishedApi
    @JvmSynthetic
    internal fun payload(body: ExpressionS<Req>): GrpcCallActionBuilder<Req, Res> =
        GrpcCallActionBuilder(
            Predef
                .grpc(requestName)
                .rpc(method)
                .payload(body)
        )

    fun payload(el: String, clazz: Class<Req>): GrpcCallActionBuilder<Req, Res> = payload(toExpression(el, clazz))
    fun payload(body: Req): GrpcCallActionBuilder<Req, Res> = payload(toStaticValueExpression(body))
    fun payload(f: Function<Session, Req>): GrpcCallActionBuilder<Req, Res> = payload(f.asScala())

    @JvmSynthetic
    inline fun payload(crossinline f: (Session) -> Req): GrpcCallActionBuilder<Req, Res> =
        payload(toScalaF { session: SessionS -> f(Session(session)) })
}

sealed class ListeningStream<Status, C : StreamCall<*, *, Status>>(private val callClazz: Class<C>) {
    abstract val requestName: ExpressionS<String>
    abstract val streamName: String
    abstract val direction: String
    fun cancelStream(): ActionBuilder =
        ActionBuilderWrapper(StreamCancelBuilder(requestName, streamName, direction))

    fun reconciliate(waitFor: WaitType = NO_WAIT): ActionBuilder =
        ActionBuilderWrapper(StreamReconciliateBuilder(requestName, streamName, direction, waitFor))

    fun status(session: Session): Status =
        `StreamMessageAction$`.`MODULE$`
            .fetchCall<C>(streamName, session.asScala(), direction, ClassTag.apply(callClazz))
            .getOrThrow()
            .state()
}

inline fun <reified Req, Res> ServerStream<Req, Res>.start(
    el: String,
): ServerStreamStartActionBuilder<Req, Res> =
    start(el, Req::class.java)

@JvmSynthetic
inline fun <Req : Message, ReqBuilder : Message.Builder, Res> ServerStream<Req, Res>.start(
    crossinline newBuilder: () -> ReqBuilder,
    crossinline f: ReqBuilder.(Session) -> Req,
): ServerStreamStartActionBuilder<Req, Res> =
    start(fromSession(newBuilder, f))

class ServerStream<Req, Res>(
    override val requestName: ExpressionS<String>,
    val method: MethodDescriptor<Req, Res>,
    override val streamName: String,
) : ListeningStream<StreamCall.ServerStreamState, ServerStreamCall<*, *>>(ServerStreamCall::class.java) {
    fun withRequestName(el: String): ServerStream<Req, Res> = ServerStream(
        el.elString, method, streamName
    )

    @PublishedApi
    @JvmSynthetic
    internal fun start(body: ExpressionS<Req>): ServerStreamStartActionBuilder<Req, Res> =
        ServerStreamStartActionBuilder(
            Predef
                .grpc(requestName)
                .serverStream(method, streamName)
                .start(body)
        )

    fun start(el: String, clazz: Class<Req>): ServerStreamStartActionBuilder<Req, Res> = start(toExpression(el, clazz))
    fun start(body: Req): ServerStreamStartActionBuilder<Req, Res> = start(toStaticValueExpression(body))
    fun start(f: Function<Session, Req>): ServerStreamStartActionBuilder<Req, Res> = start(f.asScala())

    @JvmSynthetic
    inline fun start(crossinline f: (Session) -> Req): ServerStreamStartActionBuilder<Req, Res> =
        start(toScalaF { session: SessionS -> f(Session(session)) })

    override val direction: String
        get() = "server"
}

@JvmSynthetic
inline fun <Req : Message, ReqBuilder : Message.Builder, Res> BidiStream<Req, Res>.send(
    crossinline newBuilder: () -> ReqBuilder,
    crossinline f: ReqBuilder.(Session) -> Req,
): StreamSendBuilder<Req> =
    send(fromSession(newBuilder, f))

class BidiStream<Req, Res>(
    override val requestName: ExpressionS<String>,
    val method: MethodDescriptor<Req, Res>,
    override val streamName: String,
    private val clazz: Class<Req>,
) : ListeningStream<StreamCall.BidiStreamState, BidiStreamCall<*, *>>(BidiStreamCall::class.java) {
    fun withRequestName(el: String): BidiStream<Req, Res> = BidiStream(
        el.elString, method, streamName, clazz
    )

    fun connect(): BidiStreamStartActionBuilder<Req, Res> =
        BidiStreamStartActionBuilder(streamS.connect())

    @PublishedApi
    @JvmSynthetic
    internal fun send(body: ExpressionS<Req>): StreamSendBuilder<Req> =
        StreamSendBuilder(streamS.send(body))

    fun send(el: String): StreamSendBuilder<Req> = send(toExpression(el, clazz))
    fun send(body: Req): StreamSendBuilder<Req> = send(toStaticValueExpression(body))
    fun send(f: Function<Session, Req>): StreamSendBuilder<Req> = send(f.asScala())

    @JvmSynthetic
    inline fun send(crossinline f: (Session) -> Req): StreamSendBuilder<Req> =
        send(toScalaF { session: SessionS -> f(Session(session)) })

    fun complete(waitFor: WaitType = NO_WAIT): ActionBuilder =
        ActionBuilderWrapper(streamS.complete(waitFor))

    private val streamS = Predef.grpc(requestName).bidiStream(method, streamName, ClassTag.apply(clazz))

    override val direction: String
        get() = "bidi"
}

@JvmSynthetic
inline fun <Req : Message, ReqBuilder : Message.Builder, Res> ClientStream<Req, Res>.send(
    crossinline newBuilder: () -> ReqBuilder,
    crossinline f: ReqBuilder.(Session) -> Req,
): StreamSendBuilder<Req> =
    send(fromSession(newBuilder, f))

class ClientStream<Req, Res>(
    val requestName: ExpressionS<String>,
    val method: MethodDescriptor<Req, Res>,
    val streamName: String,
    private val clazz: Class<Req>,
) {
    fun withRequestName(el: String): ClientStream<Req, Res> = ClientStream(
        el.elString, method, streamName, clazz
    )

    fun connect(): ClientStreamStartActionBuilder<Req, Res> =
        ClientStreamStartActionBuilder(streamS.connect())

    @PublishedApi
    @JvmSynthetic
    internal fun send(body: ExpressionS<Req>): StreamSendBuilder<Req> =
        StreamSendBuilder(streamS.send(body))

    fun send(el: String): StreamSendBuilder<Req> = send(toExpression(el, clazz))
    fun send(body: Req): StreamSendBuilder<Req> = send(toStaticValueExpression(body))
    fun send(f: Function<Session, Req>): StreamSendBuilder<Req> = send(f.asScala())

    @JvmSynthetic
    inline fun send(crossinline f: (Session) -> Req): ActionBuilder =
        send(toScalaF { session: SessionS -> f(Session(session)) })

    fun cancelStream(): ActionBuilder = ActionBuilderWrapper(streamS.cancelStream())
    fun completeAndWait(): ActionBuilder = ActionBuilderWrapper(streamS.completeAndWait())

    fun status(session: Session): StreamCall.ClientStreamState =
        `StreamMessageAction$`.`MODULE$`
            .fetchCall<ClientStreamCall<*, *>>(
                streamName,
                session.asScala(),
                "client",
                ClassTag.apply(ClientStreamCall::class.java)
            )
            .getOrThrow()
            .state()

    private val streamS = Predef.grpc(requestName).clientStream(method, streamName, ClassTag.apply(clazz))
}
