package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.plugins.ExtensionAware
import java.net.URI
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
     * The URL of the plugin's home page. Written to the `Url` manifest attribute and the POM `<url>`.
     */
    val homePage: Property<URI> = project.objects.property(URI::class.java)

    /**
     * The earliest version of the plugin that is binary-compatible with the current version.
     * Written to the `Compatible-Since-Version` manifest attribute.
     */
    val compatibleSinceVersion: Property<String> = project.objects.property(String::class.java)

    /**
     * When true, the plugin uses a plugin-first class loader instead of the default parent-first loader.
     * Written to the `PluginFirstClassLoader` manifest attribute.
     */
    val pluginFirstClassLoader: Property<Boolean> = project.objects.property(Boolean::class.java)
        .convention(false)

    /**
     * Space-separated list of class prefixes to hide from Jenkins core.
     * Written to the `Mask-Classes` manifest attribute.
     */
    val maskClasses: SetProperty<String> = project.objects.setProperty(String::class.java)

    /**
     * The list of plugin developers. Written to the `Plugin-Developers` manifest attribute
     * and to `<developers>` in the POM. Use [developers] to configure this with a DSL block.
     */
    val pluginDevelopers: ListProperty<PluginDeveloper> = project.objects.listProperty(PluginDeveloper::class.java)

    /**
     * The list of plugin licenses. Written to `<licenses>` in the POM.
     * Use [licenses] to configure this with a DSL block.
     */
    val pluginLicenses: ListProperty<PluginLicense> = project.objects.listProperty(PluginLicense::class.java)

    /**
     * Configures plugin developers using a DSL block.
     *
     * ```kotlin
     * jenkinsPlugin {
     *     developers {
     *         developer {
     *             id.set("me")
     *             name.set("My Name")
     *             email.set("me@example.com")
     *         }
     *     }
     * }
     * ```
     */
    fun developers(action: Action<in PluginDeveloperSpec>) {
        action.execute(object : PluginDeveloperSpec {
            override fun developer(action: Action<in PluginDeveloper>) {
                val dev = project.objects.newInstance(PluginDeveloper::class.java)
                action.execute(dev)
                pluginDevelopers.add(dev)
            }
        })
    }

    /**
     * Configures plugin licenses using a DSL block.
     *
     * ```kotlin
     * jenkinsPlugin {
     *     licenses {
     *         license {
     *             name.set("Apache License, Version 2.0")
     *             url.set("https://www.apache.org/licenses/LICENSE-2.0")
     *             distribution.set("repo")
     *         }
     *     }
     * }
     * ```
     */
    fun licenses(action: Action<in PluginLicenseSpec>) {
        action.execute(object : PluginLicenseSpec {
            override fun license(action: Action<in PluginLicense>) {
                val lic = project.objects.newInstance(PluginLicense::class.java)
                action.execute(lic)
                pluginLicenses.add(lic)
            }
        })
    }

    /**
     * Configuration for Git-based version generation. Used when [versionSource] is [VersionSource.GIT].
     */
    val gitVersion: GitVersionExtension
        get() = (this as ExtensionAware).extensions.getByType(GitVersionExtension::class.java)

    /**
     * Provider for the effective plugin version based on [versionSource].
     * Resolved when the version is first needed (e.g. when building the JPI or generating the manifest).
     * When [VersionSource.GIT], uses [gitVersion][GitVersionExtension].[version][GitVersionExtension.version] (no task required).
     */
    fun getEffectiveVersion(): Provider<String> {
        return versionSource.flatMap { source ->
            when (source) {
                VersionSource.PROJECT -> project.providers.provider { project.version.toString() }
                VersionSource.FIXED -> fixedVersion
                VersionSource.GIT -> gitVersion.version
            }
        }
    }
}
