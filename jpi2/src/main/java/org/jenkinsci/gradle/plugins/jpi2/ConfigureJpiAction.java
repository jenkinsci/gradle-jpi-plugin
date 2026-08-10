package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the JPI task for a Jenkins plugin.
 */
@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class ConfigureJpiAction implements Action<War> {
    private final Project project;
    private final Configuration configuration;
    private final Configuration jenkinsCore;
    private final JenkinsPluginExtension extension;

    public ConfigureJpiAction(Project project, Configuration configuration, Configuration jenkinsCore, JenkinsPluginExtension extension) {
        this.project = project;
        this.configuration = configuration;
        this.jenkinsCore = jenkinsCore;
        this.extension = extension;
    }

    @Override
    public void execute(@NotNull War jpi) {
        jpi.getArchiveExtension().set(extension.getArchiveExtension());
        jpi.manifest(new ManifestAction(project, extension));

        // Resolving `configuration` must wait until the jpi task actually executes: some publishing
        // plugins (e.g. com.jfrog.artifactory) realize this task from a gradle.projectsEvaluated
        // listener, before projects are configured and before Gradle's exclusive project-execution
        // lock is available, and an eager resolution there is rejected as unsafe.
        var pluginDependencies = project.provider(() -> V2JpiPlugin.resolvePluginDependencies(configuration));
        jpi.getInputs().property("pluginDependencies", pluginDependencies).optional(true);
        jpi.doFirst(new Action<>() {
            @Override
            public void execute(@NotNull Task task) {
                var value = pluginDependencies.getOrNull();
                if (value != null) {
                    jpi.getManifest().getAttributes().put("Plugin-Dependencies", value);
                }
            }
        });
        jpi.from(project.getTasks().named("jar"), new Action<>() {
            @Override
            public void execute(@NotNull CopySpec copySpec) {
                copySpec.into("WEB-INF/lib");
            }
        });
        jpi.from(project.file("src/main/webapp"), new Action<>() {
            @Override
            public void execute(@NotNull CopySpec copySpec) {
                copySpec.into("");
            }
        });
        var runtimeClasspathArtifacts = new RuntimeClasspathArtifacts(project, configuration, jenkinsCore);
        jpi.setClasspath(runtimeClasspathArtifacts.getBundledLibraries());
        jpi.finalizedBy(V2JpiPlugin.EXPLODED_JPI_TASK);
    }
}
