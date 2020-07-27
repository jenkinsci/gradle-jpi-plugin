package org.jenkinsci.gradle.plugins.jpi.server

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jenkinsci.gradle.plugins.jpi.JpiHplManifest

/**
 * This task is no longer wired to the 'generateJenkinsServerHpl' task. It has
 * been replaced by GenerateHplTask, which declares all of its inputs
 * and outputs to take advantage of Gradle's task caching.
 *
 * @see org.jenkinsci.gradle.plugins.jpi.manifest.GenerateHplTask
 * @deprecated To be removed in 1.0.0
 */
@Deprecated
@CompileStatic
class GenerateJenkinsServerHplTask extends DefaultTask {
    static final String TASK_NAME = 'generateJenkinsServerHpl'
    @Input
    final Property<String> fileName = project.objects.property(String)

    @OutputFile
    final Provider<RegularFile> hpl = fileName.flatMap {
        project.layout.buildDirectory.file("hpl/${it}.hpl")
    }

    @TaskAction
    void generate() {
        def destination = hpl.get().asFile
        destination.parentFile.mkdirs()
        destination.withOutputStream {
            new JpiHplManifest(project).write(it)
        }
    }
}
