package com.github.phisgr.gatling.grpc.bench;

import com.github.phisgr.gatling.grpc.check.GrpcCheck;
import com.github.phisgr.gatling.pb.Test;

import static com.github.phisgr.gatling.kt.grpc.Check.extract;
import static com.github.phisgr.gatling.kt.grpc.internal.ChecksKt.build;

public class CheckJ {

    public static final String MESSAGE = "Hello!";

    public static final GrpcCheck<Test.SimpleMessage> check =
            build(extract(Test.SimpleMessage::getS).is(MESSAGE));
}
