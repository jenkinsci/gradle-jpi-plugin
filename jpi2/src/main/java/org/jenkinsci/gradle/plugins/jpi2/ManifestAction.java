package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/**
 * Action to update the JAR manifest with attributes required in a Jenkins Plugin, except for
 * {@code Plugin-Dependencies}, which requires resolving a configuration and is set separately at task
 * execution time (see {@link V2JpiPlugin}).
 */
class ManifestAction implements Action<Manifest> {
    public static final int DEFAULT_MINIMUM_JAVA_VERSION = 17;
    private final Project project;
    private final JenkinsPluginExtension extension;

    public ManifestAction(Project project, JenkinsPluginExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    @Override
    public void execute(@NotNull Manifest manifest) {
        var attributes = manifest.getAttributes();
        var version = extension.getEffectiveVersion().get();
        attributes.put("Implementation-Title", project.getGroup() + "#" + project.getName() + ";" + version);
        attributes.put("Implementation-Version", version);
        attributes.put("Plugin-Version", version);
        attributes.put("Short-Name", extension.getPluginId().get());
        attributes.put("Extension-Name", extension.getPluginId().get());
        attributes.put("Group-Id", project.getGroup());
        var ext = project.getExtensions().getByType(JavaPluginExtension.class);
        attributes.put("Minimum-Java-Version", ext.getToolchain().getLanguageVersion()
                .getOrElse(JavaLanguageVersion.of(DEFAULT_MINIMUM_JAVA_VERSION))
                .toString());
        attributes.put("Long-Name", extension.getDisplayName().get());

        attributes.put("Jenkins-Version", extension.getJenkinsVersion());

        var homePageUri = extension.getHomePage().getOrNull();
        if (homePageUri != null) {
            attributes.put("Url", homePageUri.toASCIIString());
        }

        var compatibleSinceVersion = extension.getCompatibleSinceVersion().getOrNull();
        if (compatibleSinceVersion != null) {
            attributes.put("Compatible-Since-Version", compatibleSinceVersion);
        }

        if (extension.getPluginFirstClassLoader().get()) {
            attributes.put("PluginFirstClassLoader", "true");
        }

        var maskClasses = extension.getMaskClasses().get();
        if (!maskClasses.isEmpty()) {
            attributes.put("Mask-Classes", String.join(" ", maskClasses));
        }

        var developers = extension.getPluginDevelopers().get();
        if (!developers.isEmpty()) {
            var formatted = developers.stream()
                    .map(dev -> String.join(":",
                            dev.getName().getOrElse(""),
                            dev.getId().getOrElse(""),
                            dev.getEmail().getOrElse("")))
                    .collect(Collectors.joining(","));
            attributes.put("Plugin-Developers", formatted);
        }
    }
}
