package com.github.phisgr.example

import com.github.phisgr.gatling.kt.grpc.*
import com.github.phisgr.gatling.kt.javapb.fromSession
import com.github.phisgr.gatling.kt.on
import com.github.phisgr.gatling.kt.scenario
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.Simulation
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.Status.Code.DEADLINE_EXCEEDED
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Translated from [ThrottleExample]
 */
class KtThrottleSimulation : Simulation() {
    init {
        TestServer.startServer(8080)

        val grpcConf = grpc(ManagedChannelBuilder.forTarget("localhost:8080").usePlaintext())
            .shareChannel()
            .disableWarmUp()
            .forceParsing()

        val greetPayload: Function<Session, Chat.GreetRequest> = fromSession(Chat.GreetRequest::newBuilder) {
            name = "World"
            username = it.getString("username")
            build()
        }

        /**
         * This instance of CallOptions is created at the start of the simulation.
         * It will be expired by the time it is used.
         */
        val callOptionsWithFixedDeadline = CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.SECONDS)

        val s = scenario("Throttle") {
            +feed(csv("usernames.csv").queue())
            +grpc("Register")
                .rpc(ChatServiceGrpc.getRegisterMethod())
                .payload(Chat.RegisterRequest::newBuilder) {
                    username = it.getString("username")
                    build()
                }
                .check({ extract { it.token }.saveAs("token") })
            +exitHereIfFailed()
            +repeat(60).on {
                +grpc("Success")
                    .rpc(ChatServiceGrpc.getGreetMethod())
                    .payload(greetPayload)
                    .headerEL(tokenHeaderKey, "#{token}")
                    .check({
                        extract { it.data.split(" ").firstOrNull() }.saveAs("s")
                    })
                +grpc("With deadline")
                    .rpc(ChatServiceGrpc.getGreetMethod())
                    .payload(greetPayload)
                    .headerEL(tokenHeaderKey, "#{token}")
                    // No call-by-name (autoclosure in Swift) syntax in Kotlin
                    // it's clear whether the expression is evaluated every time
                    .callOptions { CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.SECONDS) }
                +grpc("Fixed deadline")
                    .rpc(ChatServiceGrpc.getGreetMethod())
                    .payload(greetPayload)
                    .silent()
                    .headerEL(tokenHeaderKey, "#{token}")
                    .callOptions { callOptionsWithFixedDeadline }
                    .check(statusCode.shouldBe(DEADLINE_EXCEEDED))
            }
        }

        setUp(
            s.injectOpen(atOnceUsers(10))
                .throttle(
                    reachRps(50).during(10.seconds.toJavaDuration()),
                    holdFor(10.minutes.toJavaDuration())
                )
        ).protocols(grpcConf)
    }
}
