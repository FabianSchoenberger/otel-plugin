package com.infendro.otel.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class GradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(
        kotlinCompilation: KotlinCompilation<*>
    ): Boolean = true

    override fun apply(
        target: Project
    ) {
        super.apply(target)
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider {
            listOf(
                SubpluginOption("enabled", "true"),
                SubpluginOption("debug", "false")
            )
        }
    }

    override fun getCompilerPluginId(): String = "otel-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.infendro.otel",
        artifactId = "plugin",
        version = "1.0.0"
    )
}
