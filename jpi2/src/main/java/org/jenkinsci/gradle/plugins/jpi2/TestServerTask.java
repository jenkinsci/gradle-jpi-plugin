package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that launches a Jenkins server and terminates after success or first error.
 */
public abstract class TestServerTask extends DefaultTask {

    private static final List<String> FAILURE_MESSAGES = List.of(
            "Failed Loading plugin",
            "Jenkins stopped",
            "java.io.IOException: Failed to load"
    );

    @Input
    public abstract Property<String> getRootDir();

    @Input
    public abstract Property<String> getGradleExecutable();

    @Input
    public abstract Property<String> getJavaHome();

    @Input
    public abstract ListProperty<String> getInitScripts();

    @Input
    public abstract ListProperty<String> getIncludedBuilds();

    @Input
    public abstract Property<Boolean> getOffline();

    @Input
    public abstract Property<Boolean> getBuildCacheEnabled();

    @Input
    public abstract Property<Boolean> getRefreshDependencies();

    @Input
    public abstract Property<Boolean> getContinueOnFailure();

    @Input
    public abstract Property<Boolean> getParallelExecution();

    @Input
    public abstract Property<Boolean> getProfile();

    @Input
    public abstract Property<Boolean> getRerunTasks();

    @Input
    public abstract Property<Boolean> getDryRun();

    @Input
    public abstract MapProperty<String, String> getSystemProperties();

    @Input
    public abstract MapProperty<String, String> getProjectProperties();

    @Input
    public abstract Property<String> getServerTaskPath();

    @Internal
    public abstract Property<PortAllocationService> getPortAllocationService();

    @TaskAction
    public void runTestServer() {
        var timeoutSystemProperty = System.getProperty("testServer.timeoutSeconds", "120");
        var timeout = Integer.parseInt(timeoutSystemProperty);
        Path workDir = null;

        try {
            workDir = createWorkDirectory();
            var commandLine = getCommandLine(workDir);
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
        } finally {
            cleanupWorkDirectory(workDir);
        }
    }

    @NotNull
    private Process launchProcess(List<String> commandLine) throws IOException {
        return new ProcessBuilder(commandLine).directory(new File(getRootDir().get())).redirectErrorStream(true).start();
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
    private Path createWorkDirectory() throws IOException {
        Files.createDirectories(getTemporaryDir().toPath());
        return Files.createTempDirectory(getTemporaryDir().toPath(), "jenkins-work-");
    }

    private void cleanupWorkDirectory(Path workDir) {
        if (workDir == null || Boolean.getBoolean(WorkDirectorySettings.PRESERVE_TEST_WORK_DIR_SYSTEM_PROPERTY)) {
            return;
        }

        try (var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new GradleException("Failed to delete temporary Jenkins work directory " + workDir, e);
                        }
                    });
        } catch (IOException e) {
            throw new GradleException("Failed to clean temporary Jenkins work directory " + workDir, e);
        }
    }

    @NotNull
    private List<String> getCommandLine(@NotNull Path workDir) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(getGradleExecutable().get());
        commandLine.add("-Dorg.gradle.java.home=" + getJavaHome().get());

        for (var initScript : getInitScripts().get()) {
            commandLine.add("--init-script");
            commandLine.add(initScript);
        }

        for (var includedBuild : getIncludedBuilds().get()) {
            commandLine.add("--include-build");
            commandLine.add(includedBuild);
        }

        if (getOffline().get()) commandLine.add("--offline");
        if (getBuildCacheEnabled().get()) commandLine.add("--build-cache");
        if (getRefreshDependencies().get()) commandLine.add("--refresh-dependencies");
        if (getContinueOnFailure().get()) commandLine.add("--continue");
        if (getParallelExecution().get()) commandLine.add("--parallel");
        if (getProfile().get()) commandLine.add("--profile");
        if (getRerunTasks().get()) commandLine.add("--rerun-tasks");
        if (getDryRun().get()) commandLine.add("--dry-run");

        getSystemProperties().get().forEach((k, v) -> {
            if (!k.equals(WorkDirectorySettings.SYSTEM_PROPERTY)) {
                commandLine.add("-D" + k + "=" + v);
            }
        });
        getProjectProperties().get().forEach((k, v) -> commandLine.add("-P" + k + "=" + v));

        commandLine.add(getServerTaskPath().get());
        commandLine.add("-Dserver.port=" + getPortAllocationService().get().findAndReserveFreePort());
        commandLine.add("-D" + WorkDirectorySettings.SYSTEM_PROPERTY + "=" + workDir.toAbsolutePath());
        System.err.println("Command: " + commandLine);
        return commandLine;
    }
}
