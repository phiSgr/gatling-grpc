package com.github.phisgr.example

import com.github.phisgr.example.Chat.CustomError
import io.grpc.Metadata
import io.grpc.protobuf.ProtoUtils

@JvmField
val tokenHeaderKey: Metadata.Key<String> = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)

@JvmField
val errorResponseKey: Metadata.Key<CustomError> = ProtoUtils.keyForProto(CustomError.getDefaultInstance())

@JvmField
val ports = listOf(8081, 8082, 8083)
