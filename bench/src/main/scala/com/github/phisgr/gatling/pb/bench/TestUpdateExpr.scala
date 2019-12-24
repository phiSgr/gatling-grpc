package com.github.phisgr.gatling.pb.bench

import java.util.concurrent.TimeUnit

import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.javapb._
import com.github.phisgr.gatling.pb.Test
import com.github.phisgr.gatling.pb.test._
import io.gatling.commons.validation.Validation
import io.gatling.core.session.{Expression, Session}
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

  @Benchmark
  def updateSimpleExprJava(): Validation[Test.SimpleMessage] = {
    SimpleExprJava(Session1)
  }

  @Benchmark
  def updateComplexExprJava(): Validation[Test.ComplexMessage] = {
    ComplexExprJava(Session1)
  }

  @Benchmark
  def lambdaSimpleExprJava(): Validation[Test.SimpleMessage] = {
    SimpleExprJavaLambda(Session1)
  }

  @Benchmark
  def lambdaComplexExprJava(): Validation[Test.ComplexMessage] = {
    ComplexExprJavaLambda(Session1)
  }
}

object TestUpdateExpr {
  private val SimpleExpr = SimpleMessage.defaultInstance.updateExpr(
    _.s :~ $("name")
  )

  private val SimpleExprJava: Expression[Test.SimpleMessage] =
    Test.SimpleMessage.getDefaultInstance
      .update(_.setS)($("name"))

  private val SimpleExprJavaLambda: Expression[Test.SimpleMessage] = { s: Session =>
    for {
      name <- s("name").validate[String]
    } yield {
      val builder = Test.SimpleMessage.newBuilder()
      builder.setS(name)
      builder.build()
    }
  }

  private val ComplexExpr = ComplexMessage.defaultInstance.updateExpr(
    _.m.s :~ $("name"),
    _.i :~ $("count"),
  )

  private val ComplexExprJava: Expression[Test.ComplexMessage] =
    Test.ComplexMessage.getDefaultInstance
      .update(_.getMBuilder.setS)($("name"))
      .update(_.setI)($("count"))

  private val ComplexExprJavaLambda: Expression[Test.ComplexMessage] = { s: Session =>
    for {
      name <- s("name").validate[String]
      count <- s("count").validate[Int]
    } yield {
      val builder = Test.ComplexMessage.newBuilder()
      builder.getMBuilder.setS(name)
      builder.setI(count)
      builder.build()
    }
  }

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
