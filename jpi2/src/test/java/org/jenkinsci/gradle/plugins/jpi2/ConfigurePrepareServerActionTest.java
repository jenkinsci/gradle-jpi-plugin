package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Sync;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ConfigurePrepareServerActionTest {

    @Test
    void doesNotRealizeJpiTaskWhenConfiguringPrepareServer() {
        var project = ProjectBuilder.builder().build();
        var realizedTasks = new ArrayList<String>();
        var jpiTask = project.getTasks().register("jpi", DefaultTask.class);
        jpiTask.configure(task -> realizedTasks.add(task.getName()));

        var defaultRuntime = project.getConfigurations().create("runtime");
        defaultRuntime.getIncoming().beforeResolve(ignored -> {
            throw new AssertionError("Runtime must not resolve during task configuration");
        });

        var prepareServer = project.getTasks()
                .register(
                        "prepareServer",
                        Sync.class,
                        new ConfigurePrepareServerAction(
                                jpiTask,
                                project.provider(() -> "build/jenkins"),
                                defaultRuntime,
                                project.provider(project::getName),
                                project.provider(() -> "1.0"),
                                project.provider(() -> "jpi")));

        prepareServer.get();

        assertThat(realizedTasks).isEmpty();
        jpiTask.get();
        assertThat(realizedTasks).containsExactly("jpi");
    }

    @Test
    void doesNotResolveRuntimeWhenConfiguringPrepareRun() {
        var project = ProjectBuilder.builder().build();
        var runtime = project.getConfigurations().create("runtime");
        runtime.getIncoming().beforeResolve(ignored -> {
            throw new AssertionError("Runtime must not resolve during task configuration");
        });
        var hpl = project.getTasks().register("hpl", GenerateHplTask.class);
        var task = project.getTasks()
                .register(
                        "prepareRun",
                        Sync.class,
                        new ConfigurePrepareRunAction(hpl, project.provider(() -> "build/jenkins"), runtime));

        assertThat(task.get()).isNotNull();
    }
}
