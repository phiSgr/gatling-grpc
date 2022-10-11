package com.github.phisgr.example

import com.github.phisgr.gatling.kt.*
import com.github.phisgr.gatling.kt.grpc.ClientStream
import com.github.phisgr.gatling.kt.grpc.grpc
import com.github.phisgr.gatling.kt.grpc.isCompleted
import com.github.phisgr.gatling.kt.grpc.statusCode
import com.google.protobuf.Empty
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Translated from [ClientStreamingExample]
 */
class KtClientStreamingSimulation : Simulation() {
    init {
        TestServer.startServer(8080)
        val grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())
            .shareChannel()
        val clientStream = grpc("Speak")
            .clientStream(ChatServiceGrpc.getBlackHoleMethod(), "call")

        val send = clientStream.send(Chat.ChatMessage.newBuilder().apply {
            data = "I'm bored"
        }.build())
        val repeatSendCounter = "repeatSendCounter"

        val speaker = scenario("Speaker") {
            +clientStream.connect()
                .check({ extract { it.value }.lte(100) })
            +repeat({ it.userId().toInt() }, repeatSendCounter).on {
                +doIfOrElse { clientStream.status(it).isCompleted() }.then {
                    +hook { session ->
                        println("Already completed: ${session.userId()}")
                        // ugly way to break
                        session.set(repeatSendCounter, session.userId().toInt())
                    }
                }.orElse {
                    +send
                    +pace(15.milliseconds.toJavaDuration())
                }
            }
            +send // trigger debug log

            @Suppress("UNCHECKED_CAST") // hard to send a wrong typed message by mistake
            +(clientStream.withRequestName("Wrong Type") as ClientStream<Empty, *>)
                .send(Empty.getDefaultInstance())
            +clientStream.completeAndWait()
            +clientStream.connect()
                .callOptions { CallOptions.DEFAULT.withDeadlineAfter(500, MILLISECONDS) }
                // should be DEADLINE_EXCEEDED, but we want to trigger logging
                .check(statusCode.shouldBe(Status.Code.CANCELLED))
            +doIfOrElse { it.userId() <= 100 }.then {
                +clientStream.completeAndWait()
            }.orElse {
                +clientStream.cancelStream()
            }
        }

        setUp(speaker.injectOpen(atOnceUsers(200)))
            .protocols(grpcConf)
            .assertions(global().failedRequests().count().shouldBe(100))
    }
}
