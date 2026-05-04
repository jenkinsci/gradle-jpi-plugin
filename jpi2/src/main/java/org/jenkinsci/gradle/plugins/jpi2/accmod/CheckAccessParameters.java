package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

/**
 * Parameters passed to the {@link CheckAccess} work action via Gradle's worker API.
 */
public interface CheckAccessParameters extends WorkParameters {
    /** @return extra properties forwarded to {@code kohsuke.accmod.Checker} */
    MapProperty<String, Object> getPropertiesForAccessModifier();

    /** @return full classpath (compiled classes + compile deps) for type resolution during scanning */
    ConfigurableFileCollection getClasspathToScan();

    /** @return the specific compiled-classes directory being inspected for violations */
    DirectoryProperty getDirToCheck();

    /** @return {@code true} to treat violations as warnings rather than build errors */
    Property<Boolean> getIgnoreFailures();

    /** @return file where this work item writes its violation report */
    RegularFileProperty getOutputFile();
}
