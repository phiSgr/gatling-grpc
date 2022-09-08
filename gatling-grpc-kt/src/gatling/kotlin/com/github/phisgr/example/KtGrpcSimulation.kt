@file:JvmName("GrpcExampleKt")

package com.github.phisgr.example

import com.github.phisgr.example.Chat.*
import com.github.phisgr.gatling.kt.getOrThrow
import com.github.phisgr.gatling.kt.grpc.*
import com.github.phisgr.gatling.kt.javapb.fromSession
import com.github.phisgr.gatling.kt.on
import com.github.phisgr.gatling.kt.scenario
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.Simulation
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import java.util.function.Function

/**
 * Translated from [GrpcExample]
 * See also [JavaGrpcSimulation]
 */
class KtGrpcSimulation : Simulation() {
    init {
        TestServer.startServer(8080)
        TestServer.startEmptyServer()
    }

    private val grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())
        .warmUpCall(ChatServiceGrpc.getGreetMethod(), GreetRequest.getDefaultInstance())
        .headerEL(tokenHeaderKey, "#{token}", optional = true)

    private val emptyGrpc = grpc(ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext())

    private val greetPayload: Function<Session, GreetRequest> = fromSession(GreetRequest::newBuilder) { session ->
        name = "World"
        username = session.getString("username")
        build()
    }

    private val successfulCall = grpc("Success")
        .rpc(ChatServiceGrpc.getGreetMethod())
        .payload(greetPayload)
        .check({
            // because of the surrounding context, we don't have to provide the type for message
            extract { message -> message.data.split(" ").firstOrNull() }.saveAs("s")
        })

    // Here without the context, the type has to be provided
    private val words = extractMultiple { m: ChatMessage -> m.data.split(" ").toList() }

    private val s = scenario("Example") {
        +feed(csv("usernames.csv")) // the first two are duplicated to force an ALREADY_EXISTS error
        +grpc("Expect UNAUTHENTICATED")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(greetPayload)
            .check(
                statusCode.shouldBe(Status.Code.UNAUTHENTICATED),
                statusDescription.notExists(),
                trailer(errorResponseKey)
                    .shouldBe(CustomError.newBuilder().setMessage("You are not authenticated!").build()),
                trailer(errorResponseKey).find(1).notExists(),
                trailer(errorResponseKey).findAll()
                    .shouldBe(listOf(CustomError.newBuilder().setMessage("You are not authenticated!").build())),
                trailer(errorResponseKey).count().shouldBe(1),
                trailer(tokenHeaderKey).count().shouldBe(0)
            )
        +grpc("Register")
            .rpc(ChatServiceGrpc.getRegisterMethod())
            .payload(RegisterRequest::newBuilder) {
                username = it.getString("username")
                build()
            }
            .check({ extract { it.token }.saveAs("token") })
        +exitHereIfFailed()
        +successfulCall
        +grpc("Empty server")
            .rpc(ChatServiceGrpc.getRegisterMethod())
            .payload(RegisterRequest.getDefaultInstance())
            // only one error
            .checkIf { session -> session.userId() == 10L }.then(statusCode.shouldBe(Status.Code.ABORTED))
            .checkIf { session -> session.userId() != 10L }.then(statusCode.shouldBe(Status.Code.UNIMPLEMENTED))
            .target(emptyGrpc)
        +grpc("Cannot Build")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload("#{notDefined}")
            .check(
                { extract { it.username } },
                { words }
            )
        +grpc("Cannot Build, with header not found")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(greetPayload)
            .headerEL(tokenHeaderKey, "#{notDefined}")
        +grpc("Trigger logging")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(greetPayload)
            .check(statusCode.shouldBe(Status.Code.NOT_FOUND))
        repeat(1000).on {
            +successfulCall
            +grpc("Expect PERMISSION_DENIED")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(GreetRequest.newBuilder().setUsername("DoesNotExist").build())
                .checkIf { res, _ -> res.validation().toOption().isEmpty }
                .then(statusCode.shouldBe(Status.Code.PERMISSION_DENIED))
            +grpc("Use session")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(GreetRequest::newBuilder) {
                    name = it.getString("s")
                    username = it.getString("username")
                    build()
                }
                .check(
                    { extract { it.data }.shouldBe("Server says: Hello Server!") }
                )
            +grpc("Extract multiple")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .checkIf { session -> session.userId() <= 10 }
                .then(
                    words.count().shouldBe(4),
                    words.find(10).notExists(),
                    words.find(2).shouldBe("Hello"),
                    words.findAll().shouldBe(listOf("Server", "says:", "Hello", "World!"))
                )
                .checkIf { res, _ -> !res.status().isOk }
                .then({ // this check should fail, but condition is not met, so no failure
                    extractMultiple { it.username.toList() }.find(1).shouldBe('u')
                })
            +grpc("Extraction crash")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .checkIf { session -> session.userId() == 50L } // only 1 error here
                .then({
                    extract { it.data.split(" ")[10] } // This will crash, see words.find(10) above
                        .exists()
                })
                .checkIf { res, session ->
                    session.userId() == 51L && // only 1 error here
                        res.validation().getOrThrow().data.startsWith("Server") // always true
                }
                .then(
                    { extract { it.data.split(" ")[10] }.exists() }
                )
        }
    }

    init {
        setUp(
            s.injectOpen(atOnceUsers(54))
        ).protocols(grpcConf)
    }
}
