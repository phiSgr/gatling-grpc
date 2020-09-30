package com.github.phisgr.example

import java.util.UUID
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import com.github.phisgr.example.chat._
import com.github.phisgr.example.util.{ErrorResponseKey, TokenHeaderKey}
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.grpc.stream.{SessionCombiner, TimestampExtractor}
import com.github.phisgr.gatling.pb._
import com.google.protobuf.empty.Empty
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.grpc.{CallOptions, Status}

import scala.concurrent.duration._

class StreamingExample extends Simulation {
  TestServer.startServer()

  val timeExpression: Expression[Long] = { _ =>
    // pretend there's clock differences
    System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(-5, 5)
  }

  val listenCall = grpc("Listen")
    .serverStream(streamName = "listener")
  val chatCall = grpc("Chat")
    .bidiStream(streamName = "chatter")
  val complete = chatCall.copy("Complete").complete

  val grpcConf = grpc(managedChannelBuilder(target = "localhost:8080").usePlaintext())
    .shareChannel

  val listener = scenario("Listener")
    .exec(
      listenCall
        .start(ChatServiceGrpc.METHOD_LISTEN)(Empty.defaultInstance)
        .timestampExtractor { (session, message, _) =>
          if (session.userId < 200) message.time - 10 else TimestampExtractor.IgnoreMessage
        }
        .extract(_.username.some)(_ saveAs "previousUsername")
        .sessionCombiner(SessionCombiner.pick("previousUsername"))
        .endCheck(statusCode is Status.Code.OK)
    )
    .repeat(5) {
      pause(10.seconds)
        .exec(listenCall.copy(requestName = "Reconciliate").reconciliate)
        .exec { session =>
          if (session.userId == 100) {
            println(s"previousUsername is ${session.attributes.get("previousUsername")}")
          }
          session
        }
    }
    .exec(
      grpc("Listen")
        .serverStream(streamName = "listener")
        .cancelStream
    )
    .exec(
      listenCall
        .copy(requestName = "Cannot build")
        .start(ChatServiceGrpc.METHOD_LISTEN)(Empty.defaultInstance)
        .header(TokenHeaderKey)($("whatever"))
    )

  val chatter = scenario("Chatter")
    .exec(_.set("username", UUID.randomUUID().toString))
    .exec(
      chatCall.connect(ChatServiceGrpc.METHOD_CHAT)
        .endCheck(trailer(ErrorResponseKey).notExists)
        .endCheck(statusCode is Status.Code.OK)
    )
    .exec(
      // for code coverage only
      // not used at all because of duplicated stream name
      grpc("Already Exists")
        .bidiStream(streamName = "chatter")
        .connect(ChatServiceGrpc.METHOD_CHAT)
        .extract(_.time.some)(_ gt System.currentTimeMillis())
        .callOptions(CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.HOURS))
        .timestampExtractor(TimestampExtractor.Ignore)
        .sessionCombiner(SessionCombiner.NoOp)
    )
    .exec(
      grpc("Wrong Message")
        .bidiStream(streamName = "chatter")
        .send(Empty.defaultInstance)
    )
    .exec(
      grpc("Wrong Name")
        .bidiStream(streamName = "Chatter")
        .send(Empty.defaultInstance)
    )
    .during(55.seconds) {
      pause(500.millis, 1.second)
        .exec(
          chatCall.send(
            ChatMessage.defaultInstance.updateExpr(
              _.username :~ $("username"),
              _.data :~ "${username} says hi!",
              _.time :~ timeExpression
            )
          )
        )
    }
    .exec(complete)
    .exec(complete)

  val failure = scenario("Failures")
    .feed(List(true, false).map(b => Map("earlyStop" -> b)).iterator)
    .exec(chatCall.copy("Fail")
      .connect(ChatServiceGrpc.METHOD_CHAT)
      .timestampExtractor { (_, message, _) =>
        if (ThreadLocalRandom.current().nextBoolean()) message.time - 100 else throw new IllegalStateException()
      }
      .extract(_.data.some)(_ transform (_.endsWith("hi")) is true)
      .endCheck(statusCode is Status.Code.CANCELLED)
    )
    .pause(2.seconds)
    .doIf($("earlyStop")) {
      exec(chatCall.complete)
    }

  setUp(
    chatter.inject(atOnceUsers(10)),
    listener.inject(atOnceUsers(100), rampUsers(10000).during(1.minute)),
    failure.inject(atOnceUsers(1))
  ).protocols(grpcConf).maxDuration(1.minute).exponentialPauses
}
