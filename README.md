# Gatling-gRPC

A [Gatling](http://gatling.io/) stress test plugin for [grpc](https://grpc.io/).

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
  "com.github.phisgr" %% "gatling-grpc" % "0.0.1" % "test"
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
    .service(GreetServiceGrpc.stub)
    .rpc(_.greet)(HelloWorld(
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
here is a bit more explanation on the above example:

`GreetServiceGrpc.stub` is a function (think static method) that
takes a gRPC channel and returns the GreetService.

When you type that the IDE may be helpful enough to insert a pair of brackets.
Delete them, because we are not calling the function but referencing it.
The function will be called by the library internally.

`_.greet` is another method reference,
where the underscore is a placeholder for the GreetService.

The next argument in the next pair of brackets is the payload for the RPC.
It can be an [`Expression`](https://gatling.io/docs/2.3/session/expression_el/),
or a plain value like the one above.

You may be wondering why the RPC method reference and the payload
are not separated by a comma, but are put in two pairs of brackets.
The reason is that it helps type inference.

## Development

`sbt clean coverage gatling:test coverageReport` for a coverage report.
