package com.github.phisgr.example

import com.github.phisgr.example.chat.{ChatMessage, ChatServiceGrpc}
import com.github.phisgr.gatling.grpc.Predef._
import io.gatling.core.Predef._
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper}
import io.grpc.{CallOptions, Status}

import scala.concurrent.duration._

class ClientStreamingExample extends Simulation {
  TestServer.startServer()

  val grpcConf = grpc(managedChannelBuilder(target = "localhost:8080").usePlaintext())
    .shareChannel
  val clientStream = grpc("Speak")
    .clientStream(ChatServiceGrpc.METHOD_BLACK_HOLE, "call")

  val send = clientStream.send(ChatMessage(data = "I'm bored"))
  val repeatSendCounter = "repeatSendCounter"

  val speaker = scenario("Speaker")
    .exec(
      clientStream.connect
        .extract(_.some)(_ lte 100)
    )
    .repeat(_.userId.toInt, repeatSendCounter) {
      doIfOrElse(clientStream.status.isCompleted) {
        exec { session =>
          println(s"Already completed: ${session.userId}")
          // ugly way to break
          session.set(repeatSendCounter, session.userId.toInt)
        }
      } {
        exec(send).pace(10.millis)
      }
    }
    .exec(send) // trigger debug log
    .exec(
      clientStream.copy(requestName = "Wrong Type")
        // it's now hard to send a wrong typed message by mistake
        .send("wrong type".expressionSuccess.asInstanceOf[Expression[ChatMessage]])
    )
    .exec(clientStream.completeAndWait)
    .exec(
      clientStream.connect
        .callOptions(CallOptions.DEFAULT.withDeadlineAfter(500, MILLISECONDS))
        // should be DEADLINE_EXCEEDED, but we want to trigger logging
        .check(statusCode is Status.Code.CANCELLED)
    )
    .doIfOrElse(_.userId <= 100) {
      exec(clientStream.completeAndWait)
    } {
      exec(clientStream.cancelStream)
    }

  setUp(speaker.inject(atOnceUsers(200)))
    .protocols(grpcConf)
    .assertions(global.failedRequests.count is 100)
}
