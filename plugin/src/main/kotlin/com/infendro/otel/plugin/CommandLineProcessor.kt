package com.infendro.otel.plugin

import com.infendro.otel.plugin.ConfigurationKeys.KEY_ENABLED
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class CommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "otel-plugin"

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            "enabled",
            "<true|false>",
            "whether the plugin is enabled"
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option.optionName) {
            "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
            else -> throw CliOptionProcessingException("unknown option: ${option.optionName}")
        }
    }
}
