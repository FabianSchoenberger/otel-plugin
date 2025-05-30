package com.infendro.otel.util

import com.infendro.otlp.OtlpExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

actual fun await(
    exporter: OtlpExporter
) = runBlocking {
    exporter.await()
}

actual fun await(
    exporter: OtlpExporter,
    start: Instant
) = runBlocking {
    exporter.await()

    val end = Clock.System.now()
    val ms = (end - start).inWholeMilliseconds
    println("Flush finished - $ms ms elapsed")
}

actual fun env(name: String): String? {
    return System.getenv(name)
}
