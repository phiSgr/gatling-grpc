Run on MacBook Pro (15-inch, 2018), 2.2 GHz Intel Core i7, 32 GB 2400 MHz DDR4, OpenJDK 11.

In some forks, `flatMap` and `map` in `for`-comprehensions
have same allocation rate and performance similar to plain `match`.
In others, they have higher allocation.

Though I must say that these numbers should not matter a bit.

```
REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.
Benchmark                                                           Mode  Cnt      Score      Error   Units
TestUpdateExpr.forComplexExpr                                      thrpt   50  15457.886 ±  405.010  ops/ms
TestUpdateExpr.forComplexExpr:·gc.alloc.rate.norm                  thrpt   50    356.800 ±   13.292    B/op
TestUpdateExpr.forSimpleExpr                                       thrpt   50  63009.362 ±  469.658  ops/ms
TestUpdateExpr.forSimpleExpr:·gc.alloc.rate.norm                   thrpt   50     64.000 ±    0.001    B/op
TestUpdateExpr.matchComplexExpr                                    thrpt   50  15347.674 ±  786.109  ops/ms
TestUpdateExpr.matchComplexExpr:·gc.alloc.rate.norm                thrpt   50    344.000 ±   16.002    B/op
TestUpdateExpr.matchSimpleExpr                                     thrpt   50  63290.079 ±  371.129  ops/ms
TestUpdateExpr.matchSimpleExpr:·gc.alloc.rate.norm                 thrpt   50     64.000 ±    0.001    B/op
TestUpdateExpr.updateComplexExpr                                   thrpt   50  12432.551 ±  311.176  ops/ms
TestUpdateExpr.updateComplexExpr:·gc.alloc.rate.norm               thrpt   50    395.200 ±   10.615    B/op
TestUpdateExpr.updateComplexExprJava                               thrpt   50  13311.706 ±  153.384  ops/ms
TestUpdateExpr.updateComplexExprJava:·gc.alloc.rate.norm           thrpt   50    360.000 ±    0.001    B/op
TestUpdateExpr.updateSimpleExpr                                    thrpt   50  43452.276 ± 1928.898  ops/ms
TestUpdateExpr.updateSimpleExpr:·gc.alloc.rate.norm                thrpt   50     96.000 ±    4.001    B/op
TestUpdateExpr.updateSimpleExprJava                                thrpt   50  45678.602 ± 1618.755  ops/ms
TestUpdateExpr.updateSimpleExprJava:·gc.alloc.rate.norm            thrpt   50     83.200 ±    3.200    B/op
```

# Java/Kotlin checks

`sbt bench/clean 'bench/Jmh/run -i 3 -wi 3 -f3 -t1 -prof gc .*TestCheck.*'`

The `TestCheck` benchmarks include the essentials of the handling of a gRPC response -
parsing, status code checking, and content checking.

Some observations:

- The Kotlin inline functions, which emits a Scala `Function1`,
  have a minor win over the Java checks, which have to use wrappers.
  See the [POC readme](../kt/README.md#inline-functions) for the details.
- In this test the Kotlin checks are slightly faster than the Scala baseline,
  but the bytecode isn't exactly what Scala would generate,
  so don't worry about it that much.
- The `checkIf` tests have a false condition,
  so the work done is similar to no check at all.
    - The condition involves a Java `io.gatling.javaapi.core.Session` wrapper
      that wraps around the Scala `io.gatling.core.session.Session`.
      But no extra allocation found in this benchmark,
      apparently the JVM can inline that.
    - I was hoping the inline function condition to be faster than Java lambdas.
      But their difference is below noise.
      Well I guess inlining is inlining,
      whether done by Kotlin compiler or the JIT compiler.

```
REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.
Benchmark                                                           Mode  Cnt      Score     Error   Units
TestCheck.javaCheck                                                thrpt    9   7812.965 ± 168.140  ops/ms
TestCheck.javaCheck:·gc.alloc.rate.norm                            thrpt    9    616.000 ±   0.001    B/op
TestCheck.javaCheckIf                                              thrpt    9  10400.860 ±  91.246  ops/ms
TestCheck.javaCheckIf:·gc.alloc.rate.norm                          thrpt    9    464.000 ±   0.001    B/op
TestCheck.justParse                                                thrpt    9  11202.544 ± 167.100  ops/ms
TestCheck.justParse:·gc.alloc.rate.norm                            thrpt    9    464.000 ±   0.001    B/op
TestCheck.kotlinCheckIf                                            thrpt    9  10353.334 ± 259.291  ops/ms
TestCheck.kotlinCheckIf:·gc.alloc.rate.norm                        thrpt    9    464.000 ±   0.001    B/op
TestCheck.kotlinElCheck                                            thrpt    9   7839.525 ±  61.748  ops/ms
TestCheck.kotlinElCheck:·gc.alloc.rate.norm                        thrpt    9    616.000 ±   0.001    B/op
TestCheck.kotlinPlainCheck                                         thrpt    9   7799.017 ± 155.644  ops/ms
TestCheck.kotlinPlainCheck:·gc.alloc.rate.norm                     thrpt    9    616.000 ±   0.001    B/op
TestCheck.kotlinWrappedCheck                                       thrpt    9   7824.871 ±  39.145  ops/ms
TestCheck.kotlinWrappedCheck:·gc.alloc.rate.norm                   thrpt    9    616.000 ±   0.001    B/op
TestCheck.scalaBaselineCheck                                       thrpt    9   7679.443 ±  61.459  ops/ms
TestCheck.scalaBaselineCheck:·gc.alloc.rate.norm                   thrpt    9    616.000 ±   0.001    B/op
TestCheck.scalaCheckWithImplicit                                   thrpt    9   7554.167 ± 176.422  ops/ms
TestCheck.scalaCheckWithImplicit:·gc.alloc.rate.norm               thrpt    9    616.000 ±   0.001    B/op
```
