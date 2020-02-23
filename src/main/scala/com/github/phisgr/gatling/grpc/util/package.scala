package com.github.phisgr.gatling.grpc

import java.lang.{StringBuilder => JStringBuilder}

import com.google.common.base.Charsets.US_ASCII
import io.gatling.commons.util.StringHelper.Eol
import io.grpc.{InternalMetadata, Metadata, Status}

package object util {

  implicit private[gatling] class GrpcStringBuilder(val buff: JStringBuilder) extends AnyVal {
    def appendWithEol(s: String): JStringBuilder =
      buff.append(s).append(Eol)

    def appendWithEol(o: Object): JStringBuilder =
      buff.append(o).append(Eol)

    def appendRequest(payload: Any, headers: Metadata): JStringBuilder = {
      val payloadString = payload match {
        case scalaPbObject: scalapb.GeneratedMessage =>
          scalaPbObject.toProtoString
        case _ => payload.toString // for Java Proto messages, this is the proto string
      }
      appendHeaders(headers)
      appendWithEol("payload=")
      appendWithEol(payloadString)
    }

    def appendResponse(body: Any, status: Status, trailers: Metadata): JStringBuilder = {
      appendStatus(status)
      appendTrailers(trailers)

      val bodyString = body match {
        case _ if null == body || !status.isOk =>
          null
        case scalaPbObject: scalapb.GeneratedMessage =>
          scalaPbObject.toProtoString
        case _ => body.toString // for Java Proto messages, this is the proto string
      }
      if (bodyString ne null) {
        buff
          .appendWithEol("body=")
          .appendWithEol(bodyString)
      }
      buff
    }

    private def appendStatus(s: Status): JStringBuilder = {
      buff.append("status=").append(Eol)
        .append(s.getCode)
      val description = s.getDescription
      if (description ne null) {
        buff.append(", description: ").appendWithEol(description)
      } else {
        buff.append(Eol)
      }
      val cause = s.getCause
      if (cause ne null) {
        buff.append("cause: ").appendWithEol(cause.toString)
      }
      buff
    }

    private def appendHeaders(headers: Metadata): JStringBuilder =
      appendMetadata(headers, "headers")

    private def appendTrailers(trailers: Metadata): JStringBuilder =
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

}
