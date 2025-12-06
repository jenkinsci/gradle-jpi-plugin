package org.jenkinsci.gradle.plugins.manifest

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jenkinsci.gradle.plugins.jpi.internal.PluginDependencyProvider
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.Manifest

open class GeneratePluginDependenciesManifestTask : DefaultTask() {
    companion object {
        const val NAME: String = "generateJenkinsPluginDependenciesManifest"
    }

    @Internal
    val pluginConfigurations: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * This allows up-to-date tracking to work without having to analyze the dependencies.
     */
    @InputFiles
    val pluginFiles: FileCollection = pluginConfigurations.filter {
        it.path.endsWith(".hpi") || it.path.endsWith(".jpi")
    }

    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @Internal
    val pluginDependencyProvider: Property<PluginDependencyProvider> = project.objects.property(PluginDependencyProvider::class.java)

    @TaskAction
    fun generate() {
        val manifest = Manifest()
        manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
        val dependencies = pluginDependencyProvider.get().pluginDependencies()
        if (dependencies.isNotEmpty()) {
            manifest.mainAttributes.putValue("Plugin-Dependencies", dependencies)
        }
        outputFile.asFile.get().outputStream().use {
            manifest.write(it)
        }
    }
}
