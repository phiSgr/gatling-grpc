Run on MacBook Pro (15-inch, 2018), 2.2 GHz Intel Core i7, 32 GB 2400 MHz DDR4.

The use of Gatling-JavaPB results in an approximately 10% slowdown
compared to writing a lambda by hand,
but is still way faster than using ScalaPB lenses.
Though I must say that these numbers should not matter a bit.

```
REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.
Benchmark                              Mode  Cnt      Score     Error   Units
TestUpdateExpr.lambdaComplexExprJava  thrpt   10  14618.356 ±  84.378  ops/ms
TestUpdateExpr.lambdaSimpleExprJava   thrpt   10  35046.056 ± 314.494  ops/ms
TestUpdateExpr.updateComplexExpr      thrpt   10   7652.590 ±  49.014  ops/ms
TestUpdateExpr.updateComplexExprJava  thrpt   10  13297.682 ±  79.639  ops/ms
TestUpdateExpr.updateSimpleExpr       thrpt   10  20669.126 ± 118.993  ops/ms
TestUpdateExpr.updateSimpleExprJava   thrpt   10  31824.563 ± 183.394  ops/ms
```
