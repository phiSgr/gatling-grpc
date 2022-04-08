package com.github.phisgr.example

import com.github.phisgr.example.chat._
import com.github.phisgr.example.util._
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
// stringToExpression is hidden because we have $ in GrpcDsl
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression
import io.grpc.Status

class GrpcExample extends Simulation {
  TestServer.startServer()
  TestServer.startEmptyServer()

  val grpcConf = grpc(managedChannelBuilder(name = "localhost", port = 8080).usePlaintext())
    .warmUpCall(ChatServiceGrpc.METHOD_GREET, GreetRequest.defaultInstance)
    .header(TokenHeaderKey, optional = true)($("token"))
  val emptyGrpc = grpc(managedChannelBuilder(name = "localhost", port = 9999).usePlaintext())

  val greetPayload: Expression[GreetRequest] = GreetRequest(name = "World").updateExpr(
    _.username :~ $("username")
  )

  val successfulCall = grpc("Success")
    .rpc(ChatServiceGrpc.METHOD_GREET)
    .payload(greetPayload)
    .extract(_.data.split(' ').headOption)(_ saveAs "s")

  val s = scenario("Example")
    .feed(csv("usernames.csv")) // the first two are duplicated to force an ALREADY_EXISTS error
    .exec(
      grpc("Expect UNAUTHENTICATED")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload(greetPayload)
        .check(
          statusCode is Status.Code.UNAUTHENTICATED,
          statusDescription.notExists,
          trailer(ErrorResponseKey) is CustomError("You are not authenticated!"),
          trailer(ErrorResponseKey).find(1).notExists,
          trailer(ErrorResponseKey).findAll is List(CustomError("You are not authenticated!")),
          trailer(ErrorResponseKey).count is 1,
          trailer(TokenHeaderKey).count is 0
        )
    )
    .exec(
      grpc("Register")
        .rpc(ChatServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.updateExpr(
          _.username :~ $("username")
        ))
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .exec(successfulCall)
    .exec(
      grpc("Empty server")
        .rpc(ChatServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance)
        // only one error
        .checkIf(_.userId == 10)(statusCode is Status.Code.ABORTED)
        .checkIf(_.userId != 10)(statusCode is Status.Code.UNIMPLEMENTED)
        .target(emptyGrpc)
    )
    .exec(
      grpc("Cannot Build")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload($("notDefined"))
        .check(
          // the extraction checks can be added inside .check
          // but the extraction functions need type annotation
          // so .extract and .extractMultiple are added to GrpcCallActionBuilder, see below
          extract((_: ChatMessage).username.some),
          extractMultiple((_: ChatMessage).data.split(' ').toSeq.some)
        )
    )
    .exec(
      grpc("Cannot Build, with header not found")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload(GreetRequest.defaultInstance)
        .header(TokenHeaderKey)($("notDefined"))
    )
    .exec(
      grpc("Trigger logging")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload(greetPayload)
        .check(statusCode is Status.Code.NOT_FOUND)
    )
    .repeat(1000) {
      exec(successfulCall)
        .exec(
          grpc("Expect PERMISSION_DENIED")
            .rpc(ChatServiceGrpc.METHOD_GREET)
            .payload(GreetRequest(username = "DoesNotExist"))
            .checkIf((res, _) => res.validation.toOption.isEmpty)(statusCode is Status.Code.PERMISSION_DENIED)
        )
        .exec(
          grpc("Use session")
            .rpc(ChatServiceGrpc.METHOD_GREET)
            .payload(
              GreetRequest.defaultInstance.updateExpr(
                _.name :~ $("s"),
                _.username :~ $("username")
              )
            )
            .extract(_.data.some)(_ is "Server says: Hello Server!")
        )
        .exec(
          grpc("Extract multiple")
            .rpc(ChatServiceGrpc.METHOD_GREET)
            .payload(greetPayload)
            .extractMultipleIf(_.userId <= 10)(_.data.split(' ').toSeq.some)(
              _.count is 4,
              _.find(10).notExists,
              _.find(2) is "Hello",
              _.findAll is List("Server", "says:", "Hello", "World!")
            )
            .extractMultipleIf((message, _) =>
              !message.status.isOk
            )(_.username.toSeq.some
            )( // this check should fail, but condition is not met, so no failure
              _.find(1) is 'u'
            )
        )
    }
    .exec(
      grpc("Extraction crash")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload(greetPayload)
        .extractIf(
          _.userId == 50)( // only 1 error
          _.data.split(' ')(10).some)( // This will crash, see _.find(10) above
          _.exists
        )
        .extractIf { (res, session) =>
          session.userId == 51 && // only 1 failure
            res.validation.toOption.get.data.startsWith("Server") // always true
        }(
          _.data.split(' ')(10).some)( // This will crash, see _.find(10) above
          _.exists
        )
    )

  setUp(
    s.inject(atOnceUsers(54))
  ).protocols(grpcConf)
}
