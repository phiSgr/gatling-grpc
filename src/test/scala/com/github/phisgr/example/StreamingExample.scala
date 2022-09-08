package com.github.phisgr.example

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
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper}
import io.grpc.{CallOptions, Status}

import java.util.UUID
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
import scala.concurrent.duration._

class StreamingExample extends Simulation {
  TestServer.startServer()

  tuneLogging(classOf[GrpcProtocol], Level.INFO)

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
    .serverStream(ChatServiceGrpc.METHOD_LISTEN, streamName = "listener")
  val chatCall = grpc("Chat")
    .bidiStream(ChatServiceGrpc.METHOD_CHAT, streamName = "chatter")
  val complete = chatCall.copy(requestName = "Complete").complete

  val grpcConf = grpc(managedChannelBuilder(target = "localhost:8080").usePlaintext())
    .shareChannel

  val listener = scenario("Listener")
    .exec(
      listenCall
        .start(Empty.defaultInstance)
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
        .endCheckIf(_.userId < 20)(statusCode is Status.Code.OK)
        .streamEndLog(logWhen = ErrorOnly)
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

          if (session.contains(listenCall.streamName)) {
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
      chatCall.connect
        .endCheckIf((res, _) => !res.status.isOk)(trailer(ErrorResponseKey).notExists)
        .endCheck(statusCode is Status.Code.OK)
    )
    .exec(
      // for code coverage only
      // not used at all because of duplicated stream name
      grpc("Already Exists")
        .bidiStream(ChatServiceGrpc.METHOD_CHAT, streamName = "chatter")
        .connect
        .extract(_.time.some)(_ gt timeExpression)
        .callOptions(CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.HOURS))
        .timestampExtractor(TimestampExtractor.Ignore)
        .sessionCombiner(SessionCombiner.NoOp)
        .streamEndLog(logWhen = Never)
    )
    .exec(
      chatCall.copy(requestName = "Wrong Message")
        // it's now hard to send a wrong typed message by mistake
        .send(Empty.defaultInstance.expressionSuccess.asInstanceOf[Expression[ChatMessage]])
    )
    .exec(
      grpc("Wrong Name") // chatCall.streamName is lower case
        .bidiStream(ChatServiceGrpc.METHOD_CHAT, streamName = "Chatter")
        .send(ChatMessage.defaultInstance)
    )
    .during(sendMessageDuration) {
      pause(500.millis, maxSendDelay)
        .exec(
          chatCall.send(
            ChatMessage.defaultInstance.updateExpr(
              _.username :~ $("username"),
              _.data :~ "#{username} says hi!",
              _.time :~ timeExpression
            )
          )
        )
    }
    .exec(complete)
    .doIf(chatCall.status.isHalfClosed) {
      exec { session =>
        println("Confirmed to have half closed.")
        session
      }
    }
    .exec(chatCall.copy(requestName = "Send after complete").send(ChatMessage.defaultInstance))
    .exec(complete)
    .exec(chatCall.copy(requestName = "Wait for end.").reconciliate(waitFor = StreamEnd))
    .exec { session =>
      require(!session.contains(chatCall.streamName))
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
        .start(Empty.defaultInstance)
        .header(TokenHeaderKey)($("whatever"))
    )
    .exec(
      chatCall.copy("Fail")
        .connect
        .timestampExtractor { (_, message, _) =>
          if (ThreadLocalRandom.current().nextBoolean()) message.time - 100 else throw new IllegalStateException()
        }
        .extract(_.data.some)(_ validate endsWithHi)
        .endCheck(statusCode is Status.Code.CANCELLED)
        .endCheck(trailer(ErrorResponseKey).notExists)
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
