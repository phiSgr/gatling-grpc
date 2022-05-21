package com.github.phisgr.example;

import com.github.phisgr.example.Chat.*;
import com.github.phisgr.gatling.kt.grpc.GrpcProtocol;
import com.github.phisgr.gatling.kt.grpc.action.GrpcCallActionBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.grpc.CallOptions;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.phisgr.gatling.kt.javapb.MessageUpdater.updateWith;
import static com.github.phisgr.gatling.kt.grpc.Check.*;
import static com.github.phisgr.gatling.kt.grpc.GrpcDsl.*;
import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Translated from {@link GrpcExample}
 * See also {@link GrpcExampleKt}
 */
public class GrpcExampleJ extends Simulation {
    public static void main(String[] args) { // sorry, sbt can't run it yet
        io.gatling.app.Gatling.main(new String[]{"-s", GrpcExampleJ.class.getName()});
    }

    {
        TestServer.startServer(8080);
        TestServer.startEmptyServer();
    }

    static Metadata.Key<String> tokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER);
    static Metadata.Key<CustomError> errorResponseKey = ProtoUtils.keyForProto(CustomError.getDefaultInstance());
    static CheckBuilder.MultipleFind<CustomError> errorTrailer = trailer(errorResponseKey, CustomError.class);

    GrpcProtocol grpcConf = grpc(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext())
            .warmUpCall(ChatServiceGrpc.getGreetMethod(), GreetRequest.getDefaultInstance())
            .header(tokenHeaderKey, true, "#{token}", String.class);
    GrpcProtocol emptyGrpc = grpc(ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext());

    Function<Session, GreetRequest> greetPayload = updateWith(GreetRequest.newBuilder().setName("World").build(), GreetRequest::toBuilder)
            .update(builder -> builder::setUsername, "#{username}", String.class);

    GrpcCallActionBuilder<GreetRequest, ChatMessage> successfulCall = grpc("Success")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(greetPayload)
            .check(
                    extract((ChatMessage m) -> Arrays.stream(m.getData().split(" ")).findFirst().orElse(null)).saveAs("s")
            );

    CheckBuilder.MultipleFind<String> words = extractMultiple((ChatMessage m) -> Arrays.asList(m.getData().split(" ")));
    ScenarioBuilder scn = scenario("Example")
            .feed(csv("usernames.csv"))
            .exec(
                    grpc("Expect UNAUTHENTICATED")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .callOptions(session -> CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.MINUTES))
                            .check(
                                    statusCode.is(Status.Code.UNAUTHENTICATED),
                                    statusDescription.notExists(),
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
                    grpc("Expect Register")
                            .rpc(ChatServiceGrpc.getRegisterMethod())
                            .payload(
                                    updateWith(RegisterRequest.getDefaultInstance(), RegisterRequest::toBuilder)
                                            .update(builder -> builder::setUsername, "#{username}", String.class)
                            )
                            .check(extract(RegisterResponse::getToken).saveAs("token"))
            )
            .exitHereIfFailed()
            .exec(successfulCall)
            .exec(
                    grpc("Empty server")
                            .rpc(ChatServiceGrpc.getRegisterMethod())
                            .payload(RegisterRequest.getDefaultInstance())
                            .checkIf(session -> session.userId() == 10).then(statusCode.is(Status.Code.ABORTED))
                            .checkIf(session -> session.userId() != 10).then(statusCode.is(Status.Code.UNIMPLEMENTED))
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
                            .header(tokenHeaderKey, "#{notDefined}", String.class)
            )
            .exec(
                    grpc("Trigger logging")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .check(statusCode.is(Status.Code.NOT_FOUND))
            )
            .repeat(1000)
            .on(
                    exec(successfulCall)
                            .exec(
                                    grpc("Expect PERMISSION_DENIED")
                                            .rpc(ChatServiceGrpc.getGreetMethod())
                                            .payload(GreetRequest.newBuilder().setUsername("DoesNotExist").build())
                                            .checkIf((res, session) -> res.validation().toOption().isEmpty())
                                            .then(statusCode.is(Status.Code.PERMISSION_DENIED))
                            )
                            .exec(
                                    grpc("Use session")
                                            .rpc(ChatServiceGrpc.getGreetMethod())
                                            .payload(
                                                    updateWith(GreetRequest.getDefaultInstance(), GreetRequest::toBuilder)
                                                            .update(b -> b::setName, "#{s}", String.class)
                                                            .update(b -> b::setUsername, "#{username}", String.class)
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
                                                    extractMultiple((ChatMessage m) -> m.getUsername().chars().mapToObj(c -> (char) c).collect(Collectors.toList()))
                                                            .find(1).is('u')
                                            )
                            )
            )
            .exec(
                    grpc("Extraction crash")
                            .rpc(ChatServiceGrpc.getGreetMethod())
                            .payload(greetPayload)
                            .checkIf(session -> session.userId() == 50L)
                            .then(
                                    extract((ChatMessage m) -> m.getData().split(" ")[10]) // This will crash, see _.find(10) above
                                            .exists()
                            )
                            .checkIf((res, session) ->
                                    session.userId() == 51 &&
                                            res.validation().toOption().get().getData().startsWith("Server")
                            )
                            .then(
                                    extract((ChatMessage m) -> m.getData().split(" ")[10]) // This will crash, see _.find(10) above
                                            .exists()
                            )

            );

    {

        setUp(
                scn.injectOpen(atOnceUsers(54))
        ).protocols(grpcConf);
    }
}
