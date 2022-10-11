package com.github.phisgr.gatling.bench

import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.javapb._
import com.github.phisgr.example.Chat.GreetRequest
import io.gatling.core.session.Expression

object JavaPbPayload {
  val payload: Expression[GreetRequest] = GreetRequest.getDefaultInstance
    .update(_.setUsername)($("username"))
    .update(_.setName)($("name"))
}
