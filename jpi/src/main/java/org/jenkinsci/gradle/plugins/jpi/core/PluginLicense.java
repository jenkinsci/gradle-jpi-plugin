package org.jenkinsci.gradle.plugins.jpi.core;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Represents a plugin license for the plugin manifest.
 */
public interface PluginLicense {
    /**
     * @return the license name
     */
    @Input
    @Optional
    Property<String> getName();

    /**
     * @return the license URL
     */
    @Input
    @Optional
    Property<String> getUrl();

    /**
     * @return the distribution type
     */
    @Input
    @Optional
    Property<String> getDistribution();

    /**
     * @return license comments
     */
    @Input
    @Optional
    Property<String> getComments();
}
