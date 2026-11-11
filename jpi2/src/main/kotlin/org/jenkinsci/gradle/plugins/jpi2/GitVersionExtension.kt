package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Configuration for Git-based version generation (used when [JenkinsPluginExtension.versionSource] is [VersionSource.GIT]).
 *
 * The version string is computed from the Git repository: commit depth and abbreviated hash,
 * formatted with [versionFormat] (default `%d.%s` for depth.hash). Written to [outputFile];
 * the first line is the version string used for the plugin.
 */
abstract class GitVersionExtension
    @Inject
    constructor(
        objectFactory: ObjectFactory,
        private val layout: ProjectLayout,
        providers: ProviderFactory,
    ) {
        companion object {
            /** Default format for commit depth and abbreviated hash. */
            const val DEFAULT_VERSION_FORMAT = "%d.%s"

            /** Default number of characters in the abbreviated Git hash. */
            const val DEFAULT_ABBREV_LENGTH = 12
        }

        /**
         * Format string for the version. Receives commit depth (Long) and abbreviated hash (String).
         * Default is [DEFAULT_VERSION_FORMAT] (`%d.%s`). Can be overridden with Gradle property `gitVersionFormat`.
         */
        val versionFormat: Property<String> =
            objectFactory
                .property(String::class.java)
                .convention(providers.gradleProperty("gitVersionFormat").orElse(DEFAULT_VERSION_FORMAT))

        /**
         * Prefix prepended to the formatted version string. Can be set via Gradle property `gitVersionPrefix`.
         */
        val versionPrefix: Property<String> =
            objectFactory
                .property(String::class.java)
                .convention(providers.gradleProperty("gitVersionPrefix").orElse(""))

        /**
         * Length of the abbreviated Git hash. Default [DEFAULT_ABBREV_LENGTH].
         */
        val abbrevLength: Property<Int> =
            objectFactory
                .property(Int::class.javaObjectType)
                .convention(DEFAULT_ABBREV_LENGTH)

        /**
         * Whether to allow a dirty working directory. If false, the task fails when there are uncommitted changes.
         */
        val allowDirty: Property<Boolean> =
            objectFactory
                .property(Boolean::class.javaObjectType)
                .convention(false)

        /**
         * Root of the Git repository. Defaults to the project directory.
         */
        val gitRoot: DirectoryProperty =
            objectFactory
                .directoryProperty()
                .convention(layout.projectDirectory)

        /**
         * Output file for the generated version (first line = version string, second line = full hash).
         * Can be set via Gradle property `gitVersionFile`. Defaults to `build/generated/version/version.txt`.
         */
        val outputFile: RegularFileProperty =
            objectFactory
                .fileProperty()
                .convention(
                    providers
                        .gradleProperty("gitVersionFile")
                        .map { layout.projectDirectory.file(it) }
                        .orElse(layout.buildDirectory.file("generated/version/version.txt")),
                )

        /**
         * Provider for the Git-derived version string (commit depth + abbreviated hash per [versionFormat]).
         * Computed once when first read in a build; no task required.
         * Gradle revalidates Git state before reusing a configuration that consumed the value. Use this as the source of truth
         * for the plugin version when [VersionSource.GIT]. The `generateGitVersion` task still writes
         * this value (and the full hash) to [outputFile] for scripts and compatibility.
         */
        val version: Provider<String> =
            providers
                .of(GitVersionValueSource::class.java) {
                    parameters.gitRoot.set(gitRoot)
                    parameters.versionFormat.set(versionFormat)
                    parameters.versionPrefix.set(versionPrefix)
                    parameters.abbrevLength.set(abbrevLength)
                    parameters.allowDirty.set(allowDirty)
                }.map { it.version() }
    }