package com.github.phisgr.example

import com.github.phisgr.example.util.`ClientSideLoadBalancingResolverProvider$`
import com.github.phisgr.gatling.kt.grpc.*
import com.github.phisgr.gatling.kt.hook
import com.github.phisgr.gatling.kt.javapb.fromSession
import com.github.phisgr.gatling.kt.on
import com.github.phisgr.gatling.kt.peek
import com.github.phisgr.gatling.kt.scenario
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.Simulation
import io.grpc.ManagedChannelBuilder
import io.grpc.NameResolverRegistry
import java.util.function.Function

/**
 * Translated from [ResolverExample]
 */
class KtResolverSimulation : Simulation() {
    init {
        ports.forEach(TestServer::startServer)

        NameResolverRegistry.getDefaultRegistry().register(`ClientSideLoadBalancingResolverProvider$`.`MODULE$`)

        val grpcConf = grpc(
            ManagedChannelBuilder.forTarget("my-resolver://name.resolver.example")
                .usePlaintext()
                .defaultLoadBalancingPolicy("round_robin")
        )
        val greetPayload: Function<Session, Chat.GreetRequest> = fromSession(Chat.GreetRequest::newBuilder) {
            name = "World"
            username = it.getString("username")
            build()
        }

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
            +repeat(30).on {
                +grpc("Success")
                    .rpc(ChatServiceGrpc.getGreetMethod())
                    .payload(greetPayload)
                    .headerEL(tokenHeaderKey, "#{token}")
                    .check({
                        extract { it.data.split(" ").getOrNull(3)?.toInt() }.saveAs("previousPort")
                    })
                +hook { session ->
                    val port = session.getInt("previousPort")
                    val count = session.getIntegerWrapper("count$port") ?: 0
                    session
                        .set("count$port", count + 1)
                        .remove("previousPort")
                }
            }
            +peek {
                ports.forEach { port ->
                    require(it.getInt("count$port") == 10)
                }
            }
            +exitHereIfFailed()
            +grpc("Final one")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .headerEL(tokenHeaderKey, "#{token}")
        }


        setUp(
            s.injectOpen(atOnceUsers(5))
        ).protocols(grpcConf).assertions(
            global().allRequests().count().shouldBe(31 * 3 + 5)
        )
    }
}
