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
  override val direction: String
) extends RequestAction
  with ExitableAction
  with NameGen
  with StreamCallAccess {
  override val statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override val clock: Clock = ctx.coreComponents.clock

  override val name: String = genName(direction + baseName)
}
