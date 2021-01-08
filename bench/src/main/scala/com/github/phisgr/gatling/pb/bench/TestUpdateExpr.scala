package com.github.phisgr.gatling.pb.bench

import java.util.concurrent.TimeUnit

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.javapb._
import com.github.phisgr.gatling.pb.Test
import com.github.phisgr.gatling.pb.test._
import io.gatling.commons.validation.Validation
import io.gatling.core.session.{Expression, Session}
import org.openjdk.jmh.annotations.{Benchmark, Fork, OutputTimeUnit}

// JVM args from io.gatling.sbt.utils.PropertyUtils.DefaultJvmArgs
@Fork(jvmArgsAppend = Array("-XX:MaxInlineLevel=20", "-XX:MaxTrivialSize=12"))
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
  def updateSimpleExprJavaUncurry(): Validation[Test.SimpleMessage] = {
    SimpleExprJavaUncurry(Session1)
  }

  @Benchmark
  def updateComplexExprJavaUncurry(): Validation[Test.ComplexMessage] = {
    ComplexExprJavaUncurry(Session1)
  }

  @Benchmark
  def forSimpleExpr(): Validation[Test.SimpleMessage] = {
    SimpleExprFor(Session1)
  }

  @Benchmark
  def forComplexExpr(): Validation[Test.ComplexMessage] = {
    ComplexExprFor(Session1)
  }

  @Benchmark
  def matchComplexExpr(): Validation[Test.ComplexMessage] = {
    ComplexExprMatch(Session1)
  }

  @Benchmark
  def matchSimpleExpr(): Validation[Test.SimpleMessage] = {
    SimpleExprMatch(Session1)
  }
}

object TestUpdateExpr {

  private val nameExpression: Expression[String] = $("name")
  private val countExpression: Expression[Int] = $("count")

  private val SimpleExpr = SimpleMessage.defaultInstance.updateExpr(
    _.s :~ nameExpression
  )

  private val SimpleExprJava: Expression[Test.SimpleMessage] =
    Test.SimpleMessage.getDefaultInstance
      .update(_.setS)(nameExpression)

  private val SimpleExprJavaUncurry: Expression[Test.SimpleMessage] =
    Test.SimpleMessage.getDefaultInstance
      .updateUncurry(_.setS)(nameExpression)

  private val SimpleExprFor: Expression[Test.SimpleMessage] = { s: Session =>
    for {
      name <- nameExpression(s)
    } yield {
      val builder = Test.SimpleMessage.newBuilder()
      builder.setS(name)
      builder.build()
    }
  }

  private val SimpleExprMatch: Expression[Test.SimpleMessage] = { s: Session =>
    forToMatch {
      for {
        name <- nameExpression(s)
      } yield {
        val builder = Test.SimpleMessage.newBuilder()
        builder.setS(name)
        builder.build()
      }
    }
  }

  private val ComplexExpr = ComplexMessage.defaultInstance.updateExpr(
    _.m.s :~ nameExpression,
    _.i :~ countExpression
  )

  private val ComplexExprJava: Expression[Test.ComplexMessage] =
    Test.ComplexMessage.getDefaultInstance
      .update(_.getMBuilder.setS)(nameExpression)
      .update(_.setI)(countExpression)

  private val ComplexExprJavaUncurry: Expression[Test.ComplexMessage] =
    Test.ComplexMessage.getDefaultInstance
      .updateUncurry(_.getMBuilder.setS)(nameExpression)
      .updateUncurry(_.setI)(countExpression)

  private val ComplexExprFor: Expression[Test.ComplexMessage] = { s: Session =>
    for {
      name <- nameExpression(s)
      count <- countExpression(s)
    } yield {
      val builder = Test.ComplexMessage.newBuilder()
      builder.getMBuilder.setS(name)
      builder.setI(count)
      builder.build()
    }
  }

  private val ComplexExprMatch: Expression[Test.ComplexMessage] = { s: Session =>
    forToMatch {
      for {
        name <- nameExpression(s)
        count <- countExpression(s)
      } yield {
        val builder = Test.ComplexMessage.newBuilder()
        builder.getMBuilder.setS(name)
        builder.setI(count)
        builder.build()
      }
    }
  }

  private val Session1 = Session(
    scenario = "Scenario",
    userId = 1L,
    eventLoop = null // irrelevant in this test
  ).setAll(
    "name" -> "Asdf Qwer",
    "count" -> 123
  )
}
