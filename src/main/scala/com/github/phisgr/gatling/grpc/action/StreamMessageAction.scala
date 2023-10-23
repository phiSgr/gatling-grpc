package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.util.getFromSession
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, ExitableAction, RequestAction}
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen

object StreamMessageAction {
  def fetchCall[Call](callClass: Class[Call], streamName: Expression[String], session: Session, direction: String): Validation[Call] = forToMatch {
    for {
      name <- streamName(session)
      call <- getFromSession(callClass, session, name)
        .mapFailure(m => s"Couldn't fetch open $direction stream: $m")
    } yield call
  }
}

abstract class StreamMessageAction(
  override val requestName: Expression[String],
  streamName: Expression[String],
  ctx: ScenarioContext,
  override val next: Action,
  baseName: String,
  val direction: String
) extends RequestAction
  with ExitableAction
  with NameGen {
  final def fetchCall[Call](callClass: Class[Call], session: Session): Validation[Call] =
    StreamMessageAction.fetchCall(callClass, streamName, session, direction)

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override def clock: Clock = ctx.coreComponents.clock

  override val name: String = genName(direction + baseName)
}
