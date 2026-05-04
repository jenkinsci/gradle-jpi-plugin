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
    /** @return SCM/Maven developer ID */
    @Input
    @Optional
    Property<String> getId();

    /** @return full name of the developer */
    @Input
    @Optional
    Property<String> getName();

    /** @return contact e-mail address */
    @Input
    @Optional
    Property<String> getEmail();

    /** @return personal or profile URL */
    @Input
    @Optional
    Property<String> getUrl();

    /** @return organisation the developer belongs to */
    @Input
    @Optional
    Property<String> getOrganization();

    /** @return URL of the developer's organisation */
    @Input
    @Optional
    Property<String> getOrganizationUrl();

    /** @return roles this developer plays in the project (e.g. {@code developer}, {@code maintainer}) */
    @Input
    @Optional
    SetProperty<String> getRoles();

    /** @return developer's timezone, e.g. {@code America/Los_Angeles} */
    @Input
    @Optional
    Property<String> getTimezone();

    /** @return arbitrary extra key/value pairs included in the POM {@code <developer>} element */
    @Input
    @Optional
    MapProperty<String, String> getProperties();
}
