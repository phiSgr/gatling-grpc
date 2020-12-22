package com.github.phisgr.example

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, ThreadLocalRandom, TimeUnit}
import java.util.{Timer, TimerTask}

import com.github.phisgr.example.chat._
import com.github.phisgr.example.util._
import com.google.common.collect.Sets
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.StrictLogging
import io.grpc._
import io.grpc.health.v1.health.HealthCheckResponse.ServingStatus.SERVING
import io.grpc.health.v1.health.HealthGrpc.Health
import io.grpc.health.v1.health.{HealthCheckRequest, HealthCheckResponse, HealthGrpc}
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}

import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.util.Random._
import scala.util.Try
import scala.util.control.NonFatal

object TestServer extends StrictLogging {

  val accounts: collection.concurrent.Map[String, String] = new ConcurrentHashMap[String, String]().asScala
  val listeners = Sets.newConcurrentHashSet[ServerCallStreamObserver[ChatMessage]]()
  val messages = new LinkedBlockingQueue[ChatMessage]()
  val timer = new Timer

  def startServer(port: Int = 8080): Server = {
    val portMessage = if (port == 8080) "" else s"from port $port "

    {
      val thread = new Thread({ () =>
        while (true) {
          val message = messages.take()
          listeners.forEach { listener =>
            if (!listener.isCancelled) {
              try {
                listener.onNext(message)
              } catch {
                case NonFatal(e) =>
                  logger.warn("onNext failed", e)
              }
            }
          }
        }
      })
      thread.setDaemon(true)
      thread.start()
    }

    val greetService: ChatServiceGrpc.ChatService = new ChatServiceGrpc.ChatService {
      override def greet(request: GreetRequest): Future[ChatMessage] = Future.fromTry(Try {
        val token = Option(TokenContextKey.get).getOrElse {
          val trailers = new Metadata()
          trailers.put(ErrorResponseKey, CustomError("You are not authenticated!"))
          throw Status.UNAUTHENTICATED.asException(trailers)
        }

        val username = request.username
        if (!accounts.get(username).contains(token)) throw Status.PERMISSION_DENIED.asException()
        ChatMessage(username = username, data = s"Server ${portMessage}says: Hello ${request.name}!", time = System.currentTimeMillis())
      })

      override def register(request: RegisterRequest): Future[RegisterResponse] = Future.fromTry(Try {
        val token = ThreadLocalRandom.current().alphanumeric.take(10).mkString
        val success = accounts.putIfAbsent(request.username, token).isEmpty

        if (success) {
          RegisterResponse(
            username = request.username,
            token = token
          )
        } else {
          val trailers = new Metadata()
          trailers.put(ErrorResponseKey, CustomError("The username is already taken!"))
          throw Status.ALREADY_EXISTS.asException(trailers)
        }
      })

      override def chat(responseObserver: StreamObserver[ChatMessage]): StreamObserver[ChatMessage] = {
        addObserver(responseObserver)
        new StreamObserver[ChatMessage] {
          override def onNext(message: ChatMessage): Unit = {
            messages.put(message)
          }
          override def onError(t: Throwable): Unit = {
            listeners.remove(responseObserver)
            logger.warn("onError called", t)
          }
          override def onCompleted(): Unit = {
            listeners.remove(responseObserver)
            responseObserver.onCompleted()
          }
        }
      }

      override def listen(request: Empty, responseObserver: StreamObserver[ChatMessage]): Unit = {
        addObserver(responseObserver)
      }

      override def blackHole(responseObserver: StreamObserver[Int]): StreamObserver[ChatMessage] = {
        val start = System.currentTimeMillis()
        val maxTime = 1000
        val endTime = start + 1000

        @volatile
        var count = 0
        val closed = new AtomicBoolean()
        def close(): Unit = {
          if (closed.compareAndSet(false, true)) {
            responseObserver.onNext(count)
            responseObserver.onCompleted()
          }
        }

        timer.schedule(new TimerTask {
          override def run(): Unit = close()
        }, maxTime)

        new StreamObserver[ChatMessage] {
          override def onNext(value: ChatMessage): Unit = {
            if (System.currentTimeMillis() > endTime) {
              close()
            } else {
              count += 1
            }
          }
          override def onError(t: Throwable): Unit = ()
          override def onCompleted(): Unit = ()
        }
      }

      private def addObserver(responseObserver: StreamObserver[ChatMessage]): Unit = {
        val observer = responseObserver.asInstanceOf[ServerCallStreamObserver[ChatMessage]]
        listeners.add(observer)
        observer.setOnCancelHandler { () =>
          listeners.remove(observer)
        }
      }
    }

    // normally, it just adds the "token" header, if any, to the context
    // but for demo purpose, it fails the call with 0.001% chance
    val interceptor = new ServerInterceptor {
      override def interceptCall[ReqT, RespT](
        call: ServerCall[ReqT, RespT],
        headers: Metadata,
        next: ServerCallHandler[ReqT, RespT]
      ): ServerCall.Listener[ReqT] = {
        if (
          call.getMethodDescriptor.getFullMethodName == ChatServiceGrpc.METHOD_GREET.getFullMethodName &&
            ThreadLocalRandom.current().nextInt(100000) == 0
        ) {
          val trailers = new Metadata()
          trailers.put(ErrorResponseKey, CustomError("1 in 100,000 chance!"))
          call.close(Status.UNAVAILABLE.withDescription("You're unlucky."), trailers)
          new ServerCall.Listener[ReqT] {}
        } else {
          val context = Context.current()
          val newContext = Option(headers.get(TokenHeaderKey)).fold(context)(context.withValue(TokenContextKey, _))
          Contexts.interceptCall(newContext, call, headers, next)
        }

      }
    }

    val server = ServerBuilder.forPort(port)
      .addService(ChatServiceGrpc.bindService(greetService, scala.concurrent.ExecutionContext.global))
      .intercept(interceptor)
      .build.start
    logger.info(s"Server started, listening on $port")

    server
  }

  def startEmptyServer() = {
    val service = new Health {
      override def check(request: HealthCheckRequest): Future[HealthCheckResponse] =
        Future.successful(HealthCheckResponse(SERVING))
      override def watch(request: HealthCheckRequest, responseObserver: StreamObserver[HealthCheckResponse]): Unit =
        responseObserver.onError(Status.UNIMPLEMENTED.asRuntimeException())
    }
    ServerBuilder.forPort(9999)
      .addService(HealthGrpc.bindService(service, scala.concurrent.ExecutionContext.global))
      .build.start
  }

  def main(args: Array[String]): Unit = {
    val server = startServer()
    server.awaitTermination(10, TimeUnit.MINUTES)
  }
}
