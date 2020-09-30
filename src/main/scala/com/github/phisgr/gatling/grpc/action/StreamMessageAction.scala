package com.github.phisgr.gatling.grpc.action

import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, ExitableAction, RequestAction}
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen

import scala.reflect.ClassTag

abstract class StreamMessageAction(
  override val requestName: Expression[String],
  ctx: ScenarioContext,
  override val next: Action,
  baseName: String,
  isBidi: Boolean
) extends RequestAction
  with ExitableAction
  with NameGen {
  def callFetchErrorMessage: String = s"Couldn't fetch open $direction stream"

  final def fetchCall[Call: ClassTag](streamName: String, session: Session): Validation[Call] =
    session(streamName)
      .validate[Call]
      .mapError(m => s"$callFetchErrorMessage: $m")

  override val statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override val clock: Clock = ctx.coreComponents.clock

  protected val direction: String = if (isBidi) "bidi" else "server"

  override val name: String = genName(direction + baseName)
}
