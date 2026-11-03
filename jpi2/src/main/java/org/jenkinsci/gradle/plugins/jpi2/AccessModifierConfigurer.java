package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Project;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.jenkinsci.gradle.plugins.jpi2.accmod.CheckAccessModifierTask;
import org.jenkinsci.gradle.plugins.jpi2.accmod.PrefixedPropertiesProvider;
import org.jetbrains.annotations.NotNull;

/** Configures restricted-API verification without coupling it to packaging or publishing. */
final class AccessModifierConfigurer {
    private AccessModifierConfigurer() {}

    static void configure(@NotNull Project project) {
        var library = project.getDependencies().create("org.kohsuke:access-modifier-checker:1.35");
        var mavenLog = project.getDependencies().create("org.apache.maven:maven-plugin-api:2.0.1");
        var jenkinsAccessModifier = project.getConfigurations().create("jenkinsAccessModifier", c -> {
            c.getAttributes()
                    .attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.withDependencies(dependencies -> {
                dependencies.add(library);
                dependencies.add(mavenLog);
            });
        });

        var propertyProvider =
                PrefixedPropertiesProvider.gradlePropertiesPrefixedBy(project, CheckAccessModifierTask.PREFIX);
        var checkAccessModifier = project.getTasks()
                .register(CheckAccessModifierTask.NAME, CheckAccessModifierTask.class, task -> {
                    task.setGroup("Verification");
                    task.setDescription(
                            "Checks if Jenkins restricted apis are used (https://tiny.cc/jenkins-restricted).");
                    var dirs = project.getExtensions()
                            .getByType(JavaPluginExtension.class)
                            .getSourceSets()
                            .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                            .getOutput()
                            .getClassesDirs();
                    task.getAccessModifierClasspath().from(jenkinsAccessModifier);
                    task.getAccessModifierProperties().set(propertyProvider);
                    task.getCompileClasspath().from(project.getConfigurations().getByName("compileClasspath"));
                    task.getCompilationDirs().from(dirs);
                    task.getIgnoreFailures().convention(true);
                    task.getOutputDirectory()
                            .set(project.getLayout().getBuildDirectory().dir("access-modifier"));
                    task.getOutputs()
                            .upToDateWhen(element -> !task.getIgnoreFailures().get());
                });

        project.getTasks().named("check", task -> task.dependsOn(checkAccessModifier));
    }
}
