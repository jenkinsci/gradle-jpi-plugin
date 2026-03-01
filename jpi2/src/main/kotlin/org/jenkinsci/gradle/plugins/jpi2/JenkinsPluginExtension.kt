package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.provider.Property
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

abstract class JenkinsPluginExtension @Inject constructor(private val project: Project) {

    companion object {
        const val JENKINS_VERSION_PROPERTY = "jenkins.version"
        const val DEFAULT_JENKINS_VERSION = "2.492.3"

        const val TEST_HARNESS_VERSION_PROPERTY = "jenkins.testharness.version"
        const val DEFAULT_TEST_HARNESS_VERSION = "2414.v185474555e66"

        const val DEFAULT_ARCHIVE_EXTENSION = "jpi"
    }

    /**
     * The file extension used for the plugin archive (e.g. `jpi` or `hpi`).
     * Defaults to `jpi`.
     */
    val archiveExtension: Property<String> = project.objects.property(String::class.java)
        .convention(DEFAULT_ARCHIVE_EXTENSION)

    /**
     * The version of Jenkins core to compile and run against.
     * Can be set via the [JENKINS_VERSION_PROPERTY] Gradle property.
     * Defaults to [DEFAULT_JENKINS_VERSION].
     */
    val jenkinsVersion: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.gradleProperty(JENKINS_VERSION_PROPERTY)
                .orElse(DEFAULT_JENKINS_VERSION)
        )

    /**
     * The version of the Jenkins test harness used for integration tests.
     * Can be set via the [TEST_HARNESS_VERSION_PROPERTY] Gradle property.
     * Defaults to [DEFAULT_TEST_HARNESS_VERSION].
     */
    val testHarnessVersion: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.gradleProperty(TEST_HARNESS_VERSION_PROPERTY)
                .orElse(DEFAULT_TEST_HARNESS_VERSION)
        )

    /**
     * The short name (ID) of the plugin, used in the manifest and for the plugin URL.
     * Defaults to the project name.
     */
    val pluginId: Property<String> = project.objects.property(String::class.java)
        .convention(project.name)

    /**
     * The human-readable display name of the plugin.
     * Defaults to the project description, or the project name if no description is set.
     */
    val displayName: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.provider { project.description ?: project.name }
        )

    /**
     * Source of the plugin version: [VersionSource.PROJECT] (default), [VersionSource.FIXED], or [VersionSource.GIT].
     * When [VersionSource.FIXED], [fixedVersion] is used. When [VersionSource.GIT], a version is computed from Git
     * (see [gitVersion] and the `generateGitVersion` task).
     */
    val versionSource: Property<VersionSource> = project.objects.property(VersionSource::class.java)
        .convention(VersionSource.PROJECT)

    /**
     * Fixed version string used when [versionSource] is [VersionSource.FIXED].
     */
    val fixedVersion: Property<String> = project.objects.property(String::class.java)

    /**
     * Configuration for Git-based version generation. Used when [versionSource] is [VersionSource.GIT].
     */
    val gitVersion: GitVersionExtension
        get() = (this as ExtensionAware).extensions.getByType(GitVersionExtension::class.java)

    /**
     * Provider for the effective plugin version based on [versionSource].
     * Resolved when the version is first needed (e.g. when building the JPI or generating the manifest).
     * When [VersionSource.GIT], the provider depends on [generateGitVersionTask], so that task runs first.
     *
     * @param generateGitVersionTask The registered `generateGitVersion` task, or null if not using GIT.
     */
    fun getEffectiveVersion(generateGitVersionTask: TaskProvider<out Task>?): Provider<String> {
        return project.providers.provider { versionSource.get() }.flatMap { source ->
            when (source) {
                VersionSource.PROJECT -> project.providers.provider { project.version.toString() }
                VersionSource.FIXED -> fixedVersion
                VersionSource.GIT -> {
                    if (generateGitVersionTask != null) {
                        generateGitVersionTask.flatMap {
                            project.providers.fileContents(gitVersion.outputFile)
                                .asText
                                .map { text -> text.lines().first().trim() }
                        }
                    } else {
                        project.providers.provider { project.version.toString() }
                    }
                }
            }
        }
    }
}
