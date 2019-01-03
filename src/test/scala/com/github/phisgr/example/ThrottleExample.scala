package com.github.phisgr.example

import com.github.phisgr.example.greet.{GreetServiceGrpc, HelloWorld, RegisterRequest}
import com.github.phisgr.example.util.TokenHeaderKey
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
import com.github.phisgr.gatling.util._
// stringToExpression is hidden because we have $ in GrpcDsl
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression
import io.grpc.ManagedChannelBuilder

import scala.concurrent.duration._

class ThrottleExample extends Simulation {
  TestServer.startServer()

  val grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())

  val helloWorld: Expression[HelloWorld] = HelloWorld(name = "World").updateExpr(
    _.username :~ $("username")
  )

  val s = scenario("Throttle")
    .feed(csv("usernames.csv").queue)
    .exec(setUpGrpc)
    .exec(
      grpc("Register")
        .rpc(GreetServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.updateExpr(
          _.username :~ $("username")
        ))
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .repeat(100) {
      exec(
        grpc("Success")
          .rpc(GreetServiceGrpc.METHOD_GREET)
          .payload(helloWorld)
          .header(TokenHeaderKey)($("token"))
          .extract(_.data.split(' ').headOption)(_ saveAs "s")
      )
    }

  setUp(
    s.inject(atOnceUsers(10)).throttle(reachRps(50) in 10.seconds, holdFor(10.minutes))
  ).protocols(grpcConf)
}
