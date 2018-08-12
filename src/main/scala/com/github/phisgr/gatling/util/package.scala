package com.github.phisgr.gatling

import io.gatling.core.session.Expression

package object util {

  implicit class ExpressionZipping[A](val expression: Expression[A]) extends AnyVal {
    def zipWith[B, C](that: Expression[B])(f: (A, B) => C): Expression[C] = { session =>
      expression(session).flatMap(r1 => that(session).map(r2 => f(r1, r2)))
    }
  }

  implicit class SomeWrapper[T](val value: T) extends AnyVal {
    def some: Some[T] = Some(value)
  }

}
