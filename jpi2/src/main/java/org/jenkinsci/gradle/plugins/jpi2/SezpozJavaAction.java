package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the Java compile task for SezPoz.
 */
@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class SezpozJavaAction implements Action<JavaBasePlugin> {
    private final Project project;

    public SezpozJavaAction(Project project) {
        this.project = project;
    }

    @Override
    public void execute(@NotNull JavaBasePlugin plugin) {
        project.getTasks().named("compileJava", JavaCompile.class).configure(new Action<>() {
            @Override
            public void execute(@NotNull JavaCompile javaCompile) {
                javaCompile.getOptions().getCompilerArgs().add("-Asezpoz.quiet=true");
            }
        });
        project.getTasks().withType(JavaCompile.class, new Action<>() {
            @Override
            public void execute(@NotNull JavaCompile javaCompile) {
                javaCompile.getOptions().getCompilerArgs().add("-parameters");
            }
        });
    }
}
