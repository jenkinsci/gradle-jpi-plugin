package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.JavaExec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ServerActionTest {
    @Test
    void defaultLaunchDirectoryTracksBuildDirectory() {
        var project = ProjectBuilder.builder().build();
        var launchDir = WorkDirectorySettings.getLaunchDir(project);
        project.getLayout()
                .getBuildDirectory()
                .set(project.getLayout().getProjectDirectory().dir("custom-build"));
        assertThat(launchDir.get())
                .isEqualTo(project.getLayout()
                        .getProjectDirectory()
                        .dir("custom-build/jenkins")
                        .getAsFile()
                        .getAbsolutePath());
    }

    @Test
    void launchDirectoriesCanBeIsolatedForEitherServerMode() {
        var project = ProjectBuilder.builder().build();
        var classpath = project.getConfigurations().create("serverClasspath");
        for (var name : new String[] {"server", "hplRun"}) {
            var prepare = project.getTasks().register("prepare" + name, DefaultTask.class);
            var task = project.getTasks().create(name, JavaExec.class);
            new ServerAction(
                            classpath,
                            project.provider(() -> "/home"),
                            project.provider(() -> "0"),
                            project.provider(() -> "/isolated"),
                            prepare)
                    .execute(task);
            assertThat(task.getArgs())
                    .contains(
                            "--webroot=/isolated/war",
                            "--pluginroot=/isolated/plugins",
                            "--extractedFilesFolder=/isolated/extracted",
                            "--commonLibFolder=/home/lib");
            assertThat(task.getEnvironment()).containsEntry("JENKINS_HOME", "/home");
        }
    }
}
