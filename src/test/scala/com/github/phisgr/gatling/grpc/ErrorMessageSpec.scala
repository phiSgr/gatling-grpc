package com.github.phisgr.gatling.grpc

import com.github.phisgr.example.chat.ChatServiceGrpc
import com.github.phisgr.gatling.grpc.util.checkMethodDescriptor
import com.typesafe.scalalogging.StrictLogging
import io.grpc.MethodDescriptor.MethodType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorMessageSpec extends AnyFlatSpec with Matchers with StrictLogging {
  "Error message" should "be informative" in {
    checkMethodDescriptor(ChatServiceGrpc.METHOD_REGISTER, MethodType.UNARY)

    val exception = the[IllegalArgumentException] thrownBy {
      checkMethodDescriptor(ChatServiceGrpc.METHOD_LISTEN, MethodType.UNARY)
    }
    exception.getMessage shouldBe
      "requirement failed: Expected UNARY, but got example.ChatService/Listen with type SERVER_STREAMING. " +
        "You should use grpc(...).serverStream(...) for this method."
  }
}
