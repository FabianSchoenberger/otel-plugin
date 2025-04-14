package com.infendro.otel.util

import com.infendro.otlp.OtlpExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import node.process.process

actual fun await(
    exporter: OtlpExporter
) {
    CoroutineScope(Dispatchers.Default).launch {
        exporter.await()
        process.exit()
    }
}
