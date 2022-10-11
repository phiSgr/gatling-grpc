package com.github.phisgr.gatling.grpc

import com.google.common.base.Charsets.US_ASCII
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.Failure
import io.gatling.core.session.Session
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.{InternalMetadata, Metadata, Status}

import java.io.ByteArrayInputStream
import java.lang.{StringBuilder => JStringBuilder}
import scala.reflect.ClassTag

package object util {
  def toProtoString(message: Any): String = {
    message match {
      case scalaPbObject: scalapb.GeneratedMessage =>
        scalaPbObject.toProtoString
      case _ => message.toString // for Java Proto messages, this is the proto string
    }
  }

  implicit private[gatling] class GrpcStringBuilder(val buff: JStringBuilder) extends AnyVal {
    def appendMessage(message: Any): JStringBuilder =
      buff.appendWithEol(toProtoString(message))

    def appendWithEol(s: String): JStringBuilder =
      buff.append(s).append(Eol)

    def appendWithEol(o: Object): JStringBuilder =
      buff.append(o).append(Eol)

    def appendRequest(payload: Any, headers: Metadata): JStringBuilder = {
      appendHeaders(headers)
      appendWithEol("payload=")
      appendMessage(payload)
    }

    def appendSession(session: Session): JStringBuilder = {
      appendWithEol("Session:")
      appendWithEol(session)
    }

    def appendResponse(body: Any, status: Status, trailers: Metadata): JStringBuilder = {
      appendStatus(status)
      appendTrailers(trailers)

      if (null != body && status.isOk) {
        buff
          .appendWithEol("body=")
          .appendMessage(body)
      }
      buff
    }

    def appendStatus(s: Status): JStringBuilder = {
      buff.append("status=").append(Eol)
        .append(s.getCode)
      val description = s.getDescription
      if (description ne null) {
        buff.append(", description: ").append(description)
      }
      buff.append(Eol)

      val cause = s.getCause
      if (cause ne null) {
        buff.append("cause: ").appendWithEol(cause.toString)
      }
      buff
    }

    private def appendHeaders(headers: Metadata): JStringBuilder =
      appendMetadata(headers, "headers")

    def appendTrailers(trailers: Metadata): JStringBuilder =
      appendMetadata(trailers, "trailers")

    private def appendMetadata(metadata: Metadata, headersOrTrailers: String): JStringBuilder = {
      val size = InternalMetadata.headerCount(metadata)
      if (size != 0) {
        buff.append(headersOrTrailers).appendWithEol("=")

        var i = 0
        while (i < size) {
          val headerName = new String(Reflections.name(metadata, i), US_ASCII)
          buff.append(headerName).append(": ")

          val valueBytes = Reflections.value(metadata, i)
          val valueString = if (headerName.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
            InternalMetadata.BASE64_ENCODING_OMIT_PADDING.encode(valueBytes)
          } else {
            new String(valueBytes, US_ASCII)
          }
          buff.appendWithEol(valueString)

          i += 1
        }
      }
      buff
    }
  }

  private[gatling] def wrongTypeMessage[T: ClassTag](value: Any) = Failure(
    s"Value $value is of type ${value.getClass.getName}, expected ${implicitly[ClassTag[T]].runtimeClass.getName}"
  )

  private[gatling] def delayedParsing[Res](body: Any, responseMarshaller: Marshaller[Res]): Any = {
    if (responseMarshaller.eq(null) || null == body) {
      body
    } else {
      // does not support runtime change of logger level
      val rawBytes = body.asInstanceOf[Array[Byte]]
      responseMarshaller.parse(new ByteArrayInputStream(rawBytes))
    }
  }

}
