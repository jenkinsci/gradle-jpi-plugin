package org.jenkinsci.gradle.plugins.accmod

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * This task is modeled on org.kohsuke:access-modifier-checker
 *
 * @see org.kohsuke.accmod.impl.EnforcerMojo
 */
abstract class CheckAccessModifierTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    companion object {
        const val NAME = "checkAccessModifier"
        const val PREFIX = "$NAME."
    }

    @get:Classpath
    abstract val accessModifierClasspath: ConfigurableFileCollection

    @get:Input
    abstract val accessModifierProperties: MapProperty<String, Any>

    @get:CompileClasspath
    abstract val compileClasspath: ConfigurableFileCollection

    @get:InputFiles
    abstract val compilationDirs: ConfigurableFileCollection

    @get:Input
    abstract val ignoreFailures: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val q = workerExecutor.classLoaderIsolation {
            classpath.from(accessModifierClasspath)
        }
        for (compilationDir in compilationDirs) {
            q.submit(CheckAccess::class) {
                classpathToScan.from(compilationDirs, compileClasspath)
                dirToCheck.set(compilationDir)
                ignoreFailures.set(this@CheckAccessModifierTask.ignoreFailures)
                propertiesForAccessModifier.set(accessModifierProperties)
                outputFile.set(outputDirectory.file("${compilationDir.name}-${compilationDir.parentFile.name}.txt"))
            }
        }
    }
}
