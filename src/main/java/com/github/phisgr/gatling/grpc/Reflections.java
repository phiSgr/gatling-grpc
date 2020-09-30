package com.github.phisgr.gatling.grpc;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.ClientCalls;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * We are all consenting adults.
 */
public class Reflections {
    private static final MethodHandle cancelThrow;
    private static final MethodHandle metadataName;
    private static final MethodHandle metadataValue;

    public static final Status SHUTDOWN_NOW_STATUS;

    static {
        try {
            cancelThrow = unreflectMethod(
                    ClientCalls.class.getDeclaredMethod("cancelThrow", ClientCall.class, Throwable.class)
            );
            metadataName = unreflectMethod(
                    Metadata.class.getDeclaredMethod("name", int.class)
            );
            MethodHandle value;
            try {
                value = unreflectMethod(
                        Metadata.class.getDeclaredMethod("valueAsBytes", int.class)
                );
            } catch (NoSuchMethodException e) {
                value = unreflectMethod(
                        Metadata.class.getDeclaredMethod("value", int.class)
                );
            }
            metadataValue = value;
            Field shutdownNowStatusField = Class.forName("io.grpc.internal.ManagedChannelImpl")
                    .getDeclaredField("SHUTDOWN_NOW_STATUS");
            shutdownNowStatusField.setAccessible(true);
            SHUTDOWN_NOW_STATUS = (Status) shutdownNowStatusField.get(null);
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
