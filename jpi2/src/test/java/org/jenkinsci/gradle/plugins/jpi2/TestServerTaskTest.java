package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class TestServerTaskTest {

    @Test
    void recognizesSuccessfulStartup() {
        assertThat(TestServerTask.verdictForLine("INFO Jenkins is fully up and running"))
                .isEqualTo(new TestServerTask.LaunchResult(TestServerTask.Status.SUCCESS, 0, null));
    }

    @Test
    void recognizesKnownStartupFailures() {
        for (var message :
                new String[] {"Failed Loading plugin", "Jenkins stopped", "java.io.IOException: Failed to load"}) {
            var line = "ERROR " + message;
            assertThat(TestServerTask.verdictForLine(line))
                    .isEqualTo(new TestServerTask.LaunchResult(TestServerTask.Status.CRASH, -1, line));
        }
    }

    @Test
    void ignoresOutputWithoutAStartupVerdict() {
        assertThat(TestServerTask.verdictForLine("INFO Jenkins is starting")).isNull();
    }

    @TestFactory
    Stream<DynamicTest> forwardsPropertyValuesVerbatim() {
        return Stream.of("\\d+", "domain\\user", "{\"line\":\"\\n\"}")
                .map(value -> dynamicTest(value, () -> {
                    var task = task();
                    task.getSystemProperties().set(Map.of("systemValue", value));
                    task.getProjectProperties().set(Map.of("projectValue", value));

                    assertThat(task.getCommandLine(Path.of("work")))
                            .contains("-DsystemValue=" + value, "-PprojectValue=" + value);
                }));
    }

    @Test
    void normalizesKnownPathsAndOverridesWorkDirectory() {
        var task = task();
        task.getSystemProperties().set(Map.of(WorkDirectorySettings.PROPERTY, "system-work"));
        task.getProjectProperties().set(Map.of(WorkDirectorySettings.PROPERTY, "project-work"));
        var workDir = Path.of("verification-work");

        assertThat(task.getCommandLine(workDir))
                .contains("C:/gradle/bin/gradle", "-Dorg.gradle.java.home=C:/java")
                .contains("-P" + WorkDirectorySettings.PROPERTY + "="
                        + workDir.toAbsolutePath().toString().replace('\\', '/'))
                .doesNotContain(
                        "-D" + WorkDirectorySettings.PROPERTY + "=system-work",
                        "-P" + WorkDirectorySettings.PROPERTY + "=project-work");
    }

    @Test
    void usesTheJavaLauncherForWindowsArguments(@TempDir Path root) throws Exception {
        var library = Files.createDirectories(root.resolve("gradle/lib"));
        Files.write(library.resolve("gradle-launcher-8.14.jar"), new byte[0]);
        var task = task();
        var value = "{\"line\":\"\\n\"}";
        task.getGradleExecutable().set(root.resolve("gradle/bin/gradle.bat").toString());
        task.getJavaHome().set(root.resolve("java").toString());
        task.getSystemProperties().set(Map.of("systemValue", value));
        task.getProjectProperties().set(Map.of("projectValue", value));

        assertThat(task.getCommandLine(root.resolve("work")))
                .contains("org.gradle.launcher.GradleMain", "-DsystemValue=" + value, "-PprojectValue=" + value)
                .doesNotContain(task.getGradleExecutable().get());
    }

    private static TestServerTask task() {
        var project = ProjectBuilder.builder().build();
        var task = project.getTasks()
                .register("verifyServer", TestServerTask.class)
                .get();
        task.getGradleExecutable().set("C:\\gradle\\bin\\gradle");
        task.getJavaHome().set("C:\\java");
        task.getGradleUserHome().set("C:\\gradle-home");
        task.getInitScriptPaths().set(List.of());
        task.getIncludedBuilds().set(List.of());
        task.getOffline().set(false);
        task.getBuildCacheEnabled().set(false);
        task.getRefreshDependencies().set(false);
        task.getContinueOnFailure().set(false);
        task.getParallelExecution().set(false);
        task.getProfile().set(false);
        task.getDryRun().set(false);
        task.getServerTaskPath().set(":server");
        return task;
    }
}
