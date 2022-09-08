# Gatling-JavaPB

To update a Java Protobuf message in application code,
one calls `toBuilder` and mutates the builder,
and finally calls `build()`.

Due to the wrapped nature of Gatling `Expression`s,
this can be quite clumsy to write.
One have to create a lambda and maybe use for-comprehensions
whenever an expression is required.

I asked myself if I could, but didn't stop to think if I should.
So this two-file library was created to combine
expressions with Java Protobuf messages.

This is not included in the Gatling-gRPC library to avoid confusion.

## Usage

Assuming you're using
[Gatling with Gradle](https://gatling.io/docs/current/extensions/gradle_plugin/),
add this line to the `dependencies`.  
`gatling 'com.github.phisgr:gatling-javapb:1.3.0'`

Below is copied from
[the unit tests](./src/test/scala/com/github/phisgr/gatling/javapb/JavaPbExprSpec.scala).
For the `.update` extension method to work,
you need to `import com.github.phisgr.gatling.javapb._`.

```scala
val fooE: Expression[Foo] = Foo.getDefaultInstance
  .update(_.addBarBuilder.setBaz)($("baz"))
  .update[Bar](_.addBar)($("bar"))
  .update(_.addAllBar)($("bars"))
```

IntelliJ will insert a pair of brackets after the setter,
delete them as we need the function reference.

## Demo Project

In the [demo project with Gradle](https://github.com/phiSgr/gatling-grpc-gradle-demo),
the Java generated code is used.

---

### Call for help

Unfortunately in the Java Protobuf classes, there is not a linkage of
a message type and its builder class.
Their relationship exists only in the return type
of the overridden `toBuilder`.

I managed to use macros to help the inference,
and made an Intellij Scala Plugin Extension.
There are not much documentation on the two,
so I may have made some mistakes.
Please let me know if you see how things can be improved.
