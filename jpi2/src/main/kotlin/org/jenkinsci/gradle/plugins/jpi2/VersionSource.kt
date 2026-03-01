package org.jenkinsci.gradle.plugins.jpi2

/**
 * Source of the plugin version used for the JPI artifact, manifest, and publishing.
 *
 * - [PROJECT]: Use [org.gradle.api.Project.getVersion] (default).
 * - [FIXED]: Use the value of [JenkinsPluginExtension.fixedVersion].
 * - [GIT]: Use a version computed from the Git repository (similar to the JPI plugin's
 *   `generateGitVersion` task: depth and abbreviated hash, e.g. `1234.abc123def456`).
 */
enum class VersionSource {
    /** Use the Gradle project version. */
    PROJECT,

    /** Use a fixed version string set via [JenkinsPluginExtension.fixedVersion]. */
    FIXED,

    /** Use a version derived from Git (commit depth + abbreviated hash). */
    GIT
}
