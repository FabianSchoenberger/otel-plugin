package com.infendro.otel.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class Extension() {
    var enabled: Boolean = true
    var debug: Boolean = false
    var host: String? = null
    var service: String? = null
}

class GradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(
        kotlinCompilation: KotlinCompilation<*>
    ): Boolean = true

    override fun apply(
        target: Project
    ) {
        target.extensions.add("otel", Extension())
        super.apply(target)
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            val extension = project.extensions.findByName("otel") as Extension
            buildList {
                add(SubpluginOption("enabled", extension.enabled.toString()))
                add(SubpluginOption("debug", extension.debug.toString()))
                if (extension.host != null) add(SubpluginOption("host", extension.host!!))
                if (extension.service != null) add(SubpluginOption("service", extension.service!!))
            }
        }
    }

    override fun getCompilerPluginId(): String = "otel-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.infendro.otel",
        artifactId = "plugin",
        version = "1.0.0"
    )
}
