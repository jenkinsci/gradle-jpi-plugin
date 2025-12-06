package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

/**
 * Gradle extension for configuring Git version generation.
 * <p>
 * This extension provides configuration options for generating version strings
 * based on Git repository information.
 */
public abstract class GitVersionExtension {

    private static final int DEFAULT_ABBREV_LENGTH = 12;
    private static final String DEFAULT_VERSION_FORMAT = "%d.%s";

    /**
     * Constructs a new Git version extension with default values.
     *
     * @param layout The project layout for accessing project directories
     * @param providers The provider factory for accessing Gradle properties
     */
    @Inject
    public GitVersionExtension(ProjectLayout layout, ProviderFactory providers) {
        getVersionFormat().convention(
                providers.gradleProperty("gitVersionFormat").orElse(DEFAULT_VERSION_FORMAT));
        getAbbrevLength().convention(DEFAULT_ABBREV_LENGTH);
        getAllowDirty().convention(false);
        getGitRoot().convention(layout.getProjectDirectory());
        getSanitize().convention(providers.gradleProperty("gitVersionSanitize").map(p -> true).orElse(false));
        getOutputFile().convention(
                providers.gradleProperty("gitVersionFile")
                        .map(p-> layout.getProjectDirectory().file(p))
                        .orElse(layout.getBuildDirectory().file("generated/version/version.txt")));
        getVersionPrefix().convention("");
    }

    /**
     * Gets the format string for the version.
     * <p>
     * The format string is used with String.format() and receives the commit depth
     * and abbreviated hash as arguments.
     *
     * @return A property containing the version format
     */
    @Optional
    public abstract Property<String> getVersionFormat();

    /**
     * Gets the prefix to use for the version string.
     *
     * @return A property containing the version prefix
     */
    @Optional
    public abstract Property<String> getVersionPrefix();

    /**
     * Gets whether to sanitize the version string.
     * <p>
     * When true, certain characters in the Git hash are replaced to make the
     * version string more compatible with various systems.
     *
     * @return A property indicating whether to sanitize the version
     */
    @Optional
    public abstract Property<Boolean> getSanitize();

    /**
     * Gets the abbreviation length for commit hashes.
     *
     * @return A property containing the abbreviation length
     */
    @Optional
    public abstract Property<Integer> getAbbrevLength();

    /**
     * Gets whether to allow dirty working directory.
     * <p>
     * When false, the version generation will fail if the Git repository
     * has uncommitted changes.
     *
     * @return A property indicating whether to allow dirty working directory
     */
    @Optional
    public abstract Property<Boolean> getAllowDirty();

    /**
     * Gets the root directory of the Git repository.
     *
     * @return A property containing the Git root directory
     */
    @Optional
    public abstract DirectoryProperty getGitRoot();

    /**
     * Gets the output file where the generated version will be written.
     *
     * @return A property containing the output file location
     */
    @Optional
    public abstract RegularFileProperty getOutputFile();


}
