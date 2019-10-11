package com.github.phisgr.example

import com.github.phisgr.example.greet.{ChatMessage, GreetServiceGrpc, HelloWorld, RegisterRequest}
import com.github.phisgr.example.util.TokenHeaderKey
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
import com.github.phisgr.gatling.util._
// stringToExpression is hidden because we have $ in GrpcDsl
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression
import io.grpc.{ManagedChannelBuilder, Status}

class GrpcExample extends Simulation {
  TestServer.startServer()
  TestServer.startEmptyServer()

  val grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())
  val emptyGrpc = grpc(ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext())

  val helloWorld: Expression[HelloWorld] = HelloWorld(name = "World").updateExpr(
    _.username :~ $("username")
  )

  val successfulCall = grpc("Success")
    .rpc(GreetServiceGrpc.METHOD_GREET)
    .payload(helloWorld)
    .header(TokenHeaderKey)($("token"))
    .extract(_.data.split(' ').headOption)(_ saveAs "s")

  val s = scenario("Example")
    .feed(csv("usernames.csv")) // the first two are duplicated to force an ALREADY_EXISTS error
    .exec(
      grpc("Register")
        .rpc(GreetServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.updateExpr(
          _.username :~ $("username")
        ))
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .exec(successfulCall)
    .exec(
      grpc("Empty server")
        .rpc(GreetServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance)
        .check(statusCode is Status.Code.UNIMPLEMENTED)
        .target(emptyGrpc)
    )
    .exec(
      grpc("Cannot Build")
        .rpc(GreetServiceGrpc.METHOD_GREET)
        .payload($("notDefined"))
        .check(
          // the extraction checks can be added inside .check
          // but the extraction functions need type annotation
          // so .extract and .extractMultiple are added to GrpcCallActionBuilder, see below
          extract { c: ChatMessage => c.username.some },
          extractMultiple { c: ChatMessage => c.data.split(' ').toSeq.some }.find(5)
        )
    )
    .exec(
      grpc("Cannot Build, with old API")
        .service(GreetServiceGrpc.stub)
        // Session attribute "s" is a String (see line 26),
        // but we need a HelloWorld here.
        // Note that we do not need to supply the type parameter, it is just here for clarity
        .rpc(_.greet)($[HelloWorld]("s"))
    )
    .repeat(1000) {
      exec(successfulCall)
        .exec(
          grpc("Expect UNAUTHENTICATED")
            .rpc(GreetServiceGrpc.METHOD_GREET)
            .payload(helloWorld)
            .check(
              statusCode is Status.Code.UNAUTHENTICATED,
              statusDescription.notExists
            )
        )
        .exec(
          grpc("Expect PERMISSION_DENIED")
            .rpc(GreetServiceGrpc.METHOD_GREET)
            .payload(HelloWorld(username = "DoesNotExist"))
            .header(TokenHeaderKey)($("token"))
            .check(statusCode is Status.Code.PERMISSION_DENIED)
        )
        .exec(
          grpc("Use session")
            .rpc(GreetServiceGrpc.METHOD_GREET)
            .payload(
              HelloWorld.defaultInstance.updateExpr(
                _.name :~ $("s"),
                _.username :~ $("username")
              )
            )
            .header(TokenHeaderKey)($("token"))
            .extract(_.data.some)(_ is "Server says: Hello Server!")
        )
        .exec(
          grpc("Extraction crash")
            .rpc(GreetServiceGrpc.METHOD_GREET)
            .payload(helloWorld)
            .header(TokenHeaderKey)($("token"))
            .exists(_.data.split(' ')(10).some) // This will crash, see below
        )
        .exec(
          grpc("Extract multiple")
            .rpc(GreetServiceGrpc.METHOD_GREET)
            .payload(helloWorld)
            .header(TokenHeaderKey)($("token"))
            .extractMultiple(_.data.split(' ').toSeq.some)(
              _.count is 4,
              _.find(10).notExists,
              _.find(2) is "Hello",
              _.findAll is List("Server", "says:", "Hello", "World!")
            )
        )
    }

  setUp(
    s.inject(atOnceUsers(54))
  ).protocols(grpcConf)
}
