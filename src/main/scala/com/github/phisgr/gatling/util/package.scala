package com.github.phisgr.gatling

import io.gatling.core.session.Expression
import java.lang.{StringBuilder => JStringBuilder}

import io.gatling.commons.util.StringHelper.Eol
import io.grpc.{Metadata, Status}

package object util {

  implicit class ExpressionZipping[A](val expression: Expression[A]) extends AnyVal {
    def zipWith[B, C](that: Expression[B])(f: (A, B) => C): Expression[C] = { session =>
      expression(session).flatMap(r1 => that(session).map(r2 => f(r1, r2)))
    }
  }

  implicit class SomeWrapper[T](val value: T) extends AnyVal {
    def some: Some[T] = Some(value)
  }


  implicit class GrpcStringBuilder(val buff: JStringBuilder) extends AnyVal {
    def appendWithEol(s: String): JStringBuilder =
      buff.append(s).append(Eol)

    def appendWithEol(o: Object): JStringBuilder =
      buff.append(o).append(Eol)

    def appendStatus(s: Status): JStringBuilder = {
      buff.append("status=").append(Eol)
        .append(s.getCode)
      if (s.getDescription != null) {
        buff.append(", description: ").append(s.getDescription)
      }
      if (s.getCause != null) {
        buff.append(Eol).append(s.getCause.toString)
      }
      buff.append(Eol)
    }

    def appendTrailers(trailers: Metadata): JStringBuilder = {
      // Would love to output them like HttpStringBuilder#appendHttpHeaders,
      // but Metadata does not allow iterating entries.
      buff.append("trailers= ").append(Eol).append(trailers).append(Eol)
    }
  }

}
