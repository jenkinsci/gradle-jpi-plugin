package org.jenkinsci.gradle.plugins.testing

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

open class GenerateTestTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    @OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @Input
    val requireEscapeByDefaultInJelly: Property<Boolean> = project.objects.property()

    @Input
    val mainResourcesOutputDir: Property<File> = project.objects.property()

    @Input
    val pluginId: Property<String> = project.objects.property()

    @Input
    val baseDir: Property<File> = project.objects.property()

    @Classpath
    val generatorClasspath: ConfigurableFileCollection = project.objects.fileCollection()

    @Input
    val testName: Property<String> = project.objects.property()

    @TaskAction
    fun generate() {
        val q = workerExecutor.classLoaderIsolation {
            classpath.from(generatorClasspath)
        }
        val params: Map<String, String> = mapOf(
                "basedir" to baseDir.get().absolutePath,
                "artifactId" to pluginId.get(),
                "outputDirectory" to mainResourcesOutputDir.get().absolutePath,
                "requirePI" to requireEscapeByDefaultInJelly.get().toString()
        )
        q.submit(GenerateTest::class.java) {
            outputDir.set(this@GenerateTestTask.outputDir)
            pluginId.set(this@GenerateTestTask.pluginId)
            testName.set(this@GenerateTestTask.testName)
            testParameters.set(params)
        }
    }
}
