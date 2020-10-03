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
TestUpdateExpr.forComplexExpr                                      thrpt   50  14658.032 ±  129.974  ops/ms
TestUpdateExpr.forComplexExpr:·gc.alloc.rate.norm                  thrpt   50    304.000 ±    4.001    B/op
TestUpdateExpr.forSimpleExpr                                       thrpt   50  64190.526 ±  170.913  ops/ms
TestUpdateExpr.forSimpleExpr:·gc.alloc.rate.norm                   thrpt   50     48.000 ±    0.001    B/op
TestUpdateExpr.matchComplexExpr                                    thrpt   50  15897.540 ±   71.606  ops/ms
TestUpdateExpr.matchComplexExpr:·gc.alloc.rate.norm                thrpt   50    296.000 ±    0.001    B/op
TestUpdateExpr.matchSimpleExpr                                     thrpt   50  64309.354 ±  165.874  ops/ms
TestUpdateExpr.matchSimpleExpr:·gc.alloc.rate.norm                 thrpt   50     48.000 ±    0.001    B/op
TestUpdateExpr.updateComplexExpr                                   thrpt   50  12262.249 ±  434.146  ops/ms
TestUpdateExpr.updateComplexExpr:·gc.alloc.rate.norm               thrpt   50    388.800 ±   12.802    B/op
TestUpdateExpr.updateComplexExprJava                               thrpt   50  13941.886 ±  193.865  ops/ms
TestUpdateExpr.updateComplexExprJava:·gc.alloc.rate.norm           thrpt   50    344.000 ±    8.001    B/op
TestUpdateExpr.updateSimpleExpr                                    thrpt   50  42030.479 ± 1527.799  ops/ms
TestUpdateExpr.updateSimpleExpr:·gc.alloc.rate.norm                thrpt   50     92.800 ±    3.667    B/op
TestUpdateExpr.updateSimpleExprJava                                thrpt   50  42052.846 ± 1242.218  ops/ms
TestUpdateExpr.updateSimpleExprJava:·gc.alloc.rate.norm            thrpt   50     81.600 ±    2.400    B/op
```
