package com.github.phisgr.gatling.kt.grpc.bench;

import com.github.phisgr.example.Chat.GreetRequest;
import com.github.phisgr.example.ChatServiceGrpc;
import com.github.phisgr.gatling.bench.JavaPbPayload;
import io.gatling.commons.validation.Success;
import io.gatling.commons.validation.Validation;
import io.gatling.core.session.Session;
import io.gatling.core.session.Session$;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import scala.Function1;

import java.util.concurrent.TimeUnit;

import static com.github.phisgr.gatling.kt.grpc.GrpcDsl.grpc;

@Fork(jvmArgsAppend = {"-XX:MaxInlineLevel=20", "-XX:MaxTrivialSize=12"})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class TestPayload {

    public static Session session = Session$.MODULE$.apply(
                    "Scenario",
                    1L,
                    null // irrelevant in this test
            )
            .set("username", "username")
            .set("name", "name");

    public static final Function1<Session, Validation<GreetRequest>> java = grpc("blah")
            .rpc(ChatServiceGrpc.getGreetMethod())
            .payload(session -> GreetRequest.newBuilder()
                    .setUsername(session.getString("username"))
                    .setName(session.getString("name"))
                    .build())
            .asScala()
            .payload();

    public static final Function1<Session, Validation<GreetRequest>> scala = JavaPbPayload.payload();

    @Benchmark
    public Validation<GreetRequest> scala() {
        return scala.apply(session);
    }

    @Benchmark
    public Validation<GreetRequest> java() {
        return java.apply(session);
    }

    @Benchmark
    public Validation<GreetRequest> kotlinInlined() {
        return PayloadKt.inlined.apply(session);
    }

    @Benchmark
    public Validation<GreetRequest> kotlinHelper() {
        return PayloadKt.withHelper.apply(session);
    }

    @Benchmark
    public Validation<GreetRequest> baseline() {
        return Success.apply(
                GreetRequest.newBuilder()
                        .setUsername((String) session.attributes().apply("username"))
                        .setName((String) session.attributes().apply("name"))
                        .build()
        );
    }
}
