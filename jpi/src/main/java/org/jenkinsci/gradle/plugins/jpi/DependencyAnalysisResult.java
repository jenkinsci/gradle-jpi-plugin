package org.jenkinsci.gradle.plugins.jpi;

/**
 * Represents the result of a dependency analysis for a Jenkins plugin.
 * <p>
 * This class holds information about plugin dependencies that should be
 * included in the plugin manifest.
 */
public class DependencyAnalysisResult {
    private final String manifestPluginDependencies;

    /**
     * Constructs a new dependency analysis result.
     *
     * @param manifestPluginDependencies A string representation of plugin dependencies
     *                                  to be included in the manifest
     */
    public DependencyAnalysisResult(String manifestPluginDependencies) {
        this.manifestPluginDependencies = manifestPluginDependencies;
    }

    /**
     * Gets the plugin dependencies to be included in the manifest.
     *
     * @return A string representation of plugin dependencies
     */
    public String getManifestPluginDependencies() {
        return manifestPluginDependencies;
    }
}
