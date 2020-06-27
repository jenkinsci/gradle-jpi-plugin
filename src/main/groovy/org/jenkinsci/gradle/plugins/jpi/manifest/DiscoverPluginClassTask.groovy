package org.jenkinsci.gradle.plugins.jpi.manifest

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.jar.Manifest

@CompileStatic
class DiscoverPluginClassTask extends DefaultTask {
    static final String TASK_NAME = 'discoverPluginClass'

    @InputFiles
    final Property<FileCollection> classesDirs = project.objects.property(FileCollection)

    @OutputFile
    final Provider<RegularFile> outputFile = project.layout.buildDirectory.dir('discovered').map {
        it.file('plugin-class.mf')
    }

    @TaskAction
    void validate() {
        def dirs = classesDirs.get()
        List<File> pluginImpls = dirs.collect {
            new File(it, 'META-INF/services/hudson.Plugin')
        }.findAll {
            it.exists()
        }

        if (pluginImpls.size() > 1) {
            throw new GradleException(
                    'Found multiple directories containing Jenkins plugin implementations ' +
                            "('${pluginImpls*.path.join("', '")}'). " +
                            'Use joint compilation to work around this problem.'
            )
        }

        def manifest = new Manifest()
        manifest.mainAttributes.putValue('Manifest-Version', '1.0')
        def pluginImpl = pluginImpls.find()
        if (pluginImpl?.exists()) {
            manifest.mainAttributes.putValue('Plugin-Class', pluginImpl.readLines('UTF-8')[0])
        }

        outputFile.get().asFile.withOutputStream {
            manifest.write(it)
        }
    }
}
