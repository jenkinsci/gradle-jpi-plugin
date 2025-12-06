package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

public interface ArtifactType extends Named {
    Attribute<ArtifactType> ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("org.jenkinsci.gradle.plugins.jpi2.artifact.type", ArtifactType.class);
    String PLUGIN_JAR = "pluginJar";
    String DEFAULT = "default";
}
