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

  val grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())

  val helloWorld: Expression[HelloWorld] = HelloWorld(name = "World").updateExpr(
    _.username :~ $("username")
  )

  val s = scenario("Example")
    .feed(csv("usernames.csv")) // the first two are duplicated to force an ALREADY_EXISTS error
    .exec(setUpGrpc)
    .exec(
      grpc("Register")
        .service(GreetServiceGrpc.stub)
        .rpc(_.register)(
          RegisterRequest.defaultInstance.updateExpr(
            _.username :~ $("username")
          )
        )
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .repeat(1000) {
      exec(
        grpc("Success")
          .service(GreetServiceGrpc.stub)
          .rpc(_.greet)(helloWorld)
          .header(TokenHeaderKey)($("token"))
          .extract(_.data.split(' ').headOption)(_ saveAs "s")
      )
        .exec(
          grpc("Cannot Build")
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)($("notDefined"))
            .check(
              // the extraction checks can be added inside .check
              // but the extraction functions need type annotation
              // so .extract and .extractMultiple are added to GrpcCallActionBuilder, see below
              extract { c: ChatMessage => c.username.some },
              extractMultiple { c: ChatMessage => c.data.split(' ').toSeq.some }.find(5)
            )
        )
        .exec(
          grpc("Cannot Build")
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)($[HelloWorld]("s")) // Wrong type
        )
        .exec(
          grpc("Expect UNAUTHENTICATED")
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)(helloWorld)
            .check(
              statusCode is Status.Code.UNAUTHENTICATED,
              statusDescription.notExists
            )
        )
        .exec(
          grpc("Expect PERMISSION_DENIED")
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)(HelloWorld(username = "DoesNotExist"))
            .header(TokenHeaderKey)($("token"))
            .check(statusCode is Status.Code.PERMISSION_DENIED)
        )
        .exec(
          grpc("Use session")
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)(
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
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)(helloWorld)
            .header(TokenHeaderKey)($("token"))
            .exists(_.data.split(' ')(10).some) // This will crash, see below
        )
        .exec(
          grpc("Extract multiple")
            .service(GreetServiceGrpc.stub)
            .rpc(_.greet)(helloWorld)
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
