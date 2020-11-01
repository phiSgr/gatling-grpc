#### 0.10.0
CHANGE OF BEHAVIOUR: the timestamp extractor is run **BEFORE**
the checks are run. This makes it easier to detect "first" messages in a stream.

Improves streaming logging, mimics Gatling WebSocket wordings.

Lazy parsing for streaming calls.

Dynamic Channel support. Remember to `exec`
`dyanmicConf.setChannel` before running `.target(dyanmicConf)`.

Uses the `EventLoopGroup` of Gatling in the `ManagedChannel`s.

#### 0.9.0
Upgrades to Gatling 3.4.0.

Defaults to not parsing gRPC response for unary calls.
Call `forceParsing` on your GrpcProtocol to force parsing.

Removed the `.exists(f)` check shorthand.
Write `.extract(f)(_.exists)` instead.

Streaming support!

#### 0.8.2
Fix metadata reflection failure.
Supports silent and group.

#### 0.8.1
Added methods named `managedChannelBuilder`. They mean the same as
`ManagedChannelBuilder.forTarget` or `ManagedChannelBuilder.forAddress`
but have a return type that is easier for the type inference of IntelliJ.
In addition, since Gatling-gRPC does not block threads,
they use the `directExecutor` by default to further improve performance.

#### 0.8.0
This release contains structural changes
that are a step closer to supporting streaming APIs.
Most notably the function-taking API,
that allows arbitrary Scala code to be run, is gone.

As things got moved around, you may need to check your imports.

New functionalities:
- Warm up call to force the class-loading. By default the call is a
[health check](https://github.com/grpc/grpc/blob/master/src/proto/grpc/health/v1/health.proto).
- Specification of call options.
- Trailer extraction.
- Improved logging.

Improved performance:
- Use while loop to implement `foldM`-like manipulation.
  - Performance of dynamic payload creation with ScalaPB lenses
  is now comparable to using [Gatling-JavaPB](java-pb).
- Removed the use of futures in `GrpcCallAction`.

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
