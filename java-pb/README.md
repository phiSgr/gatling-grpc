# Gatling-JavaPB

To update a Java Protobuf message in application code,
one calls `toBuilder` and mutates the builder,
and finally calls `build()`.

Due to the wrapped nature of Gatling `Expression`s,
this can be quite clumsy to write.
One have to create a lambda and maybe use for-comprehensions
whenever an expression is required.

I asked myself if I could, but didn't stop to think if I should.
So this one-file library was created to help combining
expressions with Java Protobuf messages.

This is not included in the Gatling-gRPC library to avoid confusion.

## Usage

Below is copied from the test in `bench`.

```scala
private val ComplexExprJava: Expression[Test.ComplexMessage] =
  Test.ComplexMessage.getDefaultInstance.updateWith[Test.ComplexMessage.Builder]
    .update(_.getMBuilder.setS)($("name"))
    .update(_.setI)($("count"))
```

IntelliJ will insert a pair of brackets after the setter,
delete them as we need the function reference.

### Call for help

Unfortunately in the Java Protobuf classes, there is not a linkage of
a message type and its builder class.
Their relationship exists only in the return type
of the overridden `toBuilder`.

Therefore I cannot make the Scala compiler infer `A.Builder` from `A`.
Maybe this can be accomplished with some macro,
but I have no experience with that.
