package com.github.phisgr.gatling.grpc;


import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.stub.ClientCalls;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * We are all consenting adults.
 */
public class Reflections {
    private static final MethodHandle cancelThrow;
    private static final MethodHandle metadataName;
    private static final MethodHandle metadataValue;

    static {
        try {
            cancelThrow = unreflectMethod(
                    ClientCalls.class.getDeclaredMethod(
                            "cancelThrow", ClientCall.class, Throwable.class
                    ));
            metadataName = unreflectMethod(
                    Metadata.class.getDeclaredMethod("name", int.class)
            );
            metadataValue = unreflectMethod(
                    Metadata.class.getDeclaredMethod("value", int.class)
            );
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static MethodHandle unreflectMethod(Method reflected) throws Throwable {
        reflected.setAccessible(true);
        return MethodHandles.lookup().unreflect(reflected);
    }

    public static RuntimeException cancelThrow(ClientCall<?, ?> call, Throwable t) throws Throwable {
        return (RuntimeException) cancelThrow.invokeExact(call, t);
    }

    public static byte[] name(Metadata metadata, int i) throws Throwable {
        return (byte[]) metadataName.invokeExact(metadata, i);
    }

    public static byte[] value(Metadata metadata, int i) throws Throwable {
        return (byte[]) metadataValue.invokeExact(metadata, i);
    }
}
