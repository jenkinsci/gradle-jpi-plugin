package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

public interface CheckAccessParameters extends WorkParameters {
    MapProperty<String, Object> getPropertiesForAccessModifier();

    ConfigurableFileCollection getClasspathToScan();

    DirectoryProperty getDirToCheck();

    Property<Boolean> getIgnoreFailures();

    RegularFileProperty getOutputFile();
}
