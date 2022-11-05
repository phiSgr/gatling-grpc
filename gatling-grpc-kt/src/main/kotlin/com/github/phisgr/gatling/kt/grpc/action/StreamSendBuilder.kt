package com.github.phisgr.gatling.kt.grpc.action

import io.gatling.commons.util.Clock
import io.gatling.javaapi.core.ActionBuilder
import io.gatling.javaapi.core.Session
import scala.runtime.BoxedUnit
import com.github.phisgr.gatling.grpc.action.StreamSendBuilder as StreamSendBuilderS
import io.gatling.core.action.builder.ActionBuilder as ActionBuilderS

class StreamSendBuilder<Req>(@PublishedApi internal val wrapped: StreamSendBuilderS<Req>) : ActionBuilder {
    @JvmSynthetic
    inline fun preSendAction(crossinline action: (Clock, Req, Session) -> Unit): StreamSendBuilder<Req> =
        StreamSendBuilder(wrapped.preSendAction { clock, req, session ->
            action(clock, req, Session(session))
            BoxedUnit.UNIT
        })

    fun preSendAction(action: PreSendAction<Req>): StreamSendBuilder<Req> =
        preSendAction { clock, req, session -> action(clock, req, session) }

    override fun asScala(): ActionBuilderS = wrapped
}

@FunctionalInterface // but not Kotlin's `fun interface` because it's for Java
interface PreSendAction<Req> {
    operator fun invoke(clock: Clock, req: Req, session: Session)
}
