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
  See the [POC readme](../kt/README.md#inline-functions) for the reason.
- The implicit conversion `value2Success` has an evidence param (`NOT_FOR_USER_CODE`).
  It creates a new object every time,
  and costs an extra 16 bytes (in my JVM).\
  One would hope JVM could inline and remove it,
  or Gatling would change it to a static object (c.f. `object Keep` in Akka Streams).
- The no-op `transform` added by `io.gatling.javaapi.core.CheckBuilder.Find.Default.find`
  is quite costly!
    - There are 3 extra object allocations
        1. the closure (which calls `Option.map`) to send to `Validation.map`
        2. The `Option` object
        3. the new `Validation` Object
      Each of them wraps around a pointer, costing 72 bytes in total.
    - In the earlier tests I have done, which only includes the content testing logic,
      it is a 33% slow down (from 30k ops/ms to 20k ops/ms).
- In this test the Kotlin checks are slightly faster than the Scala baseline,
  but the bytecode isn't exactly what Scala would generate,
  so don't worry about it that much.

```
REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.
Benchmark                                                           Mode  Cnt     Score     Error   Units
TestCheck.javaCheck                                                thrpt    9  7794.465 ± 177.441  ops/ms
TestCheck.javaCheck:·gc.alloc.rate.norm                            thrpt    9   616.000 ±   0.001    B/op
TestCheck.kotlinElCheck                                            thrpt    9  8062.523 ±  79.503  ops/ms
TestCheck.kotlinElCheck:·gc.alloc.rate.norm                        thrpt    9   616.000 ±   0.001    B/op
TestCheck.kotlinPlainCheck                                         thrpt    9  8056.610 ± 120.829  ops/ms
TestCheck.kotlinPlainCheck:·gc.alloc.rate.norm                     thrpt    9   616.000 ±   0.001    B/op
TestCheck.kotlinWrappedCheck                                       thrpt    9  7057.859 ±  42.560  ops/ms
TestCheck.kotlinWrappedCheck:·gc.alloc.rate.norm                   thrpt    9   688.000 ±   0.001    B/op
TestCheck.scalaBaselineCheck                                       thrpt    9  7887.096 ± 144.031  ops/ms
TestCheck.scalaBaselineCheck:·gc.alloc.rate.norm                   thrpt    9   616.000 ±   0.001    B/op
TestCheck.scalaCheckWithImplicit                                   thrpt    9  7628.845 ± 106.257  ops/ms
TestCheck.scalaCheckWithImplicit:·gc.alloc.rate.norm               thrpt    9   632.000 ±   0.001    B/op
```
