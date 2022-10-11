package com.github.phisgr.example

import ch.qos.logback.classic.Level
import com.github.phisgr.example.chat.{ChatServiceGrpc, GreetRequest, RegisterRequest}
import com.github.phisgr.example.util.{ClientSideLoadBalancingResolverProvider, TokenHeaderKey, ports, tuneLogging}
import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.grpc.action.GrpcCallAction
import com.github.phisgr.gatling.pb._
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.grpc.NameResolverRegistry

class ResolverExample extends Simulation {
  ports.foreach(TestServer.startServer)

  tuneLogging(classOf[GrpcCallAction[_, _]], Level.TRACE)

  NameResolverRegistry.getDefaultRegistry.register(ClientSideLoadBalancingResolverProvider)

  val grpcConf = grpc(
    managedChannelBuilder(target = "my-resolver://name.resolver.example")
      .usePlaintext()
      .defaultLoadBalancingPolicy("round_robin")
  )

  val greetPayload: Expression[GreetRequest] = GreetRequest(name = "World")
    .updateExpr(_.username :~ $("username"))

  val s = scenario("Throttle")
    .feed(csv("usernames.csv").queue)
    .exec(
      grpc("Register")
        .rpc(ChatServiceGrpc.METHOD_REGISTER)
        .payload(RegisterRequest.defaultInstance.updateExpr(
          _.username :~ $("username")
        ))
        .extract(_.token.some)(_ saveAs "token")
    )
    .exitHereIfFailed
    .repeat(30) {
      exec(
        grpc("Success")
          .rpc(ChatServiceGrpc.METHOD_GREET)
          .payload(greetPayload)
          .header(TokenHeaderKey)($("token"))
          .extract(_.data.split(' ').lift(3).map(_.toInt))(_ saveAs "previousPort")
      ).exec { session: Session =>
        val port = session("previousPort").as[Int]
        val count = session(s"count$port").asOption[Int].getOrElse(0)
        session.set(s"count$port", count + 1).remove("previousPort")
      }
    }
    .exec { session =>
      ports.foreach { port =>
        require(session(s"count$port").as[Int] == 10)
      }
      session
    }
    .exitHereIfFailed
    .exec(
      grpc("Final one")
        .rpc(ChatServiceGrpc.METHOD_GREET)
        .payload(greetPayload)
        .header(TokenHeaderKey)($("token"))
    )

  setUp(
    s.inject(atOnceUsers(5))
  ).protocols(grpcConf).assertions(
    global.allRequests.count is (31 * 3 + 5)
  )
}
