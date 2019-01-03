# Gatling-gRPC

A [Gatling](http://gatling.io/) stress test plugin for [gRPC](https://grpc.io/).

## Usage

Because of gRPC's need of code generation,
I assume you are running the tests using the
[SBT plugin](https://gatling.io/docs/current/extensions/sbt_plugin/).

To use this library, add this library to the test dependencies
along with the two required by Gatling.

```sbt
libraryDependencies ++= Seq(
  "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
  "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
  "com.github.phisgr" %% "gatling-grpc" % "0.2.0" % "test"
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

Currently, before making a gRPC call,
a `ManagedChannel` has to be put inside the user's session by calling
`.exec(setUpGrpc)`

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

For a complete demo, see `GrpcExample` in test.

## A word on Scala

I hope this library, like the main Gatling framework,
can be used with little knowledge in Scala
by referring to the examples.

If you want to know a bit more on what's happening,
here is a bit more explanation.

The payload can be an [`Expression`](https://gatling.io/docs/2.3/session/expression_el/),
or a plain value like the one above.
This is possible because of the implicit conversion `value2Expression`.

In `GrpcExample`, some arguments are not separated by a comma
but are put in two pairs of brackets.
An example is the header key and value `.header(TokenHeaderKey)($("token"))`
The reason is that it helps type inference.
The compiler does not know what type the expression is of
(in other words, the type parameter for `$`),
unless we tell the compiler to look at the type of the header key
by separating the arguments into two argument lists.

## Changelog

#### 0.3.0
Throttling support and channel sharing.

#### 0.2.0
Migrated to Gatling 3.0.
The new version should be source compatible with the previous one,
as evidenced by the test which is not changed.

#### 0.1.0
Previously method references
(functions that are not applied, an `_` before a method)
were used to refer to an RPC.
In this version, method descriptors are used,
bringing a better looking API.
But the old one is still kept for more flexibility.

## Development

`sbt clean coverage gatling:test coverageReport` for a coverage report.
