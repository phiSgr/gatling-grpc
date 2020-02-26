package com.github.phisgr.example

import java.util.concurrent.TimeUnit

import com.github.phisgr.example.greet.{GreetServiceGrpc, HelloWorld, RegisterRequest}
import com.github.phisgr.example.util.TokenHeaderKey
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
import io.grpc.CallOptions
import io.grpc.Status.Code._
// stringToExpression is hidden because we have $ in GrpcDsl
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression

import scala.concurrent.duration._

class ThrottleExample extends Simulation {
  TestServer.startServer()

  val grpcConf = grpc(managedChannelBuilder(target = "localhost:8080").usePlaintext())
    .shareChannel
    .disableWarmUp

  val helloWorld: Expression[HelloWorld] = HelloWorld(name = "World").updateExpr(
    _.username :~ $("username")
  )

  /**
   * This instance of CallOptions is created at the start of the simulation.
   * It will be expired by the time it is used.
   */
  val callOptionsWithFixedDeadline: Expression[CallOptions] =
    CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.SECONDS)

  val s = scenario("Throttle")
    .feed(csv("usernames.csv").queue)
    .exec(
      grpc("Register")
        .rpc(GreetServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.updateExpr(
          _.username :~ $("username")
        ))
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .repeat(60) {
      exec(
        grpc("Success")
          .rpc(GreetServiceGrpc.METHOD_GREET)
          .payload(helloWorld)
          .header(TokenHeaderKey)($("token"))
          .extract(_.data.split(' ').headOption)(_ saveAs "s")
      ).exec(
        grpc("With deadline")
          .rpc(GreetServiceGrpc.METHOD_GREET)
          .payload(helloWorld)
          .header(TokenHeaderKey)($("token"))
          .callOptions(CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.SECONDS))
      )
    }.exec(
      grpc("Fixed deadline")
        .rpc(GreetServiceGrpc.METHOD_GREET)
        .payload(helloWorld)
        .header(TokenHeaderKey)($("token"))
        .callOptions(callOptionsWithFixedDeadline)
        .check(statusCode is DEADLINE_EXCEEDED)
  )

  setUp(
    s.inject(atOnceUsers(10)).throttle(reachRps(50) in 10.seconds, holdFor(10.minutes))
  ).protocols(grpcConf)
}
