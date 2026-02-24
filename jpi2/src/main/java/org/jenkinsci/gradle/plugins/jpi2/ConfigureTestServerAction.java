package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class ConfigureTestServerAction implements Action<Task> {

    private static final List<String> FAILURE_MESSAGES = List.of(
            "Failed Loading plugin",
            "Jenkins stopped",
            "java.io.IOException: Failed to load"
    );

    private final Project project;
    private final PortAllocationService portAllocationService;

    public ConfigureTestServerAction(Project project, PortAllocationService portAllocationService) {
        this.project = project;
        this.portAllocationService = portAllocationService;
    }


    @Override
    public void execute(@NotNull Task task) {
        task.setGroup("verification");
        task.setDescription("Launch Jenkins server and terminate after success or first error");
        task.doLast(new Action<>() {

            @Override
            public void execute(@NotNull Task task) {
                List<String> commandLine = getCommandLine();
                var timeoutSystemProperty = System.getProperty("testServer.timeoutSeconds", "120");
                var timeout = Integer.parseInt(timeoutSystemProperty);

                try {
                    var process = launchProcess(commandLine);

                    var timerThread = new Thread(() -> {
                        try {
                            Thread.sleep(timeout * 1000L);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        System.err.println("Timeout reached, terminating Jenkins server");
                        process.destroy();
                    });

                    timerThread.start();

                    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    boolean foundSuccess = isProcessSuccessful(stdoutReader, process);

                    if (process.waitFor() != 0) {
                        if (!foundSuccess) {
                            throw new GradleException("Jenkins failed to start with exit code " + process.exitValue());
                        }
                    }
                } catch (IOException e) {
                    throw new GradleException("IO Exception", e);
                } catch (InterruptedException e) {
                    throw new GradleException("Process interrupted", e);
                }

            }
        });
    }

    @NotNull
    private Process launchProcess(List<String> commandLine) throws IOException {
        return new ProcessBuilder(commandLine).directory(project.getRootDir()).redirectErrorStream(true).start();
    }

    private static boolean isProcessSuccessful(BufferedReader stdoutReader, Process process) throws IOException, InterruptedException {
        String stdout;

        while ((stdout = stdoutReader.readLine()) != null) {
            System.err.println("    " + stdout);
            if (stdout.contains("Jenkins is fully up and running")) {
                process.destroy();
                return true;
            }
            if (FAILURE_MESSAGES.stream().anyMatch(stdout::contains)) {
                process.destroy();
                process.waitFor();
                throw new GradleException("Jenkins failed to start: " + stdout);
            }
        }
        return false;
    }

    @NotNull
    private List<String> getCommandLine() {
        Gradle gradle = project.getGradle();
        var startParameter = gradle.getStartParameter();
        var gradleHome = gradle.getGradleHomeDir();
        var gradleExecutable = gradleHome != null ? new File(gradleHome, "bin/gradle").getAbsolutePath() : "gradle";

        List<String> commandLine = new ArrayList<>();
        commandLine.add(gradleExecutable);
        startParameter.getAllInitScripts().forEach(initScript -> {
            commandLine.add("--init-script");
            commandLine.add(initScript.getAbsolutePath());
        });

        commandLine.addAll(startParameter.getIncludedBuilds().stream()
                .flatMap(it -> Stream.of("--include-build", it.getPath()))
                .toList());
        if (startParameter.isOffline()) commandLine.add("--offline");
        if (startParameter.isBuildCacheEnabled()) commandLine.add("--build-cache");
        if (startParameter.isRefreshDependencies()) commandLine.add("--refresh-dependencies");
        if (startParameter.isContinueOnFailure()) commandLine.add("--continue");
        if (startParameter.isParallelProjectExecutionEnabled()) commandLine.add("--parallel");
        if (startParameter.isProfile()) commandLine.add("--profile");
        if (startParameter.isRerunTasks()) commandLine.add("--rerun-tasks");
        if (startParameter.isDryRun()) commandLine.add("--dry-run");

        startParameter.getSystemPropertiesArgs().forEach((k, v) -> commandLine.add("-D" + k + "=" + v));
        startParameter.getProjectProperties().forEach((k, v) -> commandLine.add("-P" + k + "=" + v));

        commandLine.add(project == project.getRootProject() ? ":server" : project.getPath() + ":server");
        commandLine.add("-Dserver.port=" + portAllocationService.findAndReserveFreePort());
        System.err.println("Command: " + commandLine);
        return commandLine;
    }
}
