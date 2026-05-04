package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Represents a plugin license for the plugin POM.
 */
public interface PluginLicense {
    /** @return SPDX or human-readable license name */
    @Input
    @Optional
    Property<String> getName();

    /** @return URL to the full license text */
    @Input
    @Optional
    Property<String> getUrl();

    /** @return how the software is distributed under this license, typically {@code repo} or {@code manual} */
    @Input
    @Optional
    Property<String> getDistribution();

    /** @return optional additional information about this license */
    @Input
    @Optional
    Property<String> getComments();
}
