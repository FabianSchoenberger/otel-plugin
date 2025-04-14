package com.infendro.otel.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

object ConfigurationKeys {
    val KEY_ENABLED: CompilerConfigurationKey<Boolean> = CompilerConfigurationKey.create("enabled")
}
