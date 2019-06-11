# Gatling-gRPC

A [Gatling](http://gatling.io/) stress test plugin for [gRPC](https://grpc.io/).

## Issues

Please do NOT use the issue tracker to ask questions.
The issue tracker is now strictly for bug reports and feature requests,
which are more than welcomed.

If you need help, the documentation on [Gatling](https://gatling.io/docs/3.1)
and [gRPC Java](https://grpc.io/docs/tutorials/basic/java/)
are both very helpful.
This is a very small library, the problems you encounter are likely
not Gatling-gRPC specific and can be solved by better understanding
Gatling, gRPC, or general tooling.

If you get stuck, feel free to send me an email.
But before you do, please make sure to read
[How To Ask Questions The Smart Way](http://www.catb.org/~esr/faqs/smart-questions.html).
This page is not related to me. I link it here with the hope that
this document will make communication more effective.

Unresolved questions stay longer in my head than I want them to.
As a help for my mental discipline,
if your problem cannot be solved in one email,
I will not respond further until I receive 100 USD in PayPal.

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
  "com.github.phisgr" %% "gatling-grpc" % "0.4.0" % "test"
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

#### 0.4.0
Uses the `RequestAction`,
if you have unbuildable requests in your tests,
you will get more error logs.

#### 0.3.0
Throttling support and channel sharing,
removed `setUpGrpc` as it is done automatically.

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
