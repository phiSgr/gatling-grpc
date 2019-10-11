package com.github.phisgr.example

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.logging.Logger

import com.github.phisgr.example.greet._
import com.github.phisgr.example.util._
import io.grpc._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Random, Try}

object TestServer {
  def startServer(): Server = {

    val logger = Logger.getLogger("TestServer")

    val accounts: collection.concurrent.Map[String, String] = new ConcurrentHashMap[String, String]().asScala

    val greetService = new GreetServiceGrpc.GreetService {
      override def greet(request: HelloWorld) = Future.fromTry(Try {
        val token = Option(TokenContextKey.get).getOrElse(throw Status.UNAUTHENTICATED.asException)

        val username = request.username
        if (!accounts.get(username).contains(token)) throw Status.PERMISSION_DENIED.asException()
        ChatMessage(username = username, data = s"Server says: Hello ${request.name}!")
      })

      override def register(request: RegisterRequest) = Future.fromTry(Try {
        val token = new Random().alphanumeric.take(10).mkString
        val success = accounts.putIfAbsent(request.username, token).isEmpty

        if (success) {
          RegisterResponse(
            username = request.username,
            token = token
          )
        } else throw Status.ALREADY_EXISTS.asException()
      })
    }

    // normally, it just adds the "token" header, if any, to the context
    // but for demo purpose, it fails the call with 1% chance
    val interceptor = new ServerInterceptor {
      override def interceptCall[ReqT, RespT](
        call: ServerCall[ReqT, RespT], headers: Metadata,
        next: ServerCallHandler[ReqT, RespT]
      ): ServerCall.Listener[ReqT] = {
        if (new Random().nextInt(100) == 0) {
          call.close(Status.UNAVAILABLE, new Metadata())
          new ServerCall.Listener[ReqT] {}
        } else {
          val context = Context.current()
          val newContext = Option(headers.get(TokenHeaderKey)).fold(context)(context.withValue(TokenContextKey, _))
          Contexts.interceptCall(newContext, call, headers, next)
        }

      }
    }

    val port = 8080
    val server = ServerBuilder.forPort(port)
      .addService(GreetServiceGrpc.bindService(greetService, scala.concurrent.ExecutionContext.global))
      .intercept(interceptor)
      .build.start
    logger.info("Server started, listening on " + port)

    server
  }

  def startEmptyServer() = {
    ServerBuilder.forPort(9999)
      .build.start
  }

  def main(args: Array[String]): Unit = {
    val server = startServer()
    server.awaitTermination(10, TimeUnit.MINUTES)
  }
}
