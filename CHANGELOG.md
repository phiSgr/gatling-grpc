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
