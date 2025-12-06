package org.jenkinsci.gradle.plugins.jpi.core;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

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
