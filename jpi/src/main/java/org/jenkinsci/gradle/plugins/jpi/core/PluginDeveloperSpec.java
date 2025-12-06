package org.jenkinsci.gradle.plugins.jpi.core;

import org.gradle.api.Action;

/**
 * This is aligned with MavenPomDeveloperSpec, but not the same
 * because we need every property to be annotated as an Input to
 * serialize as a task input
 */
public interface PluginDeveloperSpec {
    /**
     * Configures a developer for the plugin.
     *
     * @param action The configuration action to apply to the developer
     */
    void developer(Action<? super PluginDeveloper> action);
}
