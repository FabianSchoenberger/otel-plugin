package com.infendro.otel.util

import com.infendro.otlp.OtlpExporter
import kotlinx.coroutines.runBlocking

actual fun await(
    exporter: OtlpExporter
) = runBlocking {
    exporter.await()
}
