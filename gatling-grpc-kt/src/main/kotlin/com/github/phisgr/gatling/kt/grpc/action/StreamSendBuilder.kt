package com.github.phisgr.gatling.kt.grpc.action

import io.gatling.commons.util.Clock
import io.gatling.javaapi.core.ActionBuilder
import io.gatling.javaapi.core.Session
import scala.runtime.BoxedUnit
import com.github.phisgr.gatling.grpc.action.StreamSendBuilder as StreamSendBuilderS
import io.gatling.core.action.builder.ActionBuilder as ActionBuilderS

class StreamSendBuilder<Req>(@PublishedApi internal val wrapped: StreamSendBuilderS<Req>) : ActionBuilder {
    // Java doesn't have a built-in TriFunction interface
    // won't bother creating a Java-specific API
    inline fun preSendAction(crossinline action: (Clock, Req, Session) -> Unit): StreamSendBuilder<Req> =
        StreamSendBuilder(wrapped.preSendAction { clock, req, session ->
            action(clock, req, Session(session))
            BoxedUnit.UNIT
        })

    override fun asScala(): ActionBuilderS = wrapped
}
