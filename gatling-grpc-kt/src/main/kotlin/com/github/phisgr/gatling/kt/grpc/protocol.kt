@file:JvmMultifileClass
@file:JvmName("GrpcDsl")
@file:Suppress("UNCHECKED_CAST")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.kt.grpc.internal.nullToFailure
import com.github.phisgr.gatling.kt.internal.ActionBuilderWrapper
import com.github.phisgr.gatling.kt.internal.safely
import com.github.phisgr.gatling.kt.javapb.ExpressionFunction
import com.google.protobuf.Message
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.ActionBuilder
import io.gatling.javaapi.core.ProtocolBuilder
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import scala.Function1
import java.util.function.Function
import com.github.phisgr.gatling.grpc.protocol.DynamicGrpcProtocol as DynamicGrpcProtocolS
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol as GrpcProtocolS
import com.github.phisgr.gatling.grpc.protocol.StaticGrpcProtocol as StaticGrpcProtocolS
import io.gatling.core.session.Session as SessionS

sealed class GrpcProtocol<Self : GrpcProtocol<Self, Wrapped>, Wrapped : GrpcProtocolS> : ProtocolBuilder {
    abstract override fun protocol(): Wrapped

    @PublishedApi
    @JvmSynthetic
    internal abstract fun wrap(wrapped: Wrapped): Self

    fun forceParsing(): Self = wrap(protocol().forceParsing() as Wrapped)

    /**
     * See also [com.github.phisgr.gatling.kt.grpc.request.CallDefinition.header]
     */
    @PublishedApi
    @JvmSynthetic
    internal fun <T : Any> header(
        key: Metadata.Key<T>,
        optional: Boolean,
        f: Function1<SessionS, Validation<T>>,
    ): Self = wrap(protocol().header(key, optional, f) as Wrapped)

    /**
     * Java API.
     * You have to provide the class of what type the [el] should evaluate to.
     */
    fun <T : Any> headerEL(
        key: Metadata.Key<T>,
        el: String,
        optional: Boolean,
        clazz: Class<T>,
    ): Self = header(key, optional, Expressions.toExpression(el, clazz))

    inline fun <reified T : Any> headerEL(
        key: Metadata.Key<T>,
        el: String,
        optional: Boolean = false,
    ): Self = headerEL(key, el, optional, T::class.java)

    fun <T : Any> header(
        key: Metadata.Key<T>,
        optional: Boolean = false,
        value: Function<Session, T?>,
    ): Self = when (value) {
        is ExpressionFunction<*> -> // bound is `: Any` never null
            header(key, optional, (value as ExpressionFunction<T>).asScala)
        else -> header(key, optional) { value.apply(it) }
    }

    @JvmSynthetic
    inline fun <T : Any> header(
        key: Metadata.Key<T>,
        optional: Boolean = false,
        crossinline value: (Session) -> T?,
    ): Self = header(key, optional) { session: SessionS ->
        safely { nullToFailure(value(Session(session)), key, optional) }
    }

    @JvmSynthetic
    inline fun <T : Message, Builder : Message.Builder> header(
        key: Metadata.Key<T>,
        optional: Boolean = false,
        crossinline newBuilder: () -> Builder,
        crossinline f: Builder.(Session) -> T?,
    ): Self = header(key, optional) {
        // Because of the null handling, we cannot use `fromSession` here.
        newBuilder().f(it)
    }

    fun <T : Any> header(
        key: Metadata.Key<T>,
        value: T,
    ): Self = wrap(protocol().header(key, false, Expressions.toStaticValueExpression(value)) as Wrapped)
}

class StaticGrpcProtocol(
    private val wrapped: StaticGrpcProtocolS,
) : GrpcProtocol<StaticGrpcProtocol, StaticGrpcProtocolS>() {

    fun shareChannel(): StaticGrpcProtocol = StaticGrpcProtocol(wrapped.shareChannel())

    fun disableWarmUp(): StaticGrpcProtocol =
        StaticGrpcProtocol(wrapped.disableWarmUp())

    fun <T> warmUpCall(method: MethodDescriptor<T, *>, req: T): StaticGrpcProtocol =
        StaticGrpcProtocol(wrapped.warmUpCall(method, req))

    override fun protocol(): StaticGrpcProtocolS = wrapped
    override fun wrap(wrapped: StaticGrpcProtocolS): StaticGrpcProtocol = StaticGrpcProtocol(wrapped)
}

class DynamicGrpcProtocol(
    private val wrapped: DynamicGrpcProtocolS,
) : GrpcProtocol<DynamicGrpcProtocol, DynamicGrpcProtocolS>() {
    fun setChannel(
        createBuilder: Function<Session, ManagedChannelBuilder<*>>,
    ): ActionBuilder =
        setChannel { createBuilder.apply(it) }

    @JvmSynthetic
    inline fun setChannel(
        crossinline createBuilder: (Session) -> ManagedChannelBuilder<*>,
    ): ActionBuilder =
        ActionBuilderWrapper(protocol().setChannel {
            createBuilder(Session(it))
        })

    /**
     * See [DynamicGrpcProtocolS.disposeChannel]
     */
    fun disposeChannel(): ActionBuilder = ActionBuilderWrapper(wrapped.disposeChannel())

    override fun protocol(): DynamicGrpcProtocolS = wrapped
    override fun wrap(wrapped: DynamicGrpcProtocolS): DynamicGrpcProtocol = DynamicGrpcProtocol(wrapped)
}
