package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.stream.StreamCall
import com.github.phisgr.gatling.grpc.stream.StreamCall.WaitType
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext

class StreamReconciliateBuilder(
  requestName: Expression[String],
  streamName: Expression[String],
  direction: String,
  waitType: WaitType,
  sync: Boolean
) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, streamName, ctx, next, baseName = "StreamReconciliate", direction) {
      private[this] val sync = StreamReconciliateBuilder.this.sync
      private[this] val waitType = StreamReconciliateBuilder.this.waitType
      override def sendRequest(session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall(classOf[StreamCall[_, _, _]], session)
        } yield {
          call.combineState(session, this.next, waitType, sync)
        }
      }
    }
}
