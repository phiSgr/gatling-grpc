package com.github.phisgr.example

import com.github.phisgr.example.chat.{ChatServiceGrpc, GreetRequest, RegisterRequest}
import com.github.phisgr.example.util.TokenHeaderKey
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
// stringToExpression is hidden because we have $ in GrpcDsl
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression

class DynamicExample extends Simulation {
  val ports = Vector(8081, 8082, 8083)
  ports.foreach(TestServer.startServer)

  val static1 = grpc(managedChannelBuilder(name = "localhost", port = 8081).usePlaintext())
  val static2 = grpc(managedChannelBuilder(target = "localhost:8082").usePlaintext()).shareChannel
  val dynamic = dynamicChannel("target").forceParsing

  val greetPayload: Expression[GreetRequest] = GreetRequest(name = "World")
    .updateExpr(_.username :~ $("username"))

  val s = scenario("Throttle")
    .feed(csv("usernames.csv").queue)
    .feed(ports.map(port => Map("port" -> port)).circular)
    .exec(
      grpc("Register")
        .rpc(ChatServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.updateExpr(
          _.username :~ $("username")
        ))
        .extract(_.token.some)(_ saveAs "token")
        .target(static1)
    )
    .exec(dynamic.setChannel { session =>
      // Imagine this comes from an API response
      val port = session("port").as[Int]
      managedChannelBuilder(s"localhost:$port").usePlaintext()
    })
    .exitHereIfFailed
    .repeat(10) {
      exec(
        grpc("Success")
          .rpc(ChatServiceGrpc.METHOD_GREET)
          .payload(greetPayload)
          .header(TokenHeaderKey)($("token"))
          .extract(_.data.split(' ').lift(3).map(_.toInt))(_ is $("port"))
          .target(dynamic)
      )
    }
    .exitHereIfFailed
    .exec(
      grpc("Final one")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload(greetPayload)
        .header(TokenHeaderKey)($("token"))
        .target(static2)
    )

  // If all calls specify the target, no global default protocol is needed
  setUp(
    s.inject(atOnceUsers(5))
  ).assertions(
    global.allRequests.count is (11 * 3 + 5)
  )
}
