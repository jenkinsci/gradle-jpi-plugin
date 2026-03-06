package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Represents a plugin developer for the plugin manifest and POM.
 */
public interface PluginDeveloper {
    @Input
    @Optional
    Property<String> getId();

    @Input
    @Optional
    Property<String> getName();

    @Input
    @Optional
    Property<String> getEmail();

    @Input
    @Optional
    Property<String> getUrl();

    @Input
    @Optional
    Property<String> getOrganization();

    @Input
    @Optional
    Property<String> getOrganizationUrl();

    @Input
    @Optional
    SetProperty<String> getRoles();

    @Input
    @Optional
    Property<String> getTimezone();

    @Input
    @Optional
    MapProperty<String, String> getProperties();
}
