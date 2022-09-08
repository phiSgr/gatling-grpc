Run on MacBook Pro (15-inch, 2018), 2.2 GHz Intel Core i7, 32 GB 2400 MHz DDR4, OpenJDK 11.

What can I say, straightforward boring code wins, to no one's surprise.

It does seem (from another run not shown below)
that the inline versions written in Kotlin, that emit a `scala.Function1` directly,
are less at the optimizer's mercy.

Also noteworthy is that the (allocation of the) Java `Session` wrapper is optimized away.

```
REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.

Benchmark                                                Mode  Cnt      Score     Error   Units
TestPayload.baseline                                    thrpt   30  47784.420 ±  64.851  ops/ms
TestPayload.baseline:·gc.alloc.rate.norm                thrpt   30     56.000 ±   0.001    B/op
TestPayload.java                                        thrpt   30  47341.675 ±  31.507  ops/ms
TestPayload.java:·gc.alloc.rate.norm                    thrpt   30     56.000 ±   0.001    B/op
TestPayload.kotlinHelper                                thrpt   30  47385.835 ±  49.586  ops/ms
TestPayload.kotlinHelper:·gc.alloc.rate.norm            thrpt   30     56.000 ±   0.001    B/op
TestPayload.kotlinInlined                               thrpt   30  47371.987 ±  36.497  ops/ms
TestPayload.kotlinInlined:·gc.alloc.rate.norm           thrpt   30     56.000 ±   0.001    B/op
TestPayload.scala                                       thrpt   30  21516.884 ± 465.411  ops/ms
TestPayload.scala:·gc.alloc.rate.norm                   thrpt   30    160.000 ±   0.001    B/op
```
