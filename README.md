# Gatling-gRPC

A [Gatling](http://gatling.io/) load test plugin for [gRPC](https://grpc.io/).

## Usage

Because of gRPC's need of code generation,
I assume you are running the tests using the
[SBT plugin](https://gatling.io/docs/current/extensions/sbt_plugin/).
For a quickstart guide, see this
[Medium article](https://medium.com/@georgeleung_7777/a-demo-of-gatling-grpc-bc92158ca808).

To use this library, add this library to the test dependencies
along with the two required by Gatling.

```sbt
libraryDependencies ++= Seq(
  "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
  "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
  "com.github.phisgr" %% "gatling-grpc" % "0.8.2" % "test"
)
enablePlugins(GatlingPlugin)
```

For setting up the code generation, see
[the documentation in ScalaPB](https://scalapb.github.io/sbt-settings.html).

If your protobuf files are in `src/test/protobuf`
instead of `src/main/protobuf`, change `Compile` to `Test`.

```sbt
PB.targets in Test := Seq(
  scalapb.gen() -> (sourceManaged in Test).value
)
```

To make a gRPC call:

```scala
exec(
  grpc("my_request")
    .rpc(GreetServiceGrpc.METHOD_GREET)
    .payload(HelloWorld(
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

### Logging
In [`logback.xml`](https://gatling.io/docs/current/general/debugging/#logback), add  
`<logger name="com.github.phisgr.gatling.grpc" level="DEBUG" />`  
to log the requests that are failed;
or set the `level` to `TRACE` to log all gRPC requests.

## Development

`sbt clean coverage test gatling:test coverageReport` for a coverage report.  
`sbt bench/clean 'bench/jmh:run -i 10 -wi 5 -f1 -t1'` for JMH tests.
