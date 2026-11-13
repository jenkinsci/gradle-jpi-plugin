package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the JPI task for a Jenkins plugin.
 */
class ConfigureJpiAction implements Action<War> {
    private final Project project;
    private final FileCollection bundledLibraries;
    private final JenkinsPluginExtension extension;

    /** Creates an action that packages the plugin and its runtime dependencies. */
    public ConfigureJpiAction(Project project, FileCollection bundledLibraries, JenkinsPluginExtension extension) {
        this.project = project;
        this.bundledLibraries = bundledLibraries;
        this.extension = extension;
    }

    @Override
    public void execute(@NotNull War jpi) {
        jpi.getArchiveExtension().set(extension.getArchiveExtension());
        jpi.getManifest().attributes(JenkinsManifestAttributes.attributes(project, extension));
        jpi.from(project.getTasks().named("jar"), copySpec -> copySpec.into("WEB-INF/lib"));
        jpi.from(project.file("src/main/webapp"), copySpec -> copySpec.into(""));
        jpi.setClasspath(bundledLibraries);
        jpi.finalizedBy(V2JpiPlugin.EXPLODED_JPI_TASK);
    }
}
