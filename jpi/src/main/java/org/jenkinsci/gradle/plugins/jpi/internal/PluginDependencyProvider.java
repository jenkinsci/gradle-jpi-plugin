package org.jenkinsci.gradle.plugins.jpi.internal;

/**
 * Provider for plugin dependency information.
 */
public interface PluginDependencyProvider {
    /** @return the plugin dependencies as a formatted string */
    String pluginDependencies();
}
