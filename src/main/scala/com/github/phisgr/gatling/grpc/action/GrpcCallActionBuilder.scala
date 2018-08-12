package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, ResponseExtract}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.check.{CheckBuilder, MultipleFindCheckBuilder, ValidatorCheckBuilder}
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext
import io.grpc.stub.AbstractStub
import io.grpc.{Channel, Metadata}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.util.Try

case class GrpcCallActionBuilder[Service <: AbstractStub[Service], Req, Res](
  requestName: String,
  stub: Channel => Service,
  fun: Service => Req => Future[Res],
  payload: Expression[Req],
  headers: List[HeaderPair[_]] = Nil,
  checks: List[GrpcCheck[Res]] = Nil
) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = GrpcCallAction(this, ctx, next)

  def header[T](key: Metadata.Key[T])(value: Expression[T]) = copy(
    headers = HeaderPair(key, value) :: headers
  )

  private def mapToList[T, U](s: Seq[T])(f: T => U) = s.map[U, List[U]](f)(breakOut)

  // If this method takes GrpcCheck[Res], type inference may not work
  // because of the implicit conversions like `checkBuilder2Check` needed
  def check(checks: CheckBuilder[GrpcCheck[Res], Try[Res], _, _]*) = copy(
    checks = this.checks ::: mapToList(checks)(_.build)
  )

  // In fact they can be added to checks using .check
  // but the type Res cannot be inferred there
  def extract[X](
    f: Res => Option[X])(
    ts: (ValidatorCheckBuilder[GrpcCheck[Res], Try[Res], Res, X] => GrpcCheck[Res])*
  ) = {
    val e = ResponseExtract.extract(f)
    copy(
      checks = checks ::: mapToList(ts)(_.apply(e))
    )
  }

  def exists[X](f: Res => Option[X]) = extract(f)(_.exists.build)

  def extractMultiple[X](
    f: Res => Option[Seq[X]])(
    ts: (MultipleFindCheckBuilder[GrpcCheck[Res], Try[Res], Res, X] => GrpcCheck[Res])*
  ) = {
    val e = ResponseExtract.extractMultiple[Res, X](f)
    copy(
      checks = checks ::: mapToList(ts)(_.apply(e))
    )
  }

}
