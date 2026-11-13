package org.jenkinsci.gradle.plugins.jpi2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/** Configures the common Jenkins plugin manifest attributes. */
final class JenkinsManifestAttributes {
    /** Minimum Java version used when no Java toolchain version is configured. */
    public static final int DEFAULT_MINIMUM_JAVA_VERSION = 17;

    private JenkinsManifestAttributes() {}

    static void resolveProviders(Manifest manifest) {
        resolveAttributes(manifest.getAttributes());
        manifest.getSections().values().forEach(JenkinsManifestAttributes::resolveAttributes);
    }

    private static void resolveAttributes(Map<String, Object> attributes) {
        var entries = attributes.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var value = entry.getValue();
            while (value instanceof Provider<?> provider) {
                value = provider.getOrNull();
            }
            if (value == null) {
                entries.remove();
            } else {
                entry.setValue(value);
            }
        }
    }

    static Map<String, Object> attributes(Project project, JenkinsPluginExtension extension) {
        var attributes = new LinkedHashMap<String, Object>();
        var version = extension.getEffectiveVersion();
        attributes.put(
                "Implementation-Title",
                project.provider(() -> project.getGroup() + "#" + project.getName() + ";" + version.get()));
        attributes.put("Implementation-Version", version);
        attributes.put("Plugin-Version", version);
        attributes.put("Short-Name", extension.getPluginId());
        attributes.put("Extension-Name", extension.getPluginId());
        attributes.put("Group-Id", project.provider(() -> project.getGroup().toString()));
        var ext = project.getExtensions().getByType(JavaPluginExtension.class);
        attributes.put(
                "Minimum-Java-Version",
                project.provider(() -> ext.getToolchain()
                        .getLanguageVersion()
                        .getOrElse(JavaLanguageVersion.of(DEFAULT_MINIMUM_JAVA_VERSION))
                        .toString()));
        attributes.put("Long-Name", extension.getDisplayName());

        attributes.put("Jenkins-Version", extension.getJenkinsVersion());

        attributes.put("Url", extension.getHomePage().map(uri -> uri.toASCIIString()));
        attributes.put("Compatible-Since-Version", extension.getCompatibleSinceVersion());
        attributes.put(
                "PluginFirstClassLoader",
                extension.getPluginFirstClassLoader().map(enabled -> enabled ? "true" : null));
        attributes.put(
                "Mask-Classes",
                extension.getMaskClasses().map(classes -> classes.isEmpty() ? null : String.join(" ", classes)));
        attributes.put(
                "Plugin-Developers",
                extension
                        .getPluginDevelopers()
                        .map(developers -> developers.isEmpty()
                                ? null
                                : developers.stream()
                                        .map(dev -> String.join(
                                                ":",
                                                dev.getName().getOrElse(""),
                                                dev.getId().getOrElse(""),
                                                dev.getEmail().getOrElse("")))
                                        .collect(Collectors.joining(","))));
        return attributes;
    }
}
