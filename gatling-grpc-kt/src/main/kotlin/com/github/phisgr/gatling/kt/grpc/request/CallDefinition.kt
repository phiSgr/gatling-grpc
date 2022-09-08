package com.github.phisgr.gatling.kt.grpc.request

import com.github.phisgr.gatling.grpc.check.CheckWithSelfType
import com.github.phisgr.gatling.kt.grpc.GrpcProtocol
import com.github.phisgr.gatling.kt.grpc.internal.dummyFrom
import com.github.phisgr.gatling.kt.internal.ActionCheckBuilder
import com.github.phisgr.gatling.kt.internal.toScalaF
import com.github.phisgr.gatling.kt.internal.toSeq
import com.github.phisgr.gatling.kt.javapb.asScala
import com.github.phisgr.gatling.kt.javapb.fromSession
import com.google.protobuf.Message
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions.toExpression
import io.gatling.javaapi.core.internal.Expressions.toStaticValueExpression
import io.grpc.CallOptions
import io.grpc.Metadata
import scala.Function1
import java.util.function.Function
import com.github.phisgr.gatling.grpc.request.CallDefinition as CallDefinitionS
import io.gatling.core.session.Session as SessionS

@Suppress("UNCHECKED_CAST")
abstract class CallDefinition<
    Self : CallDefinition<Self, Req, Res, WrappedRes, Wrapped, Check>,
    Req,
    Res,
    WrappedRes,
    Wrapped : CallDefinitionS<Wrapped, Req, Res>,
    Check : CheckWithSelfType<WrappedRes, Check>,
    > : ActionCheckBuilder<
    Self,
    Res,
    WrappedRes,
    Check
    >(dummyFrom) {
    abstract override fun asScala(): Wrapped

    @PublishedApi
    @JvmSynthetic
    internal abstract fun wrap(wrapped: Wrapped): Self

    /**
     * See also [com.github.phisgr.gatling.kt.grpc.GrpcProtocol.header]
     */
    @PublishedApi
    @JvmSynthetic
    internal fun <T> header(key: Metadata.Key<T>, f: Function1<SessionS, Validation<T>>) =
        wrap(asScala().header(key, f))

    inline fun <reified T : Any> headerEL(key: Metadata.Key<T>, el: String): Self = headerEL(key, el, T::class.java)
    fun <T : Any> headerEL(key: Metadata.Key<T>, el: String, clazz: Class<T>): Self =
        header(key, toExpression(el, clazz))

    fun <T : Any> header(key: Metadata.Key<T>, value: T): Self = header(key, toStaticValueExpression(value))
    fun <T : Any> header(key: Metadata.Key<T>, value: Function<Session, T>): Self = header(key, value.asScala())

    @JvmSynthetic
    inline fun <T : Any> header(key: Metadata.Key<T>, crossinline value: (Session) -> T): Self =
        header(key, toScalaF { session: SessionS -> value(Session(session)) })

    @JvmSynthetic
    inline fun <T : Message, Builder : Message.Builder> header(
        key: Metadata.Key<T>,
        crossinline newBuilder: () -> Builder,
        crossinline f: Builder.(Session) -> T,
    ): Self = header(key, fromSession(newBuilder, f))

    @JvmSynthetic
    inline fun callOptions(crossinline callOptions: (Session) -> CallOptions): Self = wrap(
        asScala().callOptions(toScalaF { session: SessionS ->
            callOptions(Session(session))
        })
    )

    fun callOptions(callOptions: Function<Session, CallOptions>) = wrap(
        asScala().callOptions(callOptions.asScala())
    )

    fun callOptions(callOptions: CallOptions) = wrap(
        asScala().callOptions(toStaticValueExpression(callOptions))
    )

    override fun addChecks(checks: List<Check>): Self =
        wrap(asScala().check(checks.toSeq()))

    fun target(protocol: GrpcProtocol<*, *>) = wrap(asScala().target(protocol.protocol()))

    abstract override fun buildCheck(builder: CheckBuilder): Check
}
