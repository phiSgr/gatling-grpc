package com.github.phisgr.gatling.pb

import java.util.concurrent.TimeUnit

import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb.test._
import io.gatling.commons.validation.Validation
import io.gatling.core.session.Session
import org.openjdk.jmh.annotations.{Benchmark, OutputTimeUnit}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
class TestUpdateExpr {
  import TestUpdateExpr._

  @Benchmark
  def updateSimpleExpr(): Validation[SimpleMessage] = {
    SimpleExpr(Session1)
  }

  @Benchmark
  def updateComplexExpr(): Validation[ComplexMessage] = {
    ComplexExpr(Session1)
  }
}

object TestUpdateExpr {
  private val SimpleExpr = SimpleMessage.defaultInstance.updateExpr(
    _.s :~ $("name")
  )
  private val ComplexExpr = ComplexMessage.defaultInstance.updateExpr(
    _.m.s :~ $("name"),
    _.i :~ $("count"),
  )
  private val Session1 = Session(
    scenario = "Scenario",
    userId = 1L,
    attributes = Map(
      "name" -> "Asdf Qwer",
      "count" -> 123
    ),
    startDate = System.currentTimeMillis()
  )
}
