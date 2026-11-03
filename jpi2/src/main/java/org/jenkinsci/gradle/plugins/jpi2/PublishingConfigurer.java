package org.jenkinsci.gradle.plugins.jpi2;

import java.net.URI;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/** Configures Jenkins plugin publications and their POM metadata. */
final class PublishingConfigurer {
    private PublishingConfigurer() {}

    /** Returns the name of the publication that was configured (for task name resolution). */
    @NotNull
    static String configure(
            @NotNull Project project,
            TaskProvider<?> jpiTask,
            Configuration runtimeClasspath,
            JenkinsPluginExtension extension) {
        var publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
        var existingPublication = !publishingExtension.getPublications().isEmpty()
                ? publishingExtension.getPublications().iterator().next()
                : null;
        var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        javaPlugin.withJavadocJar();
        javaPlugin.withSourcesJar();
        if (existingPublication instanceof MavenPublication publication) {
            configurePublication(publication, jpiTask, runtimeClasspath, project, extension);
            return publication.getName();
        } else {
            publishingExtension.getPublications().create("mavenJpi", MavenPublication.class, publication -> {
                publication.from(project.getComponents().getByName("java"));
                configurePublication(publication, jpiTask, runtimeClasspath, project, extension);
            });
            return "mavenJpi";
        }
    }

    private static void configurePublication(
            @NotNull MavenPublication publication,
            TaskProvider<?> jpiTask,
            Configuration runtimeClasspath,
            Project project,
            JenkinsPluginExtension extension) {
        publication.artifact(jpiTask);
        publication.getPom().setPackaging(extension.getArchiveExtension().get());
        publication.getPom().getName().set(extension.getDisplayName());
        publication.getPom().getUrl().set(extension.getHomePage().map(URI::toASCIIString));
        publication.getPom().getDescription().set(project.provider(project::getDescription));
        publication.getPom().withXml(new PomBuilder(runtimeClasspath, project, extension, project.getLogger()));
    }
}
