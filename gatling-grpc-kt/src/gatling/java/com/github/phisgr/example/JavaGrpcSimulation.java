package com.github.phisgr.example;

import com.github.phisgr.example.Chat.*;
import com.github.phisgr.gatling.kt.grpc.StaticGrpcProtocol;
import com.github.phisgr.gatling.kt.grpc.action.GrpcCallActionBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import kotlin.text.StringsKt;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import static com.github.phisgr.example.UtilKt.errorResponseKey;
import static com.github.phisgr.example.UtilKt.tokenHeaderKey;
import static com.github.phisgr.gatling.kt.ValidationKt.getOrThrow;
import static com.github.phisgr.gatling.kt.grpc.GrpcDsl.*;
import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Translated from {@link GrpcExample}
 * See also {@link KtGrpcSimulation}
 */
public class JavaGrpcSimulation extends Simulation {
    {
        TestServer.startServer(8080);
        TestServer.startEmptyServer();
    }

    static CheckBuilder.MultipleFind<CustomError> errorTrailer = trailer(errorResponseKey, CustomError.class);

    StaticGrpcProtocol grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())
            .warmUpCall(ChatServiceGrpc.getGreetMethod(), GreetRequest.getDefaultInstance())
            .headerEL(tokenHeaderKey, "#{token}", true, String.class);
    StaticGrpcProtocol emptyGrpc = grpc(ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext());

    Function<Session, GreetRequest> greetPayload = session -> GreetRequest.newBuilder()
            .setName("World")
            .setUsername(session.getString("username"))
            .build();

    GrpcCallActionBuilder<GreetRequest, ChatMessage> successfulCall = grpc("Success")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(greetPayload)
            .check(
                    extract((ChatMessage m) -> Arrays.stream(m.getData().split(" ")).findFirst().orElse(null)).saveAs("s")
            );

    CheckBuilder.MultipleFind<String> words = extractMultiple((ChatMessage m) -> Arrays.asList(m.getData().split(" ")), String.class);
    ScenarioBuilder scn = scenario("Example")
            .feed(csv("usernames.csv")) // the first two are duplicated to force an ALREADY_EXISTS error
            .exec(
                    grpc("Expect UNAUTHENTICATED")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .check(
                                    statusCode().is(Status.Code.UNAUTHENTICATED),
                                    statusDescription().notExists(),
                                    errorTrailer.is(CustomError.newBuilder().setMessage("You are not authenticated!").build()),
                                    errorTrailer.find(1).notExists(),
                                    errorTrailer.findAll().is(Collections.singletonList(
                                            CustomError.newBuilder().setMessage("You are not authenticated!").build()
                                    )),
                                    errorTrailer.count().is(1),
                                    trailer(tokenHeaderKey, String.class).count().is(0)
                            )
            )
            .exec(
                    grpc("Register")
                            .rpc(ChatServiceGrpc.getRegisterMethod())
                            .payload(session ->
                                    RegisterRequest.newBuilder()
                                            .setUsername(session.getString("username"))
                                            .build()
                            )
                            .check(extract(RegisterResponse::getToken).saveAs("token"))
            )
            .exitHereIfFailed()
            .exec(successfulCall)
            .exec(
                    grpc("Empty server")
                            .rpc(ChatServiceGrpc.getRegisterMethod())
                            .payload(RegisterRequest.getDefaultInstance())
                            // only one error
                            .checkIf(session -> session.userId() == 10).then(statusCode().is(Status.Code.ABORTED))
                            .checkIf(session -> session.userId() != 10).then(statusCode().is(Status.Code.UNIMPLEMENTED))
                            .target(emptyGrpc)
            )
            .exec(
                    grpc("Cannot Build")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload("#{notDefined}", GreetRequest.class)
                            .check(
                                    extract(ChatMessage::getUsername),
                                    words
                            )
            )
            .exec(
                    grpc("Cannot Build, with header not found")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .headerEL(tokenHeaderKey, "#{notDefined}", String.class)
            )
            .exec(
                    grpc("Trigger logging")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .check(statusCode().is(Status.Code.NOT_FOUND))
            )
            .repeat(1000)
            .on(
                    exec(successfulCall)
                            .exec(
                                    grpc("Expect PERMISSION_DENIED")
                                            .rpc(ChatServiceGrpc.getGreetMethod())
                                            .payload(GreetRequest.newBuilder().setUsername("DoesNotExist").build())
                                            .checkIf((res, session) -> res.validation().toOption().isEmpty())
                                            .then(statusCode().is(Status.Code.PERMISSION_DENIED))
                            )
                            .exec(
                                    grpc("Use session")
                                            .rpc(ChatServiceGrpc.getGreetMethod())
                                            .payload(session -> GreetRequest.newBuilder()
                                                    .setName(session.getString("s"))
                                                    .setUsername(session.getString("username"))
                                                    .build()
                                            )
                                            .check(
                                                    extract(ChatMessage::getData).is("Server says: Hello Server!")
                                            )
                            )
                            .exec(
                                    grpc("Extract multiple")
                                            .rpc(ChatServiceGrpc.getGreetMethod())
                                            .payload(greetPayload)
                                            .checkIf(session -> session.userId() <= 10)
                                            .then(
                                                    words.count().is(4),
                                                    words.find(10).notExists(),
                                                    words.find(2).is("Hello"),
                                                    words.findAll().is(Arrays.asList("Server", "says:", "Hello", "World!"))
                                            )
                                            .checkIf((res, session) -> !res.status().isOk())
                                            .then( // this check should fail, but condition is not met, so no failure
                                                    extractMultiple((ChatMessage m) -> StringsKt.toList(m.getUsername()), Character.class)
                                                            .find(1).is('u')
                                            )
                            )
            )
            .exec(
                    grpc("Extraction crash")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .checkIf(session -> session.userId() == 50L) // only 1 error here
                            .then(
                                    extract((ChatMessage m) -> m.getData().split(" ")[10]) // This will crash, see words.find(10) above
                                            .exists()
                            )
                            .checkIf((res, session) ->
                                    session.userId() == 51 && // only 1 error here
                                            getOrThrow(res.validation()).getData().startsWith("Server") // always true
                            )
                            .then(
                                    extract((ChatMessage m) -> m.getData().split(" ")[10]).exists()
                            )

            );

    {
        setUp(
                scn.injectOpen(atOnceUsers(54))
        ).protocols(grpcConf);
    }
}
