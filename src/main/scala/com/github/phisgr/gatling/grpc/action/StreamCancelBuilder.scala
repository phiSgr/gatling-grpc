package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.stream.Cancellable
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext

class StreamCancelBuilder(requestName: Expression[String], streamName: String, direction: String) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, ctx, next, baseName = "StreamClose", direction) {
      // close over the string rather than the outer class
      private[this] val streamName = StreamCancelBuilder.this.streamName
      override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall[Cancellable](streamName, session)
        } yield {
          logger.info(s"Cancelling $direction stream '$streamName': Scenario '${session.scenario}', UserId #${session.userId}")
          call.cancel(session, next)
        }
      }
    }
}
