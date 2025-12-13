package org.jenkinsci.gradle.plugins.jpi.core;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Represents a plugin developer for the plugin manifest.
 */
public interface PluginDeveloper {
    /** @return the developer ID */
    @Input
    @Optional
    Property<String> getId();

    /** @return the developer name */
    @Input
    @Optional
    Property<String> getName();

    /** @return the developer email address */
    @Input
    @Optional
    Property<String> getEmail();

    /** @return the developer URL */
    @Input
    @Optional
    Property<String> getUrl();

    /** @return the developer's organization */
    @Input
    @Optional
    Property<String> getOrganization();

    /** @return the developer's organization URL */
    @Input
    @Optional
    Property<String> getOrganizationUrl();

    /** @return the developer's roles */
    @Input
    @Optional
    SetProperty<String> getRoles();

    /** @return the developer's timezone */
    @Input
    @Optional
    Property<String> getTimezone();

    /** @return additional developer properties */
    @Input
    @Optional
    MapProperty<String, String> getProperties();
}
