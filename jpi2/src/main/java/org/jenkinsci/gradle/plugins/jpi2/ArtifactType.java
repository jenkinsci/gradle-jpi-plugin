package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * Defines artifact types for Jenkins plugin dependencies.
 */
public interface ArtifactType extends Named {
    /** Gradle attribute used to distinguish artifact types in dependency resolution. */
    Attribute<ArtifactType> ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("org.jenkinsci.gradle.plugins.jpi2.artifact.type", ArtifactType.class);
    /** Artifact type for Jenkins plugin JAR files. */
    String PLUGIN_JAR = "pluginJar";
    /** Default artifact type. */
    String DEFAULT = "default";
}
