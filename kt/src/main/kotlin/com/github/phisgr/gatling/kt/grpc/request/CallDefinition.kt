package com.github.phisgr.gatling.kt.grpc.request

import com.github.phisgr.gatling.grpc.check.CheckWithSelfType
import com.github.phisgr.gatling.kt.grpc.GrpcProtocol
import com.github.phisgr.gatling.kt.grpc.internal.*
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.ActionBuilder
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions
import io.grpc.CallOptions
import io.grpc.Metadata
import scala.Function2
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
    > : ActionBuilder {
    abstract override fun asScala(): Wrapped

    abstract fun wrap(wrapped: Wrapped): Self

    @JvmName("_KtHeader")
    inline fun <reified T> header(key: Metadata.Key<T>, el: String): Self = header(key, el, T::class.java)

    inline fun <T> header(key: Metadata.Key<T>, crossinline value: (Session) -> T): Self = wrap(
        asScala().header(key, toScalaExpression { session: SessionS ->
            value(Session(session))
        })
    )

    fun <T> header(key: Metadata.Key<T>, el: String, clazz: Class<T>): Self = wrap(
        asScala().header(key, Expressions.toExpression(el, clazz))
    )

    inline fun callOptions(crossinline callOptions: (Session) -> CallOptions): Self = wrap(
        asScala().callOptions(toScalaExpression { session: SessionS ->
            callOptions(Session(session))
        })
    )


    /**
     * To define the extractions outside this method,
     * see [com.github.phisgr.gatling.kt.grpc.extract] and
     * [com.github.phisgr.gatling.kt.grpc.extractMultiple].
     */
    @JvmName("_KotlinCheck")
    fun check(vararg checks: From<Res>.() -> CheckBuilder): Self = wrap(
        asScala().check(
            checks.map { buildCheck(it.invoke(dummyFrom)) }.toSeq()
        )
    )

    fun check(vararg checks: CheckBuilder): Self = wrap(
        asScala().check(
            checks.map { buildCheck(it) }.toSeq()
        )
    )

    fun target(protocol: GrpcProtocol) = wrap(asScala().target(protocol.protocol()))

    fun checkIf(condition: String) = ConditionWithoutRes(
        this as Self,
        Expressions.toBooleanExpression(condition)
    )

//    fun checkIf(condition: Function<Session, Boolean>) = ConditionWithoutRes(
//        this as Self,
//        Expressions.javaBooleanFunctionToExpression(condition)
//    )
//
//    fun checkIf(condition: BiFunction<WrappedRes, Session, Boolean>): ConditionWithRes<Self, Req, Res, WrappedRes, Wrapped, Check> =
//        checkIf { res, session -> condition.apply(res, session) }

    //    @JvmName("_KotlinCheckIf")
    inline fun checkIf(crossinline condition: (Session) -> Boolean) = ConditionWithoutRes(
        this as Self
    ) { session -> boolValidation { condition(Session(session)) } }

    //    @JvmName("_KotlinCheckIf")
    inline fun checkIf(crossinline condition: (WrappedRes, Session) -> Boolean) = ConditionWithRes(
        this as Self
    ) { res, session ->
        boolValidation { condition(res as WrappedRes, Session(session)) }
    }

    protected abstract fun buildCheck(check: CheckBuilder): Check

    class ConditionWithoutRes<
        Self : CallDefinition<Self, Req, Res, WrappedRes, Wrapped, Check>,
        Req,
        Res,
        WrappedRes,
        Wrapped : CallDefinitionS<Wrapped, Req, Res>,
        Check : CheckWithSelfType<WrappedRes, Check>,
        >(
        private val builder: Self,
        private val condition: scala.Function1<SessionS, Validation<UnknownTypeParam>>,
    ) {
        @JvmName("_KotlinThen")
        fun then(vararg checks: From<Res>.() -> CheckBuilder): Self =
            then(*checks.map { it.invoke(dummyFrom) }.toTypedArray())

        fun then(vararg checks: CheckBuilder): Self = builder.wrap(
            builder.asScala().checkIf(
                condition,
                checks.map { builder.buildCheck(it) }.toSeq()
            )
        )
    }

    class ConditionWithRes<
        Self : CallDefinition<Self, Req, Res, WrappedRes, Wrapped, Check>,
        Req,
        Res,
        WrappedRes,
        Wrapped : CallDefinitionS<Wrapped, Req, Res>,
        Check : CheckWithSelfType<WrappedRes, Check>,
        >(
        private val builder: Self,
        private val condition: Function2<UnknownTypeParam, SessionS, Validation<UnknownTypeParam>>,
    ) {
        @JvmName("_KotlinThen")
        fun then(vararg checks: From<Res>.() -> CheckBuilder): Self =
            then(*checks.map { it.invoke(dummyFrom) }.toTypedArray())

        fun then(vararg checks: CheckBuilder): Self = builder.wrap(
            builder.asScala().checkIf(
                condition,
                checks.map { builder.buildCheck(it) }.toSeq()
            )
        )
    }
}
