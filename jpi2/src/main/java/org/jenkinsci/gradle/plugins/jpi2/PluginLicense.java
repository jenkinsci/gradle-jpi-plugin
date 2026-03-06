package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Represents a plugin license for the plugin POM.
 */
public interface PluginLicense {
    @Input
    @Optional
    Property<String> getName();

    @Input
    @Optional
    Property<String> getUrl();

    @Input
    @Optional
    Property<String> getDistribution();

    @Input
    @Optional
    Property<String> getComments();
}
