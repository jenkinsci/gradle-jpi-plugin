package org.jenkinsci.gradle.plugins.manifest

import hudson.Extension
import jenkins.YesNoMaybe
import net.java.sezpoz.Index
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.Attributes
import java.util.jar.Manifest

open class GenerateSupportDynamicLoadingManifestTask : DefaultTask() {
    companion object {
        const val NAME: String = "generateJenkinsSupportDynamicLoadingManifest"
    }

    @InputFiles
    val classesDirs: ConfigurableFileCollection = project.objects.fileCollection()

    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generate() {
        val classes: Array<URL> = classesDirs.asSequence()
                .map { it.toURI().toURL() }
                .toList()
                .toTypedArray()
        val classLoader = URLClassLoader(classes, javaClass.classLoader)
        val extensions = Index.load(Extension::class.java, Object::class.java, classLoader)
                .map { it.annotation().dynamicLoadable }

        val supported: Boolean? = when {
            extensions.any { it == YesNoMaybe.NO } -> false
            extensions.any { it == YesNoMaybe.MAYBE } -> null
            else -> true
        }

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        if (supported != null) {
            manifest.mainAttributes.putValue("Support-Dynamic-Loading", supported.toString())
        }
        outputFile.asFile.get().outputStream().use {
            manifest.write(it)
        }
    }
}
