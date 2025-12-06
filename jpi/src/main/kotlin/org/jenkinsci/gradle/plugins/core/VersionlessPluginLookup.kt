package org.jenkinsci.gradle.plugins.core

import org.gradle.api.artifacts.Configuration
import java.util.concurrent.Callable

@Deprecated("replaced by CreateVersionlessLookupTask")
open class VersionlessPluginLookup(private val configurations: Iterable<Configuration>) : Callable<Map<String, String>> {
    companion object {
        val DEPRECATED: Set<String> = setOf(
                "archives",
                "compile",
                "compileOnly",
                "default",
                "generatedJenkinsTestCompile",
                "generatedJenkinsTestCompileOnly",
                "generatedJenkinsTestRuntime",
                "runtime",
                "testCompile",
                "testCompileOnly",
                "testRuntime"
        )
        val EXTENSIONS: Set<String> = setOf("hpi", "jpi")
    }

    override fun call(): Map<String, String> = configurations.asSequence()
            .filterNot { DEPRECATED.contains(it.name) }
            .filter { it.isCanBeResolved }
            .flatMap { it.resolvedConfiguration.resolvedArtifacts.asSequence() }
            .filter { EXTENSIONS.contains(it.extension) }
            .map { it.file.name to "${it.name}.jpi" }
            .toMap()
}
