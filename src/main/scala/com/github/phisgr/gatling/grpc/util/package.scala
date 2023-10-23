package com.github.phisgr.gatling.grpc

import com.google.common.base.Charsets.US_ASCII
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.session.Session
import io.gatling.core.session.el.ElMessages
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.{InternalMetadata, Metadata, MethodDescriptor, Status}

import java.io.ByteArrayInputStream
import java.lang.{StringBuilder => JStringBuilder}

package object util {
  // pre-allocates the `Some` wrappers
  val statusCodeOption: Array[Some[String]] = {
    val codes = Status.Code.values()
    val res = new Array[Some[String]](codes.size)
    codes.foreach { code =>
      res(code.value()) = Some(code.toString)
    }
    res
  }

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

  private[gatling] def wrongTypeMessage(value: Any, clazz: Class[_]) = Failure(
    s"Value $value is of type ${value.getClass.getName}, expected ${clazz.getName}"
  )

  private[gatling] def getFromSession[T](clazz: Class[_], session: Session, name: String): Validation[T] = {
    session.attributes.get(name) match {
      case Some(value) =>
        if (clazz.isInstance(value)) Success(value.asInstanceOf[T]) else wrongTypeMessage(value, clazz)
      case None => ElMessages.undefinedSessionAttribute(name)
    }
  }

  private[gatling] def delayedParsing[Res](body: Any, responseMarshaller: Marshaller[Res]): Any = {
    if (responseMarshaller.eq(null) || null == body) {
      body
    } else {
      // does not support runtime change of logger level
      val rawBytes = body.asInstanceOf[Array[Byte]]
      responseMarshaller.parse(new ByteArrayInputStream(rawBytes))
    }
  }

  private[gatling] def checkMethodDescriptor(method: MethodDescriptor[_, _], expected: MethodDescriptor.MethodType): Unit = {
    require(method.getType == expected,
      (method.getType match {
        case MethodDescriptor.MethodType.UNARY => Some("rpc")
        case MethodDescriptor.MethodType.SERVER_STREAMING => Some("serverStream")
        case MethodDescriptor.MethodType.BIDI_STREAMING => Some("bidiStream")
        case MethodDescriptor.MethodType.CLIENT_STREAMING => Some("clientStream")
        case MethodDescriptor.MethodType.UNKNOWN => None
      }) match {
        case Some(builderName) =>
          s"Expected $expected, but got ${method.getFullMethodName} with type ${method.getType}. " +
            s"You should use grpc(...).$builderName(...) for this method."
        case None =>
          "Unknown method type"
      }
    )
  }

}
