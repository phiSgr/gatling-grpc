package com.github.phisgr.example

import com.github.phisgr.example.Chat.GreetRequest
import com.github.phisgr.gatling.kt.grpc.*
import com.github.phisgr.gatling.kt.javapb.fromSession
import com.github.phisgr.gatling.kt.on
import com.github.phisgr.gatling.kt.scenario
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.grpc.ManagedChannelBuilder

/**
 * Translated from [DynamicExample]
 */
class KtDynamicSimulation : Simulation() {

    init {
        ports.forEach(TestServer::startServer)

        val static1 = grpc(ManagedChannelBuilder.forAddress("localhost", 8081).usePlaintext())
        val static2 = grpc(ManagedChannelBuilder.forTarget("localhost:8082").usePlaintext()).shareChannel()

        val dynamic = dynamicChannel("target").forceParsing()
            .headerEL(tokenHeaderKey, "#{token}")

        val greetPayload = fromSession(GreetRequest::newBuilder) {
            name = "World"
            username = it.getString("username")
            build()
        }

        val s = scenario("Dynamic") {
            +feed(csv("usernames.csv").queue())
            +feed(listFeeder(ports.map { mapOf("port" to it) }).circular())
            +grpc("Register")
                .rpc(ChatServiceGrpc.getRegisterMethod())
                .payload(Chat.RegisterRequest::newBuilder) {
                    username = it.getString("username")
                    build()
                }
                .check({ extract { it.token }.saveAs("token") })
                .target(static1)
            +dynamic.setChannel { session ->
                // Imagine this comes from an API response
                val port = session.getInt("port")
                ManagedChannelBuilder.forTarget("localhost:$port").usePlaintext()
            }
            +exitHereIfFailed()
            +repeat(10).on {
                +grpc("Success")
                    .rpc(ChatServiceGrpc.getGreetMethod())
                    .payload(greetPayload)
                    .check({ extract { it.data.split(" ").getOrNull(3)?.toInt() }.isEL("#{port}") })
                    .target(dynamic)
            }
            +exitHereIfFailed()
            +grpc("Final one")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .headerEL(tokenHeaderKey, "#{token}")
                .target(static2)
            +dynamic.disposeChannel()
            // This will fail
            +dynamic.disposeChannel()
            +grpc("No Channel")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .target(dynamic)
        }

        // If all calls specify the target, no global default protocol is needed
        setUp(
            s.injectOpen(atOnceUsers(5))
        ).assertions(
            global().allRequests().count().shouldBe(11 * 3 + 5)
        )
    }
}
