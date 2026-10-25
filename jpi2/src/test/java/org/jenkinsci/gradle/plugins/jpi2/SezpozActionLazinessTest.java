package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class SezpozActionLazinessTest {

    @Test
    void javaActionDoesNotRealizeCompileTasks() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply(JavaPlugin.class);
        var realizedTasks = new ArrayList<String>();
        project.getTasks().withType(JavaCompile.class).configureEach(task -> realizedTasks.add(task.getName()));

        new SezpozJavaAction(project).execute(project.getPlugins().getPlugin(JavaBasePlugin.class));

        assertThat(realizedTasks).isEmpty();
        project.getTasks().named("compileJava", JavaCompile.class).get();
        assertThat(realizedTasks).contains("compileJava");
    }

    @Test
    void groovyActionDoesNotRealizeCompileTasks() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply(GroovyPlugin.class);
        var realizedTasks = new ArrayList<String>();
        project.getTasks().withType(GroovyCompile.class).configureEach(task -> realizedTasks.add(task.getName()));

        new SezpozGroovyAction(project).execute(project.getPlugins().getPlugin(GroovyBasePlugin.class));

        assertThat(realizedTasks).isEmpty();
        project.getTasks().named("compileGroovy", GroovyCompile.class).get();
        assertThat(realizedTasks).contains("compileGroovy");
    }
}
