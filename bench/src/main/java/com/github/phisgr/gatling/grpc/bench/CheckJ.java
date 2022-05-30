package com.github.phisgr.gatling.grpc.bench;

import com.github.phisgr.gatling.grpc.check.GrpcCheck;
import com.github.phisgr.gatling.kt.grpc.action.GrpcCallActionBuilder;
import com.github.phisgr.gatling.pb.Test;
import scala.collection.immutable.List;

import static com.github.phisgr.gatling.kt.grpc.Check.extract;
import static com.github.phisgr.gatling.kt.grpc.internal.ChecksKt.build;

public class CheckJ {

    public static final String MESSAGE = "Hello!";

    public static final GrpcCallActionBuilder<Test.SimpleMessage, Test.SimpleMessage> dummyBuilder = new GrpcCallActionBuilder<>(
            com.github.phisgr.gatling.grpc.action.GrpcCallActionBuilder.apply(
                    null, null, null, null, List.<GrpcCheck<Test.SimpleMessage>>newBuilder().result(), false
            )
    );

    public static final GrpcCheck<Test.SimpleMessage> check =
            build(extract(Test.SimpleMessage::getS).is(MESSAGE));
    public static final GrpcCheck<Test.SimpleMessage> checkIf = dummyBuilder
            .checkIf(session -> session.userId() == 0L)
            .then(extract(Test.SimpleMessage::getS).is(MESSAGE))
            .asScala()
            .checks()
            .head();
}
