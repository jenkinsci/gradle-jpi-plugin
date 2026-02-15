package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.testing.Test;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import java.util.stream.Collectors;

@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class ConfigureTestServerAction implements Action<Test> {

    private final Project project;
    private final PortAllocationService portAllocationService;
    private final SourceSet testServerSourceSet;
    private final TaskProvider<?> prepareServerTask;
    private final TaskProvider<JavaExec> serverTask;

    public ConfigureTestServerAction(
            Project project,
            PortAllocationService portAllocationService,
            SourceSet testServerSourceSet,
            TaskProvider<?> prepareServerTask,
            TaskProvider<JavaExec> serverTask
    ) {
        this.project = project;
        this.portAllocationService = portAllocationService;
        this.testServerSourceSet = testServerSourceSet;
        this.prepareServerTask = prepareServerTask;
        this.serverTask = serverTask;
    }


    @Override
    public void execute(@NotNull Test task) {
        task.setGroup("verification");
        task.setDescription("Launch Jenkins server via a synthesized JUnit 5 test");
        task.useJUnitPlatform();
        task.setTestClassesDirs(testServerSourceSet.getOutput().getClassesDirs());
        task.setClasspath(testServerSourceSet.getRuntimeClasspath());
        task.getFilter().includeTestsMatching("org.jenkinsci.gradle.plugins.jpi2.generated.SynthesizedTestServerTest");
        task.getOutputs().upToDateWhen(element -> false);
        task.getTestLogging().setShowStandardStreams(true);
        task.dependsOn(prepareServerTask);
        task.getInputs().files(prepareServerTask);
        task.doFirst(new Action<>() {
            @Override
            public void execute(@NotNull Task ignored) {
                task.systemProperty("testServer.projectDir", project.getProjectDir().getAbsolutePath());
                task.systemProperty("testServer.port", String.valueOf(portAllocationService.findAndReserveFreePort()));
                task.systemProperty("testServer.timeoutSeconds", System.getProperty("testServer.timeoutSeconds", "120"));
                task.systemProperty("testServer.jvmArgs", encodeStrings(serverTask.get().getJvmArgs() == null ? List.of() : serverTask.get().getJvmArgs()));
                task.systemProperty("testServer.systemProperties", encodeSystemProperties(serverTask.get().getSystemProperties()));
                task.systemProperty("testServer.serverClasspathNames", encodeStrings(
                        StreamSupport.stream(serverTask.get().getClasspath().spliterator(), false)
                                .map(File::getName)
                                .toList()
                ));
            }
        });
    }

    @NotNull
    private static String encodeSystemProperties(@NotNull Map<String, ?> systemProperties) {
        return systemProperties.entrySet().stream()
                .map(it -> it.getKey() + "=" + String.valueOf(it.getValue()))
                .map(it -> Base64.getEncoder().encodeToString(it.getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.joining(","));
    }

    @NotNull
    private static String encodeStrings(@NotNull List<String> strings) {
        return strings.stream()
                .map(it -> Base64.getEncoder().encodeToString(it.getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.joining(","));
    }
}
