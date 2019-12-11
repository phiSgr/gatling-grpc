# Gatling-gRPC

A [Gatling](http://gatling.io/) load test plugin for [gRPC](https://grpc.io/).

## Issues

Please do NOT use the issue tracker to ask questions.
The issue tracker is now strictly for bug reports and feature requests,
which are more than welcomed.

If you need help, the documentation on [Gatling](https://gatling.io/docs/current)
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
For a quickstart guide, see this
[Medium article](https://medium.com/@georgeleung_7777/a-demo-of-gatling-grpc-bc92158ca808).

To use this library, add this library to the test dependencies
along with the two required by Gatling.

```sbt
libraryDependencies ++= Seq(
  "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
  "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
  "com.github.phisgr" %% "gatling-grpc" % "0.7.0" % "test"
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

In order to feed data to grpc services and objects, you use the gatling session object, expressions and lenses. Expressions are specific to gatling-grpc.

```scala
grpc("Use session")
.rpc(GreetServiceGrpc.METHOD_GREET)
.payload(
  HelloWorld.defaultInstance.updateExpr(
    _.name :~ $("s"),   // extracts variable 's' from the session. The $ function is actually an expression
    _.username :~ $("username") // extracts variable 'username'
  )
)
```
The [:~](src/main/scala/com/github/phisgr/gatling/pb/package.scala) function is a lens to modify grpc objects during a simulation. In the same source file, there are corresponding functions to manipulate more complex grpc objects like lists or maps.

It's possible to get the result from a grpc call and store it in the session for subsequent calls.

For a complete demo and various examples,
see [`GrpcExample` in test](src/test/scala/com/github/phisgr/example/GrpcExample.scala).

### Logging
In [`logback.xml`](https://gatling.io/docs/current/general/debugging/#logback), add  
`<logger name="com.github.phisgr.gatling.grpc" level="DEBUG" />`  
to log the requests and responses that are failed (KO in Gatling)
and with a non-OK gRPC status code.

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

#### 0.7.0
Logs failed requests with a non-OK status code.
Adds `Expression` versions of Lens extension methods.
Upgrades versions.

#### 0.6.0
Adds `target` for connections to different endpoints
different from the value specified in `protocols`.
Upgrades to Gatling 3.3.0.

#### 0.5.0
Upgrades to Gatling 3.2.0.
Fixed the duplicated "extraction crashed" in error messages.

#### 0.4.1
The `Expression`s created with `updateExpr` should be faster.

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

`sbt clean coverage test gatling:test coverageReport` for a coverage report.  
`sbt bench/clean 'bench/jmh:run -i 10 -wi 5 -f1 -t1'` for JMH tests.
