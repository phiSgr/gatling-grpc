# Gatling-gRPC

A [Gatling](http://gatling.io/) load test plugin for [gRPC](https://grpc.io/).

### Java/Kotlin API

Since 3.7, Gatling adds Java API for the test to be written in Java or Kotlin.
In Gatling-gRPC 0.14.0, [a binding](gatling-grpc-kt) is written in Kotlin for Java and Kotlin users.

## Usage

Because of gRPC's need of code generation,
I assume you are running the tests using a build tool, e.g. the
[SBT plugin](https://gatling.io/docs/current/extensions/sbt_plugin/).
For a quickstart guide, see this
[Medium article](https://medium.com/@georgeleung_7777/a-demo-of-gatling-grpc-bc92158ca808).

For usage with the [Gradle Gatling plugin](https://gatling.io/docs/current/extensions/gradle_plugin/),
see this [example project](https://github.com/phiSgr/gatling-grpc-gradle-demo).

To use this library, add this library to the test dependencies
along with the two required by Gatling.

```sbt
libraryDependencies ++= Seq(
  "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
  "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
  "com.github.phisgr" % "gatling-grpc" % "0.14.0" % "test"
)
enablePlugins(GatlingPlugin)
```

For setting up the code generation, see
[the documentation in ScalaPB](https://scalapb.github.io/sbt-settings.html).

If your protobuf files are in `src/test/protobuf`
instead of `src/main/protobuf`, change `Compile` to `Test`.

```sbt
Test / PB.targets := Seq(
  scalapb.gen() -> (Test / sourceManaged).value
)
```

To make a gRPC call:

```scala
exec(
  grpc("my_request")
    .rpc(ChatServiceGrpc.METHOD_GREET)
    .payload(GreetRequest(
      username = "myUserName",
      name = "My name"
    ))
)
```

__For a complete demo and various examples
including the proper syntax to include
[session attributes](https://gatling.io/docs/current/session/session_api/)
(e.g. from a feeder, or saved in a check),
see [`GrpcExample` in test](src/test/scala/com/github/phisgr/example/GrpcExample.scala).__

For more complex manipulations with session attributes and protobuf objects,
like repeated fields and map fields,
see [the unit test](src/test/scala/com/github/phisgr/gatling/pb/LensExprSpec.scala).

### Dynamic Payload
There are helper methods in `gatling-grpc` for
generating dynamic ScalaPB objects with `Lens`es,
as demonstrated in the example linked above.
It makes uses of [extension methods](https://docs.scala-lang.org/overviews/core/implicit-classes.html),
and requires importing `com.github.phisgr.gatling.pb._`.

If you want to use Java Protobuf classes,
you can use the [`gatling-javapb`](java-pb) library.

If the expressive power of these two plumbing tools are not enough,
you can always resort to [writing a lambda](https://github.com/phiSgr/gatling-grpc/blob/77c9bb1231037ac4a531cfee4c3f88dd09e13fbc/bench/src/main/scala/com/github/phisgr/gatling/pb/bench/TestUpdateExpr.scala#L78).
Because an `Expression[T]`is 
[just an alias](https://gatling.io/docs/current/session/expression_el/#expression) 
for `Session => Validation[T]`.

### Logging
In [`logback.xml`](https://gatling.io/docs/current/general/debugging/#logback), add  
`<logger name="com.github.phisgr.gatling.grpc" level="DEBUG" />`  
to log the requests that are failed;
or set the `level` to `TRACE` to log all gRPC requests.

## Development

`sbt clean coverage test Gatling/test coverageReport` for a coverage report.  
`sbt clean bench/clean 'bench/jmh:run -i 3 -wi 3 -f10 -t1 -prof gc'` for JMH tests.
