package org.jenkinsci.gradle.plugins.jpi.discovery


import hudson.Extension
import jenkins.YesNoMaybe
import net.java.sezpoz.Index
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class DiscoverDynamicLoadingSupportTask extends DefaultTask {
    static final String TASK_NAME = 'discoverDynamicLoadingSupport'

    @InputFiles
    final Property<FileCollection> classesDirs = project.objects.property(FileCollection)

    @OutputFile
    final Provider<RegularFile> pluginClassFile = project.layout.buildDirectory.dir('discovered').map {
        it.file('dynamic-loading-support.txt')
    }

    @TaskAction
    void discover() {
        def dirs = classesDirs.get()
        ClassLoader classLoader = new URLClassLoader(
                dirs*.toURI()*.toURL() as URL[],
                DiscoverPluginClassTask.classLoader as ClassLoader
        )
        def support = YesNoMaybe.YES
        def enums = Index.load(Extension, Object, classLoader).collect { it.annotation().dynamicLoadable() }
        if (enums.contains(YesNoMaybe.NO)) {
            support = YesNoMaybe.NO
        }
        if (enums.contains(YesNoMaybe.MAYBE)) {
            support = YesNoMaybe.MAYBE
        }

        pluginClassFile.get().asFile.withWriter('UTF-8') { w ->
            w.writeLine(support.name())
        }
    }
}
