package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.Project
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class JenkinsPluginExtension @Inject constructor(project: Project) {

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
}
