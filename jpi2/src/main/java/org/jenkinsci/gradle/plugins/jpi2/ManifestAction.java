package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Action to update the JAR manifest with plugin dependencies and other attributes required in a Jenkins Plugin.
 */
class ManifestAction implements Action<Manifest> {
    public static final int DEFAULT_MINIMUM_JAVA_VERSION = 17;
    private final Project project;
    private final Configuration configuration;
    private final JenkinsPluginExtension extension;

    public ManifestAction(Project project, Configuration configuration, JenkinsPluginExtension extension) {
        this.project = project;
        this.configuration = configuration;
        this.extension = extension;
    }

    @Override
    public void execute(@NotNull Manifest manifest) {
        var attributes = manifest.getAttributes();
        attributes.put("Implementation-Title", project.getGroup() + "#" + project.getName() + ";" + project.getVersion());
        attributes.put("Implementation-Version", project.getVersion());

        var rootDependencies = configuration.getIncoming().getResolutionResult().getRoot().getDependencies();

        var pluginDependencies = rootDependencies
                .stream()
                .filter(it -> !it.isConstraint())
                .filter(it -> it instanceof DefaultResolvedDependencyResult)
                .map(it -> ((DefaultResolvedDependencyResult) it))
                .filter(it -> it.getResolvedVariant().getDisplayName().equals(HpiMetadataRule.DEFAULT_RUNTIME_VARIANT))
                .map(it -> it.getSelected().getModuleVersion())
                .filter(Objects::nonNull)
                .map(it -> it.getName() + ":" + it.getVersion())
                .toList();

        if (!pluginDependencies.isEmpty()) {
            attributes.put("Plugin-Dependencies", String.join(",", pluginDependencies));
        }
        attributes.put("Plugin-Version", project.getVersion());
        attributes.put("Short-Name", extension.getPluginId().get());
        attributes.put("Extension-Name", extension.getPluginId().get());
        attributes.put("Group-Id", project.getGroup());
        var ext = project.getExtensions().getByType(JavaPluginExtension.class);
        attributes.put("Minimum-Java-Version", ext.getToolchain().getLanguageVersion()
                .getOrElse(JavaLanguageVersion.of(DEFAULT_MINIMUM_JAVA_VERSION))
                .toString());
        attributes.put("Long-Name", extension.getDisplayName().get());

        attributes.put("Jenkins-Version", extension.getJenkinsVersion());
    }
}
