@file:Suppress("UNCHECKED_CAST")

package com.github.phisgr.gatling.kt.grpc.action

import com.github.phisgr.gatling.grpc.check.GrpcResponse
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.stream.EventExtractor
import com.github.phisgr.gatling.kt.generic.SessionCombiner
import com.github.phisgr.gatling.kt.grpc.StreamEndLog
import com.github.phisgr.gatling.kt.grpc.internal.GrpcStreamEnd
import com.github.phisgr.gatling.kt.grpc.internal.buildStream
import com.github.phisgr.gatling.kt.grpc.internal.buildStreamEnd
import com.github.phisgr.gatling.kt.grpc.request.CallDefinition
import com.github.phisgr.gatling.kt.grpc.statusCode
import com.github.phisgr.gatling.kt.grpc.statusDescription
import com.github.phisgr.gatling.kt.grpc.stream.TimestampExtractor
import com.github.phisgr.gatling.kt.grpc.trailer
import com.github.phisgr.gatling.kt.internal.*
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions
import scala.Function1
import scala.Function2
import scala.collection.immutable.Seq
import scala.runtime.`Null$`
import java.util.function.BiPredicate
import java.util.function.Predicate
import com.github.phisgr.gatling.generic.SessionCombiner as SessionCombinerS
import com.github.phisgr.gatling.grpc.action.StreamStartBuilder as StreamStartBuilderS
import com.github.phisgr.gatling.grpc.stream.TimestampExtractor as TimestampExtractorS
import io.gatling.core.session.Session as SessionS

/**
 * Superclass for starting a server stream or a bidi stream.
 * For end checks, we do not need the lambda-taking API with [From],
 * because its content is only
 * status (extracted with [statusCode] and [statusDescription])
 * and [trailer].
 */
abstract class StreamStartBuilder<Self : StreamStartBuilder<Self, Req, Res, Wrapped>, Req, Res, Wrapped : StreamStartBuilderS<Wrapped, Req, Res>> :
    CallDefinition<
        Self,
        Req,
        Res,
        Res,
        Wrapped,
        StreamCheck<Res>
        >() {

    fun endCheck(vararg checks: CheckBuilder): Self =
        addEndChecks(checks.map { it.buildStreamEnd() })

    fun endCheckIf(condition: String): ConditionWithoutRes<Self, Wrapped, Req, Res> =
        ConditionWithoutRes(this as Self, Expressions.toBooleanExpression(condition))

    @JvmSynthetic
    inline fun endCheckIf(
        crossinline condition: (Session) -> Boolean,
    ): ConditionWithoutRes<Self, Wrapped, Req, Res> =
        ConditionWithoutRes(this as Self) { session -> boolValidation { condition(Session(session)) } }

    fun endCheckIf(
        condition: Predicate<Session>,
    ): ConditionWithoutRes<Self, Wrapped, Req, Res> =
        ConditionWithoutRes(this as Self, condition.toExpression())

    @JvmSynthetic
    inline fun endCheckIf(
        crossinline condition: (GrpcStreamEnd, Session) -> Boolean,
    ): ConditionWithRes<Self, Wrapped, Req, Res> =
        ConditionWithRes(this as Self) { res, session ->
            boolValidation { condition(res, Session(session)) }
        }

    fun endCheckIf(
        condition: BiPredicate<GrpcStreamEnd, Session>,
    ): ConditionWithRes<Self, Wrapped, Req, Res> =
        ConditionWithRes(this as Self, condition.toFunction2())

    fun streamEndLog(logWhen: StreamEndLog): Self =
        wrap(asScala().streamEndLog(logWhen))

    fun timestampExtractor(extractor: TimestampExtractor<Res>): Self =
        wrap(asScala().timestampExtractor(extractor))

    @Deprecated("Use the overload for the Kotlin wrapper type.", level = DeprecationLevel.HIDDEN)
    fun timestampExtractor(extractor: TimestampExtractorS<Res>): Self =
        wrap(asScala().timestampExtractor(extractor))

    /**
     * This exposes the internal StatsEngine of Gatling.
     * No wrapper class around them, as this is a niche, advanced API.
     */
    fun eventExtractor(extractor: EventExtractor<Res>): Self =
        wrap(asScala().eventExtractor(extractor))

    fun sessionCombiner(sessionCombiner: SessionCombiner): Self =
        wrap(asScala().sessionCombiner(sessionCombiner))

    @Deprecated("Use the overload for the Kotlin wrapper type.", level = DeprecationLevel.HIDDEN)
    fun sessionCombiner(sessionCombiner: SessionCombinerS): Self =
        wrap(asScala().sessionCombiner(sessionCombiner))


    class ConditionWithoutRes<
        Self : StreamStartBuilder<Self, Req, Res, Wrapped>,
        Wrapped : StreamStartBuilderS<Wrapped, Req, Res>,
        Req,
        Res,
        >(
        private val builder: Self,
        private val condition: Function1<SessionS, Validation<PrimitiveBool>>,
    ) {
        fun then(vararg checks: CheckBuilder): Self =
            builder.addEndChecks(
                checks.map {
                    it.buildStreamEnd().checkIf(condition)
                }
            )
    }

    class ConditionWithRes<
        Self : StreamStartBuilder<Self, Req, Res, Wrapped>,
        Wrapped : StreamStartBuilderS<Wrapped, Req, Res>,
        Req,
        Res,
        >(
        private val builder: Self,
        private val condition: Function2<GrpcStreamEnd, SessionS, Validation<PrimitiveBool>>,
    ) {
        fun then(vararg checks: CheckBuilder): Self =
            builder.addEndChecks(
                checks.map {
                    it.buildStreamEnd().checkIf(condition)
                }
            )
    }

    private fun addEndChecks(checks: List<StreamCheck<GrpcStreamEnd>>) =
        wrap(asScala().endCheck(checks.toSeq() as Seq<StreamCheck<GrpcResponse<`Null$`>>>))

    override fun buildCheck(builder: CheckBuilder): StreamCheck<Res> = builder.buildStream()

}
