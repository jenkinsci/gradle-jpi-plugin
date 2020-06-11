package org.jenkinsci.gradle.plugins.jpi.discovery

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

@CompileStatic
class DiscoverPluginClassTask extends DefaultTask {
    static final String TASK_NAME = 'discoverPluginClass'

    @InputFiles
    final Property<FileCollection> classesDirs = project.objects.property(FileCollection)

    @OutputFile
    final Provider<RegularFile> pluginClassFile = project.layout.buildDirectory.dir('discovered').map {
        it.file('plugin-class.txt')
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

        pluginClassFile.get().asFile.withWriter('UTF-8') { w ->
            pluginImpls.each { servicesFile ->
                servicesFile.eachLine {
                    w.writeLine(it)
                }
            }
        }
    }
}
