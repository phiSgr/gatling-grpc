@file:JvmName("GrpcExampleKt")

package com.github.phisgr.example

import com.github.phisgr.example.Chat.*
import com.github.phisgr.gatling.kt.javapb.MessageUpdater.Companion.updateWith
import com.github.phisgr.gatling.kt.grpc.*
import io.gatling.app.Gatling
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.Simulation
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.protobuf.ProtoUtils
import java.util.function.Function


/**
 * Translated from [GrpcExample]
 * See also [GrpcExampleJ]
 */
class GrpcExampleKt : Simulation() {
    init {
        TestServer.startServer(8080)
        TestServer.startEmptyServer()
    }

    private val tokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
    private val errorResponseKey = ProtoUtils.keyForProto(CustomError.getDefaultInstance())

    private val grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())
        .warmUpCall(ChatServiceGrpc.getGreetMethod(), GreetRequest.getDefaultInstance())
        .header(tokenHeaderKey, true, "#{token}")

    private val emptyGrpc = grpc(ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext())

    private val greetPayload: Function<Session, GreetRequest> =
        GreetRequest.newBuilder().setName("World").build()
            .updateWith { it.toBuilder() }
            .update({ it::setUsername }, "#{username}")

    private val successfulCall = grpc("Success")
        .rpc(ChatServiceGrpc.getGreetMethod())
        .payload(greetPayload)
        .check({
            // note how we don't have to provide the type for m
            extract { m -> m.data.split(" ").getOrNull(0) }.saveAs("s")
        })

    private val words = extractMultiple { m: ChatMessage -> m.data.split(" ").toList() }

    private val s = scenario("Example")
        .feed(csv("usernames.csv"))
        .exec(
            grpc("Expect UNAUTHENTICATED")
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
        )
        .exec(
            grpc("Expect Register")
                .rpc(ChatServiceGrpc.getRegisterMethod())
                .payload(
                    RegisterRequest.getDefaultInstance().updateWith { it.toBuilder() }
                        .update({ it::setUsername }, "#{username}")
                )
                .check({ extract { it.token }.saveAs("token") })
        )
        .exitHereIfFailed()
        .exec(successfulCall)
        .exec(
            grpc("Empty server")
                .rpc(ChatServiceGrpc.getRegisterMethod())
                .payload(RegisterRequest.getDefaultInstance())
                .checkIf { session -> session.userId() == 10L }.then(statusCode.shouldBe(Status.Code.ABORTED))
                .checkIf { session -> session.userId() != 10L }.then(statusCode.shouldBe(Status.Code.UNIMPLEMENTED))
                .target(emptyGrpc)
        )
        .exec(
            grpc("Cannot Build")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload("#{notDefined}")
                .check(
                    { extract { it.username } },
                    { words }
                )
        )
        .exec(
            grpc("Cannot Build, with header not found")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .header(tokenHeaderKey, "#{notDefined}")
        )
        .exec(grpc("Trigger logging")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(greetPayload)
            .check(statusCode.shouldBe(Status.Code.NOT_FOUND))
        )
        .repeat(1000)
        .on(
            exec(successfulCall)
                .exec(
                    grpc("Expect PERMISSION_DENIED")
                        .rpc(ChatServiceGrpc.getGreetMethod())
                        .payload(GreetRequest.newBuilder().setUsername("DoesNotExist").build())
                        .checkIf { res, _ -> res.validation().toOption().isEmpty }
                        .then(statusCode.shouldBe(Status.Code.PERMISSION_DENIED))
                )
                .exec(
                    grpc("Use session")
                        .rpc(ChatServiceGrpc.getGreetMethod())
                        .payload(
                            GreetRequest.getDefaultInstance().updateWith(GreetRequest::toBuilder)
                                .update({ it::setName }, "#{s}")
                                .update({ b -> b::setUsername }, "#{username}")
                        )
                        .check(
                            { extract { it.data }.shouldBe("Server says: Hello Server!") }
                        )
                )

                .exec(
                    grpc("Extract multiple")
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
                        .then({// this check should fail, but condition is not met, so no failure
                            extractMultiple { it.username.toList() }.find(1).shouldBe('u')
                        })
                )
        )
        .exec(
            grpc("Extraction crash")
                .rpc(ChatServiceGrpc.getGreetMethod())
                .payload(greetPayload)
                .checkIf { session -> session.userId() == 50L }
                .then({
                    extract { it.data.split(" ")[10] } // This will crash, see _.find(10) above
                        .exists()
                })
                .checkIf { res, session ->
                    session.userId() == 51L &&
                        res.validation().toOption().get().data.startsWith("Server")
                }
                .then(
                    { extract { it.data.split(" ")[10] }.exists() }
                )

        )

    init {
        setUp(
            s.injectOpen(atOnceUsers(54))
        ).protocols(grpcConf)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // sorry, sbt can't run it yet
            Gatling.main(arrayOf("-s", GrpcExampleKt::class.java.name))
        }
    }
}
