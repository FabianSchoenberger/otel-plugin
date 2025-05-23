package com.infendro.otel.util

import com.infendro.otlp.OtlpExporter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.getenv

actual fun await(
    exporter: OtlpExporter
) = runBlocking {
    exporter.await()
}

@OptIn(ExperimentalForeignApi::class)
actual fun env(name: String): String? {
    return getenv(name)?.toKString()
}
