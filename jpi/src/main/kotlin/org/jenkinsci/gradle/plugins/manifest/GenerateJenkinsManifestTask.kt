package org.jenkinsci.gradle.plugins.manifest

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper
import org.jenkinsci.gradle.plugins.jpi.internal.VersionCalculator
import java.net.URI
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.Manifest

open class GenerateJenkinsManifestTask : DefaultTask() {
    companion object {
        const val NAME: String = "generateJenkinsManifest"
    }

    @InputFiles
    val upstreamManifests: ConfigurableFileCollection = project.objects.fileCollection()

    @Input
    val groupId: Property<String> = project.objects.property()

    @Input
    val minimumJavaVersion: Property<String> = project.objects.property()

    @Input
    val pluginId: Property<String> = project.objects.property()

    @Input
    val humanReadableName: Property<String> = project.objects.property()

    @Input
    @Optional
    val homePage: Property<URI> = project.objects.property()

    @Input
    val jenkinsVersion: Property<String> = project.objects.property()

    @Input
    @Optional
    val minimumJenkinsVersion: Property<String> = project.objects.property()

    @Input
    val sandboxed: Property<Boolean> = project.objects.property()

    @Input
    val usePluginFirstClassLoader: Property<Boolean> = project.objects.property()

    @Nested
    val pluginDevelopers: ListProperty<PluginDeveloper> = project.objects.listProperty()

    @Internal
    val version: Property<String> = project.objects.property()

    @Input
    val dynamicSnapshotVersion: Property<Boolean> = project.objects.property<Boolean>().convention(true)

    // TODO this is most correct based on today's behavior, but it's worth considering
    // before 1.0.0 if this timestamp appending should continue to be a part of the jpi
    // plugin. There are many gradle plugins dedicated to versioning that could be
    // recommended instead, and this means the default behavior of `-SNAPSHOT` versions
    // is to always rerun this task and therefore any downstream task
    @Input
    val pluginVersion: Provider<String> =
        dynamicSnapshotVersion.flatMap { isDynamicSnapshotVersion ->
            when (isDynamicSnapshotVersion) {
                true -> version.map { VersionCalculator().calculate(it) }
                false -> version
            }
        }

    @Input
    val maskedClasses: SetProperty<String> = project.objects.setProperty()

    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generate() {
        val manifest = Manifest()
        manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
        upstreamManifests.asSequence()
                .map { f ->
                    f.inputStream().use {
                        Manifest().apply { read(it) }
                    }
                }
                .map { it.mainAttributes }
                .flatMap { it.entries.asSequence() }
                .filterNot { (k, _) -> k == MANIFEST_VERSION.toString() }
                .forEach { (k, v) ->
                    manifest.mainAttributes.putValue(k.toString(), v.toString())
                }
        groupId.getOrElse("").apply {
            if (isNotEmpty()) {
                manifest.mainAttributes.putValue("Group-Id", groupId.get())
            }
        }
        manifest.mainAttributes.putValue("Minimum-Java-Version", minimumJavaVersion.get())
        manifest.mainAttributes.putValue("Short-Name", pluginId.get())
        manifest.mainAttributes.putValue("Extension-Name", pluginId.get())
        manifest.mainAttributes.putValue("Long-Name", humanReadableName.get())
        manifest.mainAttributes.putValue("Jenkins-Version", jenkinsVersion.get())
        homePage.orNull?.apply {
            manifest.mainAttributes.putValue("Url", toASCIIString())
        }
        minimumJenkinsVersion.orNull?.apply {
            manifest.mainAttributes.putValue("Compatible-Since-Version", this)
        }
        sandboxed.get().apply {
            if (this) {
                manifest.mainAttributes.putValue("Sandbox-Status", this.toString())
            }
        }
        usePluginFirstClassLoader.get().apply {
            if (this) {
                manifest.mainAttributes.putValue("PluginFirstClassLoader", this.toString())
            }
        }
        manifest.mainAttributes.putValue("Plugin-Version", pluginVersion.get())
        maskedClasses.get().apply {
            if (isNotEmpty()) {
                manifest.mainAttributes.putValue("Mask-Classes", joinToString(" "))
            }
        }
        pluginDevelopers.get().apply {
            if (isNotEmpty()) {
                val formatted = joinToString(",") { dev ->
                    listOf(dev.name, dev.id, dev.email).joinToString(":") {
                        it.getOrElse("")
                    }
                }
                manifest.mainAttributes.putValue("Plugin-Developers", formatted)
            }
        }
        outputFile.asFile.get().outputStream().use {
            manifest.write(it)
        }
    }
}
