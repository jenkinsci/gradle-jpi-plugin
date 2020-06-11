package org.jenkinsci.gradle.plugins.jpi.verification

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
class CheckOverlappingSourcesTask extends DefaultTask {
    static final String TASK_NAME = 'checkOverlappingSources'

    @InputFiles
    final Property<FileCollection> classesDirs = project.objects.property(FileCollection)

    @OutputFile
    final Provider<RegularFile> outputFile = project.layout.buildDirectory.dir('check-overlap').map {
        it.file('discovered.txt')
    }

    @TaskAction
    void validate() {
        List<File> discovered = []
        Set<String> existingSezpozFiles = []
        def dirs = classesDirs.get()
        dirs.each { classDir ->
            def annotationsDir = new File(classDir, 'META-INF/annotations')
            def files = annotationsDir.list()
            if (files == null) {
                return
            }
            files.each {
                def path = new File(annotationsDir, it)
                discovered.add(path)
                if (!path.isFile()) {
                    return
                }
                if (existingSezpozFiles.contains(it)) {
                    throw new GradleException("Found overlapping Sezpoz file: ${it}. Use joint compilation!")
                }
                existingSezpozFiles.add(it)
            }
        }

        outputFile.get().asFile.withWriter('UTF-8') { w ->
            discovered.each {
                w.append(it.absolutePath).append('\n')
            }
        }
    }
}
