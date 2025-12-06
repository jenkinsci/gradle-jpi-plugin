package org.jenkinsci.gradle.plugins.testing

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.mapProperty
import javax.inject.Inject

open class CopyTestPluginDependenciesTask @Inject constructor(private val fileSystemOperations: FileSystemOperations) : DefaultTask() {
    @Internal
    val files: ConfigurableFileCollection = project.objects.fileCollection()

    @InputFiles
    val plugins: FileCollection = files.filter { it.path.endsWith(".hpi") || it.path.endsWith(".jpi") }

    @Internal
    @Deprecated("replaced by dedicated task")
    val versionlessPluginLookup: MapProperty<String, String> = project.objects.mapProperty()

    @InputFile
    val versionlessLookupFile: RegularFileProperty = project.objects.fileProperty()

    @OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @Internal
    val index: Provider<RegularFile> = outputDir.file("index")

    @TaskAction
    fun go() {
        val lookup: Map<String, String> = versionlessLookupFile.asFile.get().readLines().associate {
            val (version, versionless) = it.split("\t".toRegex(), 2)
            version to versionless
        }
        fileSystemOperations.copy {
            from(plugins)
            into(outputDir)
            rename {
                lookup[it] ?: it
            }
        }
        index.get().asFile.bufferedWriter().use { w ->
            plugins.mapNotNull { lookup[it.name]?.substringBeforeLast(".jpi") }.forEach {
                w.write(it)
                w.write("\n")
            }
        }
    }
}
