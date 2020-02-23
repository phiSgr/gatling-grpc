Run on MacBook Pro (15-inch, 2018), 2.2 GHz Intel Core i7, 32 GB 2400 MHz DDR4, OpenJDK 11.

When only one single field is updated, ScalaPB lenses have a slight edge,
probably because no new intermediate builder object is created.
Though I must say that these numbers should not matter a bit.

```
REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.
Benchmark                              Mode  Cnt      Score     Error   Units
TestUpdateExpr.lambdaComplexExprJava  thrpt   10  13154.488 ± 407.202  ops/ms
TestUpdateExpr.lambdaSimpleExprJava   thrpt   10  34013.608 ± 493.937  ops/ms
TestUpdateExpr.updateComplexExpr      thrpt   10   9858.000 ± 114.951  ops/ms
TestUpdateExpr.updateComplexExprJava  thrpt   10  13758.187 ± 380.544  ops/ms
TestUpdateExpr.updateSimpleExpr       thrpt   10  39638.295 ± 556.520  ops/ms
TestUpdateExpr.updateSimpleExprJava   thrpt   10  38671.101 ± 120.089  ops/ms
```
