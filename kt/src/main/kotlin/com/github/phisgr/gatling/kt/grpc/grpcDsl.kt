@file:JvmName("GrpcDsl")
@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.kt.grpc.action.GrpcCallActionBuilder
import com.github.phisgr.gatling.kt.grpc.internal.safely
import com.github.phisgr.gatling.kt.grpc.internal.toScalaExpression
import com.github.phisgr.gatling.kt.javapb.MessageUpdater
import io.gatling.commons.validation.Failure
import io.gatling.commons.validation.Success
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.ProtocolBuilder
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import scala.Function1
import java.util.function.Function
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol as GrpcProtocolS
import com.github.phisgr.gatling.grpc.protocol.StaticGrpcProtocol as StaticGrpcProtocolS
import io.gatling.core.session.Session as SessionS

fun grpc(managedChannelBuilder: ManagedChannelBuilder<*>) = StaticGrpcProtocol(Predef.grpc(managedChannelBuilder))
fun grpc(requestName: String): Grpc = Grpc(Expressions.toStringExpression(requestName))

sealed class GrpcProtocol : ProtocolBuilder {
    abstract override fun protocol(): GrpcProtocolS
}

class StaticGrpcProtocol(private val wrapped: StaticGrpcProtocolS) : GrpcProtocol() {

    fun <T> warmUpCall(method: MethodDescriptor<T, *>, req: T): StaticGrpcProtocol =
        StaticGrpcProtocol(wrapped.warmUpCall(method, req))

    fun forceParsing(): StaticGrpcProtocol = StaticGrpcProtocol(wrapped.forceParsing())

    fun shareChannel(): StaticGrpcProtocol = StaticGrpcProtocol(wrapped.shareChannel())

    fun <T : Any> header(
        key: Metadata.Key<T>,
        optional: Boolean,
        el: String,
        clazz: Class<T>,
    ): StaticGrpcProtocol =
        StaticGrpcProtocol(protocol().header(key, optional, Expressions.toExpression(el, clazz)))

    @JvmName("_KotlinHeader")
    inline fun <reified T : Any> header(
        key: Metadata.Key<T>,
        optional: Boolean = false,
        el: String,
    ): StaticGrpcProtocol = header(key, optional, el, T::class.java)

//    fun <T : Any> header(
//        key: Metadata.Key<T>,
//        optional: Boolean = false,
//        value: Function<Session, T?>,
//    ) = header(key, optional) { value.apply(it) }

    //    @JvmName("_KotlinHeader")
    inline fun <T : Any> header(
        key: Metadata.Key<T>,
        optional: Boolean = false,
        crossinline value: (Session) -> T?,
    ): StaticGrpcProtocol =
        StaticGrpcProtocol(protocol().header(key, optional) { session ->
            safely {
                when (val got = value(Session(session))) {
                    null -> Failure.apply("Header for ${key.name()} not found.") as Validation<T>
                    else -> Success.apply(got)
                }
            }
        })

    override fun protocol(): StaticGrpcProtocolS = wrapped

}

class Grpc(private val requestName: Function1<SessionS, Validation<String>>) {
    fun <Req, Res> rpc(method: MethodDescriptor<Req, Res>) = Unary(requestName, method)
}

@JvmName("_KotlinPayload")
inline fun <reified Req, Res> Unary<Req, Res>.payload(el: String) = payload(el, Req::class.java)

class Unary<Req, Res>(
    val requestName: Function1<SessionS, Validation<String>>,
    val method: MethodDescriptor<Req, Res>,
) {

    fun payload(el: String, clazz: Class<Req>) =
        GrpcCallActionBuilder(
            Predef
                .grpc(requestName)
                .rpc(method)
                .payload(Expressions.toExpression(el, clazz))
        )

    fun payload(body: Req): GrpcCallActionBuilder<Req, Res> =
        payload { body }

    fun payload(f: Function<Session, Req>): GrpcCallActionBuilder<Req, Res> = when (f) {
        is MessageUpdater<*, *> -> GrpcCallActionBuilder(
            Predef
                .grpc(requestName)
                .rpc(method)
                .payload(f.asScala() as Function1<SessionS, Validation<Req>>)
        )
        else -> payload { f.apply(it) }
    }

    @JvmName("_KotlinPayload")
    inline fun payload(crossinline f: (Session) -> Req): GrpcCallActionBuilder<Req, Res> =
        GrpcCallActionBuilder(
            Predef
                .grpc(requestName)
                .rpc(method)
                .payload(toScalaExpression { session: SessionS -> f(Session(session)) })
        )
}
