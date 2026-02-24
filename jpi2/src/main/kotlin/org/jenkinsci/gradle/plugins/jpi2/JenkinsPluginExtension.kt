package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.util.concurrent.Callable
import javax.inject.Inject

abstract class JenkinsPluginExtension @Inject constructor(project: Project) {
    val jenkinsVersion: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.gradleProperty(V2JpiPlugin.JENKINS_VERSION_PROPERTY)
                .orElse(V2JpiPlugin.DEFAULT_JENKINS_VERSION)
        )

    val testHarnessVersion: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.gradleProperty(V2JpiPlugin.TEST_HARNESS_VERSION_PROPERTY)
                .orElse(V2JpiPlugin.DEFAULT_TEST_HARNESS_VERSION)
        )

    val pluginId: Property<String> = project.objects.property(String::class.java)
        .convention(project.name)

    val displayName: Property<String> = project.objects.property(String::class.java)
        .convention(
            project.providers.provider<String?>(Callable { project.description })
                .orElse(project.name)
        )
}
