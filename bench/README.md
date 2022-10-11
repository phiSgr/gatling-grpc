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

See also [benchmarks done for Kotlin support](../gatling-grpc-kt/src/jmh/README.md)
