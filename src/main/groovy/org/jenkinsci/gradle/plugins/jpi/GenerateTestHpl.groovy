package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * This task is no longer wired to the 'generate-test-hpl' task. It has
 * been replaced by GenerateHplTask, which declares all of its inputs
 * and outputs to take advantage of Gradle's task caching.
 *
 * @see org.jenkinsci.gradle.plugins.jpi.manifest.GenerateHplTask
 * @deprecated To be removed in 1.0.0
 */
@Deprecated
class GenerateTestHpl extends DefaultTask {
    public static final String TASK_NAME = 'generate-test-hpl'

    @OutputDirectory
    final DirectoryProperty hplDir

    GenerateTestHpl() {
        this.hplDir = services.get(ObjectFactory).directoryProperty()
    }

    @TaskAction
    void generateTestHpl() {
        hplDir.file('the.hpl').get().asFile.withOutputStream { new JpiHplManifest(project).write(it) }
    }
}
