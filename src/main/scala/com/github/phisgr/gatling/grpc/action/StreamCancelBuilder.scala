package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.stream.Cancellable
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext

class StreamCancelBuilder(requestName: Expression[String], streamName: Expression[String], direction: String) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, streamName, ctx, next, baseName = "StreamClose", direction) {
      override def sendRequest(session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall(classOf[Cancellable], session)
        } yield {
          logger.debug(s"Cancelling ${this.direction} stream '${call.streamName}': Scenario '${session.scenario}', UserId #${session.userId}")
          call.cancel(session, this.next)
        }
      }
    }
}
