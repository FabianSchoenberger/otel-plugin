# OpenTelemetry Gradle Plugin

This Gradle Plugin automatically implements OpenTelemetry tracing using code instrumentation.

## Usage

Add the following to your plugins.
```kotlin
id("com.infendro.otel") version "1.0.0"
```

And configure the plugin using the following.
```kotlin
otel {
    enabled = true             // optional, default is true
    debug = true               // optional, default is false
    host = "localhost:4318"    // required
    service = "plugin"         // required
}
```

Add the following to your common dependencies.
```kotlin
implementation("com.infendro.otel:util:1.0.0")
implementation("com.infendro.otel:otlp-exporter:1.0.0")
implementation("io.opentelemetry.kotlin.api:all:1.0.570")
implementation("io.opentelemetry.kotlin.sdk:sdk-trace:1.0.570")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
```

## Test

The tests require the OpenTelemetry Collector to be running and accessible at `localhost:4318`.

Run the tests using the following in the `plugin` module. \
`./gradlew :test --tests "com.infendro.otel.plugin.PluginTest"`
