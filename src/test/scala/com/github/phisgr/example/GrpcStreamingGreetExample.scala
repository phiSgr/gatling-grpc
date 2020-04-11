package com.github.phisgr.example

import com.github.phisgr.example.greet._
import com.github.phisgr.example.util._
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
// stringToExpression is hidden because we have $ in GrpcDsl
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression
import io.grpc.Status

class GrpcStreamingGreetExample extends Simulation {
  TestServer.startServer()
  TestServer.startEmptyServer()

  val grpcConf = grpc(managedChannelBuilder(name = "localhost", port = 8080).usePlaintext())
    .disableWarmUp

  val helloWorld: Expression[HelloWorld] = HelloWorld(name = "World").updateExpr(
    _.username :~ $("username")
  )

  val s = scenario("Stream Example")
//    .feed(csv("usernames.csv")) // the first two are duplicated to force an ALREADY_EXISTS error
    .exec(
      grpc("Register")
        .rpc(GreetServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.withUsername("user_A"))
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .exec(grpc("Greet stream")
      .rpcServerStreaming(GreetServiceGrpc.METHOD_GREET_STREAM)
      .payload(HelloWorld.defaultInstance.withUsername("user_A"))
      .header(TokenHeaderKey)($("token"))
      .check(
        statusCode is Status.Code.OK
        )
    )

  setUp(
    s.inject(atOnceUsers(1))
  ).protocols(grpcConf)
    .assertions(
      details("Greet stream").failedRequests.count.is(0),
      global.failedRequests.count.is(0)
    )
}
