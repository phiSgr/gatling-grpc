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
TestUpdateExpr.forComplexExpr                                      thrpt   50  13843.553 ±  509.311  ops/ms
TestUpdateExpr.forComplexExpr:·gc.alloc.rate.norm                  thrpt   50    300.800 ±    3.667    B/op
TestUpdateExpr.forSimpleExpr                                       thrpt   50  34542.799 ± 1315.359  ops/ms
TestUpdateExpr.forSimpleExpr:·gc.alloc.rate.norm                   thrpt   50     86.400 ±    3.920    B/op
TestUpdateExpr.matchComplexExpr                                    thrpt   50  14181.627 ±   75.486  ops/ms
TestUpdateExpr.matchComplexExpr:·gc.alloc.rate.norm                thrpt   50    296.000 ±    0.001    B/op
TestUpdateExpr.matchSimpleExpr                                     thrpt   50  41709.347 ±  130.864  ops/ms
TestUpdateExpr.matchSimpleExpr:·gc.alloc.rate.norm                 thrpt   50     80.000 ±    0.001    B/op
TestUpdateExpr.updateComplexExpr                                   thrpt   50  10356.887 ±  281.384  ops/ms
TestUpdateExpr.updateComplexExpr:·gc.alloc.rate.norm               thrpt   50    401.600 ±   15.679    B/op
TestUpdateExpr.updateComplexExprJava                               thrpt   50  12816.820 ±  302.722  ops/ms
TestUpdateExpr.updateComplexExprJava:·gc.alloc.rate.norm           thrpt   50    340.800 ±    7.839    B/op
TestUpdateExpr.updateSimpleExpr                                    thrpt   50  34171.439 ± 1312.140  ops/ms
TestUpdateExpr.updateSimpleExpr:·gc.alloc.rate.norm                thrpt   50     99.200 ±    3.667    B/op
TestUpdateExpr.updateSimpleExprJava                                thrpt   50  37880.445 ±  731.415  ops/ms
TestUpdateExpr.updateSimpleExprJava:·gc.alloc.rate.norm            thrpt   50     91.200 ±    3.667    B/op
```
