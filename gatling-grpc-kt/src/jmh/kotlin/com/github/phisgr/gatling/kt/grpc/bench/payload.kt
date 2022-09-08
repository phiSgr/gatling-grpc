package com.github.phisgr.gatling.kt.grpc.bench

import com.github.phisgr.example.Chat
import com.github.phisgr.example.ChatServiceGrpc
import com.github.phisgr.gatling.kt.grpc.*
import io.gatling.commons.validation.Validation
import io.gatling.core.session.Session
import scala.Function1

@JvmField
val inlined: Function1<Session, Validation<Chat.GreetRequest>> = grpc("blah")
    .rpc(ChatServiceGrpc.getGreetMethod())
    .payload { session ->
        Chat.GreetRequest.newBuilder()
            .setUsername(session.getString("username"))
            .setName(session.getString("name"))
            .build()
    }
    .asScala()
    .payload()

// shouldn't be different from the other one, but just to confirm
@JvmField
val withHelper: Function1<Session, Validation<Chat.GreetRequest>> = grpc("blah")
    .rpc(ChatServiceGrpc.getGreetMethod())
    .payload(Chat.GreetRequest::newBuilder) { session ->
        username = session.getString("username")
        name = session.getString("name")
        build()
    }
    .asScala()
    .payload()
