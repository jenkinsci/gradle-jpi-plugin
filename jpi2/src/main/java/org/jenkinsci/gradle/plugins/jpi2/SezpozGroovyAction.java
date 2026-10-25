package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the Groovy compile task for SezPoz.
 */
class SezpozGroovyAction implements Action<GroovyBasePlugin> {
    private final Project project;

    /** Creates an action that enables SezPoz annotation processing for Groovy compilation. */
    public SezpozGroovyAction(Project project) {
        this.project = project;
    }

    @Override
    public void execute(@NotNull GroovyBasePlugin plugin) {
        project.getTasks()
                .named("compileGroovy", GroovyCompile.class)
                .configure(groovyCompile ->
                        groovyCompile.getOptions().getCompilerArgs().add("-Asezpoz.quiet=true"));
        project.getTasks()
                .withType(GroovyCompile.class)
                .configureEach(groovyCompile -> groovyCompile.getGroovyOptions().setJavaAnnotationProcessing(true));
    }
}
