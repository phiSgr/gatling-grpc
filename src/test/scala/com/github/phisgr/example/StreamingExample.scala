package com.github.phisgr.example

import java.util.UUID
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import ch.qos.logback.classic.Level
import com.github.phisgr.example.chat._
import com.github.phisgr.example.util.{ErrorResponseKey, TokenHeaderKey, tuneLogging}
import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import com.github.phisgr.gatling.grpc.stream.TimestampExtractor
import com.github.phisgr.gatling.pb._
import com.google.protobuf.empty.Empty
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.Predef._
import io.gatling.core.check.Matcher
import io.gatling.core.session.Expression
import io.grpc.{CallOptions, Status}

import scala.concurrent.duration._

class StreamingExample extends Simulation {
  TestServer.startServer()

  tuneLogging(classOf[GrpcProtocol].getName, Level.INFO)

  val sendMessageDuration = 55.seconds
  val simulationDuration = 60.seconds

  val maxSendDelay = 1.second

  val lastMessageTillEnd = {
    val raw = (simulationDuration - sendMessageDuration).toMillis
    val allowedError = raw / 20
    (raw - allowedError) to (raw + allowedError + maxSendDelay.toMillis)
  }

  val timeExpression: Expression[Long] = { _ => System.currentTimeMillis() }

  val listenCall = grpc("Listen")
    .serverStream(streamName = "listener")
  val chatCall = grpc("Chat")
    .bidiStream(streamName = "chatter")
  val complete = chatCall.copy(requestName = "Complete").complete

  val grpcConf = grpc(managedChannelBuilder(target = "localhost:8080").usePlaintext())
    .shareChannel

  val listener = scenario("Listener")
    .exec(
      listenCall
        .start(ChatServiceGrpc.METHOD_LISTEN)(Empty.defaultInstance)
        .timestampExtractor { (session, message, _) =>
          if (session.userId < 20) {
            // Let's see how Gatling handles negative response times
            message.time + ThreadLocalRandom.current().nextInt(-5, 5)
          } else {
            // Cut down log size by ignoring other virtual users
            TimestampExtractor.IgnoreMessage
          }
        }
        .extract(_.username.some)(_ saveAs "prevUsername")
        .extract(_.time.some)(_ saveAs "prevTime")
        .sessionCombiner(SessionCombiner.pick("prevUsername", "prevTime"))
        .endCheck(statusCode is Status.Code.OK)
    )
    .repeat(5) {
      pause(10.seconds)
        .exec(listenCall.copy(requestName = "Reconciliate").reconciliate)
        .exec { session =>
          if (session.userId == 100) {
            println(s"prevUsername is ${session.attributes.get("prevUsername")}")
          }
          session
        }
        .exec(listenCall.copy(requestName = "Reconciliate").reconciliate(waitFor = NextMessage))
        .exec { session =>
          val diff = System.currentTimeMillis() - session.attributes("prevTime").asInstanceOf[Long]

          if (session.attributes.contains(listenCall.streamName)) {
            require(
              diff <= 4,
              "This hook should be immediately run after receiving, " +
                "which should not be long after sending. " +
                s"But diff is ${diff}ms."
            )
          } else { // stream has ended
            require(
              lastMessageTillEnd.contains(diff),
              s"Last message sent should be around ${simulationDuration - sendMessageDuration} before simulation end." +
                s"But diff is ${diff}ms."
            )
          }
          session
        }
    }
    .exec(listenCall.cancelStream)

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
        .extract(_.time.some)(_ gt timeExpression)
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
    .during(sendMessageDuration) {
      pause(500.millis, maxSendDelay)
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
    .exec(chatCall.copy(requestName = "Send after complete").send(ChatMessage.defaultInstance))
    .exec(complete)
    .exec(chatCall.copy(requestName = "Wait for end.").reconciliate(waitFor = StreamEnd))
    .exec { session =>
      require(!session.attributes.contains(chatCall.streamName))
      session
    }


  val endsWithHi: Matcher[String] = new Matcher[String] {
    override protected def doMatch(actual: Option[String]): Validation[Option[String]] =
      if (actual.exists(_.endsWith("hi"))) actual else {
        val actualEnding = actual.map(_.split(' ').last)
        Failure(s"ends with $actualEnding")
      }

    override def name: String = "endsWith(hi)"
  }
  val failure = scenario("Failures")
    .feed(List(true, false).map(b => Map("earlyStop" -> b)).iterator)
    .exec(
      listenCall
        .copy(requestName = "Cannot build")
        .start(ChatServiceGrpc.METHOD_LISTEN)(Empty.defaultInstance)
        .header(TokenHeaderKey)($("whatever"))
    )
    .exec(
      chatCall.copy("Fail")
        .connect(ChatServiceGrpc.METHOD_CHAT)
        .timestampExtractor { (_, message, _) =>
          if (ThreadLocalRandom.current().nextBoolean()) message.time - 100 else throw new IllegalStateException()
        }
        .extract(_.data.some)(_ validate endsWithHi)
        .endCheck(statusCode is Status.Code.CANCELLED)
    )
    .pause(2.seconds)
    .doIf($("earlyStop")) {
      exec(chatCall.complete)
    }

  setUp(
    chatter.inject(atOnceUsers(10)),
    listener.inject(rampUsers(100).during(30.seconds)),
    failure.inject(atOnceUsers(2))
  ).protocols(grpcConf).maxDuration(simulationDuration).exponentialPauses
}
