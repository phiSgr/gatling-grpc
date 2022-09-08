# Gatling-gRPC Kotlin/Java API

Binding of Gatling-gRPC for Kotlin and Java.

The overall goal is to allow both Kotlin and Java users to write idiomatically.

`@JvmSynthetic` is added to hide Kotlin specific APIs from Java users.

## Session API and Expressions

In Scala Gatling, there are two ways to describe a dynamic value,
using the [Expression Language](https://gatling.io/docs/gatling/reference/current/core/session/el/),
or creating a [function](https://gatling.io/docs/gatling/reference/current/core/session/function/).

With the [magnet pattern](https://docs.scala-lang.org/tour/implicit-conversions.html),
the Scala API simply takes an `Expression[Something]`,
and accept the plain object, a `String` (that is parsed as EL), or a function object.

There's no such affordance in Java and Kotlin (thankfully).
In Gatling's Java API, several overloads are created.

- `repeat(int times)`
- `repeat(@Nonnull String times)`
- `repeat(@Nonnull Function<Session, Integer> times)`

### Overloads

Because this binding is written in Kotlin, there are more considerations.

There are 6 overloads of `Unary.payload`. For streams, the following works similarly.

First we have `fun payload(body: Req)`, a static value as the payload.

For an EL, Java users will have to provide the `Class` instance,
whereas Kotlin users can use the extension function.

```kotlin
fun payload(el: String, clazz: Class<Req>)
inline fun <reified Req, Res> Unary<Req, Res>.payload(el: String)
```

For supplying a function, we have an overload that takes a `java.util.function.Function`,
which is wrapped to make a `scala.Function1`;
and an `inline` version that emits a `scala.Function1` directly.

```kotlin
fun payload(f: Function<Session, Req>)
inline fun payload(crossinline f: (Session) -> Req)
```

The final overload is protobuf integration for cleaner code, described in the next section.

## Java Protobuf Integration

In the Scala API, Gatling uses the type `Validation` pervasively.
With the monadic wrapping, it can be
[hard to pipe](https://stackoverflow.com/questions/60978575/access-variables-in-gatling-feeder-with-grpc/61006546#61006546)
the data from a `Session` into the message.

Therefore, there's the [Gatling-JavaPB](https://github.com/phiSgr/gatling-grpc/tree/master/java-pb)
integration for Scala users.

But in Gatling's Java API, code for [getting values](
https://gatling.io/docs/gatling/reference/current/core/session/session_api/#getting-attributes)
from a `Session` gives you a simple value.
There's no need for special code to create a dynamic protobuf message.

```java
.payload(session ->
        RegisterRequest.newBuilder()
                .setUsername(session.getString("username"))
                .build()
)
```

But it doesn't hurt to make it easier for Kotlin users.

```kotlin
.payload(RegisterRequest::newBuilder) {
    username = it.getString("username")
    build()
}
```

Here we have the code a bit tighter and use the property access syntax.

## Checks

Unlike the HTTP use-case for Gatling, in Gatling-gRPC checks are performed on well-typed values.
It was quite a challenge to design the API so that the extraction code can be written concisely
(i.e. no need to write explicit types).

Because of a Kotlin [feature](https://kotlinlang.org/docs/lambdas.html#function-literals-with-receiver),
this can be achieved cleanly.

```kotlin
.check({ extract { it.token }.saveAs("token") })
```

Java users will have to provide the response type.

```java
.check(extract(RegisterResponse::getToken).saveAs("token"))
```
