package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.SuccessWrapper
import io.gatling.core.check.{CheckMaterializer, CountCriterionExtractor, DefaultMultipleFindCheckBuilder, Extractor, FindAllCriterionExtractor, FindCriterionExtractor, Preparer}
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper}
import io.grpc.Metadata

import scala.collection.JavaConverters._

private[gatling] object TrailersExtract {

  def trailer[T](key: Metadata.Key[T]): DefaultMultipleFindCheckBuilder[TrailersExtract, Metadata, T] =
    new DefaultMultipleFindCheckBuilder[TrailersExtract, Metadata, T](
      displayActualValue = true
    ) {
      // The use of the iterable returned by `getAll` involves deserializing unused elements
      // except for `find(0)` and `findAll`
      override def findExtractor(occurrence: Int): Expression[Extractor[Metadata, T]] =
        new FindCriterionExtractor[Metadata, String, T](
          checkName = "trailer",
          criterion = key.name(),
          occurrence = occurrence,
          extractor = { metadata =>
            Option(metadata.getAll(key)).flatMap { iterable =>
              val iterator = iterable.iterator().asScala
              iterator.drop(occurrence)
              if (iterator.hasNext) Some(iterator.next()) else None
            }.success
          }
        ).expressionSuccess
      override def findAllExtractor: Expression[Extractor[Metadata, Seq[T]]] =
        new FindAllCriterionExtractor[Metadata, String, T](
          checkName = "trailer",
          criterion = key.name(),
          extractor = metadata => Option(metadata.getAll(key)).map(_.asScala.toSeq).success
        ).expressionSuccess
      override def countExtractor: Expression[Extractor[Metadata, Int]] =
        new CountCriterionExtractor[Metadata, String](
          checkName = "trailer",
          criterion = key.name(),
          extractor = metadata => Some(metadata.getAll(key) match {
            case null => 0
            case iterable => iterable.asScala.size
          }).success
        ).expressionSuccess
    }

  object Materializer extends CheckMaterializer[TrailersExtract, GrpcCheck[Any], GrpcResponse[Any], Metadata](
    specializer = GrpcCheck(_, GrpcCheck.Trailers)
  ) {
    override protected def preparer: Preparer[GrpcResponse[Any], Metadata] = _.trailers.success
  }

}

// phantom type for implicit materializer resolution
trait TrailersExtract
