package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
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

        var defaultRuntime = mock(Configuration.class);
        var resolvedRuntime = mock(ResolvedConfiguration.class);
        when(defaultRuntime.getResolvedConfiguration()).thenReturn(resolvedRuntime);
        when(resolvedRuntime.getResolvedArtifacts()).thenReturn(Set.of());

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
}
