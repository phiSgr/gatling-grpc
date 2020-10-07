package com.github.phisgr.gatling.grpc.protocol

import java.io.{ByteArrayInputStream, InputStream}

import io.grpc.KnownLength
import io.grpc.MethodDescriptor.Marshaller

import scala.reflect.io.Streamable

private[gatling] object EmptyMarshaller extends Marshaller[Unit] {
  override def stream(value: Unit): InputStream = new ByteArrayInputStream(Array())
  override def parse(stream: InputStream): Unit = {}
}

private[gatling] object ByteArrayMarshaller extends Marshaller[Array[Byte]] {
  override def stream(value: Array[Byte]): InputStream = new ByteArrayInputStream(value)
  override def parse(stream: InputStream): Array[Byte] = {
    val size = stream match {
      case knownLength: KnownLength => knownLength.available()
      case _ => -1
    }
    new Streamable.Bytes {
      override def inputStream(): InputStream = stream
      override def length: Long = size.toLong
    }.toByteArray()
  }
}
