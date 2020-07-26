package org.jenkinsci.gradle.plugins.jpi.manifest

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jenkinsci.gradle.plugins.jpi.internal.VersionCalculator

import java.util.jar.Manifest

@CompileStatic
class GenerateManifestTask extends DefaultTask {
    @InputFile
    final RegularFileProperty pluginClassFile = project.objects.fileProperty()

    @InputFile
    final RegularFileProperty dynamicLoadingSupportFile = project.objects.fileProperty()

    @Input
    final Property<String> groupId = project.objects.property(String)

    @Input
    final Property<String> shortName = project.objects.property(String)

    @Input
    final Property<String> longName = project.objects.property(String).convention(shortName)

    @Input
    @Optional
    final Property<String> url = project.objects.property(String)

    @Input
    @Optional
    final Property<String> compatibleSinceVersion = project.objects.property(String)

    @Input
    final Property<Boolean> sandboxStatus = project.objects.property(Boolean)

    @Input
    final Property<String> pluginVersion = project.objects.property(String)

    @Input
    final Property<String> jenkinsVersion = project.objects.property(String)

    @Input
    final Property<String> minimumJavaVersion = project.objects.property(String)

    @Input
    @Optional
    final Property<String> maskClasses = project.objects.property(String)

    @Input
    final Property<String> pluginDependencies = project.objects.property(String)

    @Input
    final Property<Boolean> pluginFirstClassLoader = project.objects.property(Boolean).convention(false)

    @Input
    final Property<String> pluginDevelopers = project.objects.property(String)

    @OutputFile
    final Provider<RegularFile> manifestFile = project.layout.buildDirectory.dir('discovered').map {
        it.file('manifest.mf')
    }

    @Internal
    final Provider<String> enrichedVersion = pluginVersion.map { new VersionCalculator().calculate(it) }

    @TaskAction
    void generate() {
        def manifest = new Manifest()
        [pluginClassFile, dynamicLoadingSupportFile].each {
            it.get().asFile.newInputStream().with {
                def m = new Manifest(it)
                m.mainAttributes.each {
                    manifest.mainAttributes.put(it.key, it.value)
                }
            }
        }
        def foundGroupId = groupId.getOrElse('')
        if (!foundGroupId.isEmpty()) {
            manifest.mainAttributes.putValue('Group-Id', foundGroupId)
        }
        def shortNameValue = shortName.get()
        manifest.mainAttributes.putValue('Short-Name', shortNameValue)
        manifest.mainAttributes.putValue('Long-Name', longName.get())
        if (url.isPresent()) {
            manifest.mainAttributes.putValue('Url', url.get())
        }
        if (compatibleSinceVersion.isPresent()) {
            manifest.mainAttributes.putValue('Compatible-Since-Version', compatibleSinceVersion.get())
        }
        def status = sandboxStatus.getOrElse(false)
        if (status) {
            manifest.mainAttributes.putValue('Sandbox-Status', String.valueOf(status))
        }
        manifest.mainAttributes.putValue('Extension-Name', shortNameValue)
        manifest.mainAttributes.putValue('Plugin-Version', enrichedVersion.get())
        manifest.mainAttributes.putValue('Jenkins-Version', jenkinsVersion.get())
        manifest.mainAttributes.putValue('Minimum-Java-Version', minimumJavaVersion.get())
        if (maskClasses.isPresent()) {
            manifest.mainAttributes.putValue('Mask-Classes', String.valueOf(maskClasses.get()))
        }
        if (pluginDependencies.isPresent()) {
            def dep = pluginDependencies.get()
            if (dep.length() > 0) {
                manifest.mainAttributes.putValue('Plugin-Dependencies', dep)
            }
        }
        def classLoader = pluginFirstClassLoader.get()
        if (classLoader) {
            manifest.mainAttributes.putValue('PluginFirstClassLoader', String.valueOf(classLoader))
        }

        def developers = pluginDevelopers.get()
        if (!developers.isEmpty()) {
            manifest.mainAttributes.putValue('Plugin-Developers', developers)
        }

        manifestFile.get().asFile.withOutputStream {
            manifest.write(it)
        }
    }
}
