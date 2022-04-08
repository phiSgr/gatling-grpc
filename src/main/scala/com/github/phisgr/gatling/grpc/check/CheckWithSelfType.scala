package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.Validation
import io.gatling.core.check.Check
import io.gatling.core.session.{Expression, Session}

import scala.annotation.unchecked.uncheckedVariance

/**
 * Make the return type of [[Check.checkIf]] to be the child class.
 *
 * @tparam T    the type of values being checked
 * @tparam Self the child class
 */
trait CheckWithSelfType[-T, +Self <: Check[T@uncheckedVariance]] extends Check[T@uncheckedVariance] {

  def checkIf(condition: Expression[Boolean]): Self

  def checkIf(condition: (T@uncheckedVariance, Session) => Validation[Boolean]): Self
}
