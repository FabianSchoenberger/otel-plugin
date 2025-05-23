package com.infendro.otel.util

import com.infendro.otlp.OtlpExporter

expect fun await(
    exporter: OtlpExporter
)

expect fun env(name: String): String?
