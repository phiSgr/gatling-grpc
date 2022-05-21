# Kotlin/Java POC

Wrappers for Gatling-gRPC.

The project is written in Kotlin,
but the goal is that Java users can write simulations idiomatically.

Currently, this POC includes `GrpcCallActionBuilder` and `StaticGrpcProtocol`.

## Function types

The 3 languages each have their function types.

- The Scala function types will not be exposed to Java/Kotlin users.
- The Java function types will be used as default.
- The Kotlin function types are exposed to Java users when there is benefit, see below.

It is possible to "hide" the Kotlin specific methods in Java
by using the `@JvmName("_Kt...")` annotation.\
This trick can also be applied to Kotlin-function-taking methods.
But I don't think the code duplication is worth it.

## Why Kotlin

### Inline Functions

`inline` functions allows low overhead creation of Scala functions. E.g.:

```kotlin
inline fun checkIf(crossinline condition: (Session) -> Boolean) = ConditionWithoutRes(
    this as Self,
    toScalaExpression { session -> condition(Session(session)) }
)
```

When `.checkIf { session -> session.userId() == 50L }` is called,
a `scala.Function1` is created that contains the logic directly,
rather than a wrapper object that calls the wrapped function object.

When `checkIf` is called in Java, a wrapper will be created.
But in the code there is no loss of ergonomics,
despite the use of Kotlin function type.

```java
.checkIf(session -> session.userId() == 50L)
```

### Better type inference

A problem in the Scala API is that the `Res` type of the `extract`ion function cannot be inferred.
In [GrpcExample](../src/test/scala/com/github/phisgr/example/GrpcExample.scala):\
`extract((_: ChatMessage).username.some)`\
The type `ChatMessage` has to be explicitly provided.
Therefore, the various `extract`tion methods are added to the `GrpcCallActionBuilder` classs.

Because of the receiver function feature in Kotlin, we can write, in
[GrpcExampleKt](../kt/src/test/kotlin/com/github/phisgr/example/GrpcExampleKt.kt):\
`.check({ extract { it.token }.saveAs("token") })`.

In [Java](../kt/src/test/java/com/github/phisgr/example/GrpcExampleJ.java)
the method reference can be used\
`.check(extract(RegisterResponse::getToken).saveAs("token"))`.\
But it gets clumsier if the extraction is not a single getter call.\
`extract((ChatMessage m) -> Arrays.stream(m.getData().split(" ")).findFirst().orElse(null)).saveAs("s")`

## Limitations

Sbt does not handle this trilingual project well.
Every `compile` for Kotlin has to start from scratch.

Sometimes the Kotlin compiler crash,
sometimes IntelliJ itself crash.
