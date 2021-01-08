package com.github.phisgr.gatling.javapb

import com.github.phisgr.gatling.grpc.Predef.$
import com.github.phisgr.gatling.javapb._
import com.github.phisgr.pb.complex.{Bar, Foo}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.session.{Expression, Session}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util
import java.util.stream.{Collector, Collectors}
import scala.jdk.CollectionConverters._

class JavaPbExprSpec extends AnyFlatSpec with Matchers with StrictLogging {

  private def newBar(baz: Int) = Bar.newBuilder().setBaz(baz).build()

  private def newFoo(bar: List[Bar] = Nil, barMap: Map[String, Bar] = Map.empty) =
    Foo.newBuilder().addAllBar(bar.asJava).putAllBarMap(barMap.asJava).build()

  private val session = Session(
    scenario = "Scenario",
    userId = 1L,
    eventLoop = null
  ).setAll(
    "bar" -> newBar(baz = 1),
    "baz" -> 2,
    "bars" -> List(newBar(baz = 5), newBar(baz = 6)).asJava,
    "stringBar" -> ("bar" -> newBar(baz = 10)),
    "stringBarMap" -> Map("barr" -> newBar(baz = 11)).asJava,
    "generics" -> List("erasure", "BOOM!").asJava
  )

  "Setting with builder" should "work" in {
    val barE: Expression[Bar] = Bar.getDefaultInstance
      .updateUncurry(_.setBaz)($("baz"))
    barE(session) shouldBe Success(Bar.newBuilder().setBaz(2).build())

    val fooE: Expression[Foo] = newFoo(List(newBar(100)))
      .updateUncurry(_.getBarBuilder(0).setBaz)($("baz"))
    fooE(session) shouldBe Success(newFoo(bar = List(newBar(2))))
  }

  "Modifying with builder" should "work" in {
    val barE: Expression[Bar] = $[Bar]("bar")
      .updateUncurry(builder => (baz: Int) => builder.setBaz(builder.getBaz - baz))($("baz"))
    barE(session) shouldBe Success(newBar(-1))
  }

  "Handling list with builder" should "work" in {
    val fooE: Expression[Foo] = Foo.getDefaultInstance
      .updateUncurry(_.addBarBuilder.setBaz)($("baz"))
      .updateUncurry[Bar](_.addBar)($("bar"))
      .updateUncurry(_.addAllBar)($("bars"))
    val foo = newFoo(
      bar = List(
        newBar(2), newBar(1), newBar(5), newBar(6)
      )
    )
    fooE(session) shouldBe Success(foo)

    fooE.updateUncurry(
      builder => (baz: Int) => builder.getBarBuilderList.forEach(_.setBaz(baz))
    )($("baz"))(session) shouldBe Success(
      newFoo(
        bar = List(
          newBar(2), newBar(2), newBar(2), newBar(2)
        )
      )
    )
    fooE.updateUncurry(_.getBarBuilder(2).setBaz)($("baz"))(session) shouldBe Success(
      newFoo(
        bar = List(
          newBar(2), newBar(1), newBar(2), newBar(6)
        )
      )
    )
  }

  "Handling map with builder" should "work" in {
    val listToMap = Collectors.toMap[String, String, Bar](identity[String], (s: String) => newBar(s.length))
      .asInstanceOf[Collector[String, Any, util.Map[String, Bar]]]

    val fooE: Expression[Foo] = Foo.getDefaultInstance
      .updateUncurry(builder => (builder.putBarMap _).tupled)($("stringBar"))
      .updateUncurry(_.putAllBarMap)($[util.List[String]]("generics").map(_.stream().collect(listToMap)))
      .updateUncurry(_.putAllBarMap)($("stringBarMap"))
    val foo = newFoo(
      barMap = Map(
        "bar" -> newBar(10),
        "barr" -> newBar(11),
        "erasure" -> newBar(7),
        "BOOM!" -> newBar(5),
      )
    )

    fooE(session) shouldBe Success(foo)

    fooE.updateUncurry(builder => (baz: Int) =>
      builder.getBarMapMap.forEach { (key, bar) =>
        builder.putBarMap(key, bar.toBuilder.setBaz(baz).build())
      }
    )($("baz"))(session) shouldBe Success(
      newFoo(barMap = Map(
        "bar" -> newBar(2),
        "barr" -> newBar(2),
        "erasure" -> newBar(2),
        "BOOM!" -> newBar(2)
      ))
    )
  }

  "Wrong session variables" should "fail" in {
    Bar.getDefaultInstance
      .updateUncurry(_.setBaz)($("bar"))(session) shouldBe
      Failure("Value baz: 1\n is of type com.github.phisgr.pb.complex.Bar, expected int")

    Bar.getDefaultInstance
      .updateUncurry(_.setBaz)($("nonExisting"))(session) shouldBe
      Failure("No attribute named 'nonExisting' is defined")
  }

  "Erased generics" should "be unsafe" in {
    def assertClassCastException(fooE: Expression[Foo]): Unit = {
      val Success(foo) = fooE(session)
      // Notice that the error is thrown after the expression is evaluated
      an[ClassCastException] should be thrownBy foo.toString
      an[ClassCastException] should be thrownBy foo.toByteArray
    }

    assertClassCastException(
      newFoo(bar = List(newBar(1)))
        .updateUncurry(_.addAllBar)($("generics"))
    )

    import io.gatling.core.Predef.stringToExpression
    assertClassCastException(
      newFoo(bar = List(newBar(1)))
        .updateUncurry(_.addAllBar)("${generics}")
    )
  }
}
