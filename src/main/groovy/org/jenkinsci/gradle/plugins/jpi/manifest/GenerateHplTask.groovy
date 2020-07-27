package org.jenkinsci.gradle.plugins.jpi.manifest

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskAction
import org.jenkinsci.gradle.plugins.jpi.JpiPlugin
import org.jenkinsci.gradle.plugins.jpi.internal.FileExistsSpec

import java.util.jar.Manifest

@CompileStatic
class GenerateHplTask extends DefaultTask {
    @InputFile
    final RegularFileProperty manifestFile = project.objects.fileProperty()

    @Input
    final Property<File> resourcePathDir = project.objects.property(File)
            .convention(project.file(JpiPlugin.WEB_APP_DIR))

    @InputFiles
    final Property<SourceSetOutput> mainOutput = project.objects.property(SourceSetOutput)

    @Classpath
    final Property<Configuration> pluginLibraries = project.objects.property(Configuration)

    private final Provider<String> libraries = mainOutput.map {
        def main = it.filter(new FileExistsSpec())
        def all = main + pluginLibraries.get()
        all.join(',')
    }

    @OutputFile
    final RegularFileProperty hpl = project.objects.fileProperty()

    @TaskAction
    void generate() {
        Manifest manifest = new Manifest()
        manifestFile.get().asFile.newInputStream().with {
            def m = new Manifest(it)
            m.mainAttributes.each {
                manifest.mainAttributes.put(it.key, it.value)
            }
        }
        manifest.mainAttributes.putValue('Resource-Path', resourcePathDir.get().absolutePath)
        manifest.mainAttributes.putValue('Libraries', libraries.get())
        File output = hpl.get().asFile
        output.parentFile.mkdirs()
        output.withOutputStream {
            manifest.write(it)
        }
    }
}
