package com.github.phisgr.example

import com.github.phisgr.example.Chat.ChatMessage
import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.stream.TimestampExtractor
import com.github.phisgr.gatling.kt.*
import com.github.phisgr.gatling.kt.grpc.*
import com.google.protobuf.Empty
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.Simulation
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Translated from [StreamingExample]
 */
class KtStreamingSimulation : Simulation() {
    init {
        TestServer.startServer(8080)

        val sendMessageDuration = 55.seconds.toJavaDuration()
        val simulationDuration = 60.seconds.toJavaDuration()

        val maxSendDelay = 1.seconds.toJavaDuration()

        val lastMessageTillEnd = run {
            val raw = (simulationDuration - sendMessageDuration).toMillis()
            val allowedError = raw / 20
            (raw - allowedError)..(raw + allowedError + maxSendDelay.toMillis())
        }

        val timeExpression: Function<Session, Long> = Function { System.currentTimeMillis() }

        val listenCall = grpc("Listen")
            .serverStream(ChatServiceGrpc.getListenMethod(), streamName = "listener")
        val chatCall = grpc("Chat")
            .bidiStream(ChatServiceGrpc.getChatMethod(), streamName = "chatter")
        val complete = chatCall.withRequestName("Complete").complete()

        val grpcConf = grpc(ManagedChannelBuilder.forTarget("localhost:8080").usePlaintext())
            .shareChannel()

        val listener = scenario("Listener") {
            +listenCall
                .start(Empty.getDefaultInstance())
                .timestampExtractor { session, message, _ ->
                    if (session.userId() < 20) {
                        // Let's see how Gatling handles negative response times
                        message.time + ThreadLocalRandom.current().nextInt(-5, 5)
                    } else {
                        // Cut down log size by ignoring other virtual users
                        TimestampExtractor.IgnoreMessage()
                    }
                }
                .check(
                    { extract { it.username }.saveAs("prevUsername") },
                    { extract { it.time }.saveAs("prevTime") }
                )
                .sessionCombiner(SessionCombiner.pick("prevUsername", "prevTime"))
                .endCheckIf { session -> session.userId() < 20 }.then(
                    statusCode.shouldBe(Status.Code.OK)
                )
                .streamEndLog(logWhen = ERROR_ONLY)
            +repeat(5).on {
                +pause(10)
                +listenCall.withRequestName("Reconciliate").reconciliate()
                +peek { session ->
                    if (session.userId() == 100L) {
                        println("prevUsername is ${session.getString("prevUsername")}")
                    }
                }
                +listenCall.withRequestName("Reconciliate").reconciliate(waitFor = NEXT_MESSAGE)
                +peek { session ->
                    val diff = System.currentTimeMillis() - session.getLong("prevTime")

                    if (session.contains(listenCall.streamName)) {
                        require(diff <= 4) {
                            "This hook should be immediately run after receiving, " +
                                "which should not be long after sending. " +
                                "But diff is ${diff}ms."
                        }
                    } else { // stream has ended
                        require(lastMessageTillEnd.contains(diff)) {
                            "Last message sent should be around ${simulationDuration - sendMessageDuration} before simulation end." +
                                "But diff is ${diff}ms."
                        }
                    }
                }
            }
            +listenCall.cancelStream()
        }

        val chatter = scenario("Chatter") {
            +hook { it.set("username", UUID.randomUUID().toString()) }
            +chatCall.connect()
                .endCheckIf { res, _ -> !res.status().isOk }.then(
                    trailer(errorResponseKey).notExists()
                )
                .endCheck(statusCode.shouldBe(Status.Code.OK))

            // not used at all because of duplicated stream name
            +grpc("Already Exists")
                .bidiStream(ChatServiceGrpc.getChatMethod(), streamName = "chatter")
                .connect()
                .check({ extract { it.time }.gt(timeExpression) })
                .callOptions { CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.HOURS) }
                .timestampExtractor(TimestampExtractor.ignore())
                .sessionCombiner(SessionCombiner.NoOp())
                .streamEndLog(logWhen = NEVER)

            @Suppress("UNCHECKED_CAST") // hard to send a wrong typed message by mistake
            +(chatCall.withRequestName("Wrong Message") as BidiStream<Empty, *>)
                .send(Empty.getDefaultInstance())

            +grpc("Wrong Name") // chatCall.streamName is lower case
                .bidiStream(ChatServiceGrpc.getChatMethod(), streamName = "Chatter")
                .send(ChatMessage.getDefaultInstance())

            +during(sendMessageDuration).on {
                +pause(500.milliseconds.toJavaDuration(), maxSendDelay)
                +chatCall.send(ChatMessage::newBuilder) { session ->
                    username = session.getString("username")
                    data = "$username says hi!"
                    time = timeExpression.apply(session)
                    build()
                }.preSendAction { clock, req, _ ->
                    println("Time difference is ${clock.nowMillis() - req.time}ms.")
                }
            }
            +complete
            +doIf { session -> chatCall.status(session).isHalfClosed() }.then {
                +peek {
                    println("Confirmed to have half closed.")
                }
            }
            +chatCall.withRequestName("Send after complete").send(ChatMessage.getDefaultInstance())
            +complete
            +chatCall.withRequestName("Wait for end.").reconciliate(waitFor = STREAM_END)
            +peek { session ->
                require(!session.contains(chatCall.streamName))
            }
        }

        val failure = scenario("Failures") {
            +feed(listFeeder(listOf(true, false).map { mapOf("earlyStop" to it) }))
            +listenCall
                .withRequestName("Cannot build")
                .start(Empty.getDefaultInstance())
                .headerEL(tokenHeaderKey, "#{whatever}")
            +chatCall
                .withRequestName("Fail")
                .connect()
                .eventExtractor { session, _, _, message, receiveTime, statsEngine, _, status, errorMessage ->
                    if (ThreadLocalRandom.current().nextBoolean()) throw IllegalStateException()

                    // Exposing a Gatling's internal Scala class to Java/Kotlin is not ideal
                    // but it's not used enough to justify wrapper code
                    statsEngine.logResponse(
                        session.scenario(),
                        session.groups(),
                        "requestName is overridden",
                        message.time - 100,
                        receiveTime,
                        status,
                        scala.Option.empty(),
                        errorMessage
                    )
                }
                .check({
                    extract { it.data }
                        .validate("endsWith(hi)") { data, _ ->
                            if (data.endsWith("hi")) data else {
                                val actualEnding = data.split(' ').last()
                                failWith("ends with $actualEnding")
                            }
                        }
                })
                .endCheck(statusCode.shouldBe(Status.Code.CANCELLED))
                .endCheck(trailer(errorResponseKey).notExists())
            +pause(2)
            +doIf("#{earlyStop}").then {
                +chatCall.complete()
            }
        }


        setUp(
            chatter.injectOpen(atOnceUsers(10)),
            listener.injectOpen(rampUsers(100).during(30.seconds.toJavaDuration())),
            failure.injectOpen(atOnceUsers(2))
        ).protocols(grpcConf).maxDuration(simulationDuration).exponentialPauses()

    }
}
