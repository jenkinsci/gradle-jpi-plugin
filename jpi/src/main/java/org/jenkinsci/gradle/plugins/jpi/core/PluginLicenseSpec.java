package org.jenkinsci.gradle.plugins.jpi.core;

import org.gradle.api.Action;

/**
 * This is aligned with MavenPomLicenseSpec, but not the same
 * because we need every property to be annotated as an Input to
 * serialize as a task input
 */
public interface PluginLicenseSpec {
    /**
     * Configures a license for the plugin.
     *
     * @param action The configuration action to apply to the license
     */
    void license(Action<? super PluginLicense> action);
}
