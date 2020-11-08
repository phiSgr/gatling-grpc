package com.github.phisgr.example

import com.github.phisgr.example.chat.{ChatMessage, ChatServiceGrpc}
import com.github.phisgr.gatling.grpc.Predef._
import io.gatling.core.Predef._
import io.grpc.{CallOptions, Status}

import scala.concurrent.duration._

class ClientStreamingExample extends Simulation {
  TestServer.startServer()

  val grpcConf = grpc(managedChannelBuilder(target = "localhost:8080").usePlaintext())
    .shareChannel
  val clientStream = grpc("Speak")
    .clientStream("call")

  val speaker = scenario("Speaker")
    .exec(
      clientStream.connect(ChatServiceGrpc.METHOD_BLACK_HOLE)
        .extract(_.some)(_ lte 100)
    )
    .repeat(_.userId.toInt) {
      pause(10.millis)
        .exec(clientStream.send(ChatMessage(data = "I'm bored")))
    }
    .exec(clientStream.copy(requestName = "Wrong Type").send("wrong type"))
    .exec(clientStream.completeAndWait)
    .exec(
      clientStream.connect(ChatServiceGrpc.METHOD_BLACK_HOLE)
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
