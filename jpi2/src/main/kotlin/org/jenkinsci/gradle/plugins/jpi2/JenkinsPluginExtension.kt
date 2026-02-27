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
    }

    val jenkinsVersion: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.gradleProperty(JENKINS_VERSION_PROPERTY)
                .orElse(DEFAULT_JENKINS_VERSION)
        )

    val testHarnessVersion: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.gradleProperty(TEST_HARNESS_VERSION_PROPERTY)
                .orElse(DEFAULT_TEST_HARNESS_VERSION)
        )

    val pluginId: Property<String> = project.objects.property(String::class.java)
        .convention(project.name)

    val displayName: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.provider { project.description ?: project.name }
        )
}
