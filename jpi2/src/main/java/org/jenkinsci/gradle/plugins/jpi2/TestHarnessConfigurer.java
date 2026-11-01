package org.jenkinsci.gradle.plugins.jpi2;

import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.testing.Test;
import org.jetbrains.annotations.NotNull;

/** Installs Jenkins plugin dependencies for the test harness. */
final class TestHarnessConfigurer {
    private TestHarnessConfigurer() {}

    /**
     * Wires up {@link CopyTestPluginDependenciesTask} so that Jenkins plugin dependencies
     * resolved on the test classpath (not just library jars) are installed into the
     * {@code JenkinsRule} instance used by the {@code test} task, mirroring what JPI (v1)'s
     * {@code copyTestPluginDependencies} task does.
     */
    static void configure(@NotNull Project project, @NotNull Configuration testDefaultRuntime) {
        var testPluginDependenciesDir = project.getLayout().getBuildDirectory().dir("jpi-plugin/test");
        var copyTestPluginDependencies = project.getTasks()
                .register(CopyTestPluginDependenciesTask.NAME, CopyTestPluginDependenciesTask.class, task -> {
                    task.setGroup("Verification");
                    task.setDescription("Copies Jenkins plugin dependencies on the test classpath into a directory "
                            + "jenkins-test-harness reads to install them as plugins in JenkinsRule.");
                    task.getPluginFiles()
                            .from(project.provider(() -> resolveTestPluginArtifacts(testDefaultRuntime).stream()
                                    .map(ResolvedArtifact::getFile)
                                    .collect(Collectors.toList())));
                    task.getFileNameToPluginId()
                            .set(project.provider(() -> resolveTestPluginArtifacts(testDefaultRuntime).stream()
                                    .collect(Collectors.toMap(
                                            a -> a.getFile().getName(), ResolvedArtifact::getName, (a, b) -> a))));
                    task.getOutputDir().set(testPluginDependenciesDir.map(dir -> dir.dir("test-dependencies")));
                });
        project.getTasks().named("test", Test.class, task -> {
            task.getInputs().files(copyTestPluginDependencies);
            task.setClasspath(task.getClasspath().plus(project.files(testPluginDependenciesDir)));
        });
    }

    @NotNull
    private static List<ResolvedArtifact> resolveTestPluginArtifacts(@NotNull Configuration testDefaultRuntime) {
        return testDefaultRuntime.getResolvedConfiguration().getResolvedArtifacts().stream()
                .filter(a -> "jpi".equals(a.getExtension()) || "hpi".equals(a.getExtension()))
                .collect(Collectors.toList());
    }
}
