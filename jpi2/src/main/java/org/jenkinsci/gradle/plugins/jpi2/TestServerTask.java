package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that launches a Jenkins server and terminates after success or first error.
 *
 * <p>It does so by spawning a nested Gradle build that runs the real {@code :server} / {@code :hplRun}
 * task, so the verification exercises exactly the launch path users rely on.
 *
 * <p>Cacheable: a successful run produces a marker file. If the declared inputs are unchanged, Gradle
 * can restore the marker from cache and skip launching Jenkins. The modeled inputs are:
 * <ul>
 *   <li>the plugin files synced for the server and the Jenkins runtime classpath;</li>
 *   <li>files referenced from the plugin's HPL ({@code testHplRun} only);</li>
 *   <li>the forwarded init scripts — content via {@link #getInitScriptFiles()} and order/identity
 *       via {@link #getInitScriptPaths()};</li>
 *   <li>the project's build logic that can change the nested build: build &amp; settings scripts,
 *       {@code gradle.properties}, version catalogs, and {@code buildSrc} sources (see
 *       {@link #getBuildConfigFiles()});</li>
 *   <li>the forwarded Gradle flags and system/project properties.</li>
 * </ul>
 *
 * <p><strong>Cacheability boundary.</strong> Because the nested build re-evaluates the whole project,
 * its inputs cannot be captured exhaustively from here. Changes to build logic <em>outside</em> the
 * modeled set are <em>not</em> guaranteed to invalidate the cache, notably: included builds located
 * outside the project tree, environment variables, {@code ~/.gradle/gradle.properties}, the Gradle or
 * wrapper version, and dynamic dependency versions resolved from the network. When in doubt, run with
 * {@code --rerun-tasks} to force a fresh launch.
 */
@CacheableTask
public abstract class TestServerTask extends DefaultTask {

    private static final List<String> FAILURE_MESSAGES = List.of(
            "Failed Loading plugin",
            "Jenkins stopped",
            "java.io.IOException: Failed to load"
    );

    /** @return root directory of the plugin project, used as the working directory for the spawned Gradle process */
    @Input
    public abstract Property<String> getRootDir();

    /** @return path to the {@code gradlew} executable to invoke for the nested build */
    @Input
    public abstract Property<String> getGradleExecutable();

    /** @return path to the JDK passed to the spawned Gradle via {@code -Dorg.gradle.java.home} */
    @Input
    public abstract Property<String> getJavaHome();

    /**
     * @return ordered absolute paths of the init scripts forwarded via {@code --init-script}, in the
     * exact order they are passed. Gradle applies init scripts in command-line order, so the order
     * (and the set of paths) is part of the cache key — reordering two scripts, or swapping one for a
     * same-content script at a different path, changes the nested build and must invalidate the cache.
     * Content is fingerprinted separately by {@link #getInitScriptFiles()}.
     */
    @Input
    public abstract ListProperty<String> getInitScriptPaths();

    /**
     * @return the init script files, fingerprinted for content so that editing a script invalidates
     * the cache even when its path is unchanged. Path and ordering are captured by
     * {@link #getInitScriptPaths()}, so this collection only needs to track content
     * ({@link PathSensitivity#NONE}).
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInitScriptFiles();

    /**
     * @return build-logic files for the nested build's project tree: build &amp; settings scripts
     * ({@code *.gradle}, {@code *.gradle.kts}), {@code gradle.properties}, version catalogs
     * ({@code *.versions.toml}), and {@code buildSrc} sources. Changes to these affect the nested
     * build's behavior (e.g. {@code JavaExec} task args or resolved plugin versions) but are not
     * captured by plugin-file or classpath inputs. This is best-effort, not exhaustive — see the
     * cacheability boundary on the class javadoc.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBuildConfigFiles();

    /** @return composite-build inclusions forwarded via {@code --include-build} */
    @Input
    public abstract ListProperty<String> getIncludedBuilds();

    /** @return {@code true} to pass {@code --offline} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getOffline();

    /** @return {@code true} to pass {@code --build-cache} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getBuildCacheEnabled();

    /** @return {@code true} to pass {@code --refresh-dependencies} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getRefreshDependencies();

    /** @return {@code true} to pass {@code --continue} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getContinueOnFailure();

    /** @return {@code true} to pass {@code --parallel} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getParallelExecution();

    /** @return {@code true} to pass {@code --profile} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getProfile();

    /** @return {@code true} to pass {@code --rerun-tasks} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getRerunTasks();

    /** @return {@code true} to pass {@code --dry-run} to the spawned Gradle */
    @Input
    public abstract Property<Boolean> getDryRun();

    /** @return system properties forwarded as {@code -Dkey=value} to the spawned Gradle, excluding internal ones */
    @Input
    public abstract MapProperty<String, String> getSystemProperties();

    /** @return project properties forwarded as {@code -Pkey=value} to the spawned Gradle, excluding internal ones */
    @Input
    public abstract MapProperty<String, String> getProjectProperties();

    /** @return Gradle task path to run inside the spawned build (e.g. {@code :server}) */
    @Input
    public abstract Property<String> getServerTaskPath();

    /**
     * @return files that {@code prepareServer} / {@code prepareRun} would copy into the Jenkins
     * work directory (the project's own JPI/HPL and resolved plugin dependencies). Fingerprinting
     * these is what makes the task safely cacheable when nothing relevant has changed.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getPluginFiles();

    /** @return classpath used to launch the embedded Jenkins server (jenkins-war). */
    @Classpath
    public abstract ConfigurableFileCollection getJenkinsClasspath();

    /**
     * @return files referenced by the plugin's HPL but not bundled into {@link #getPluginFiles()}:
     * the plugin's own classes, resource directories, and bundled libraries. Required for
     * {@code testHplRun}, where the HPL points at these paths directly; ignored (empty) for
     * {@code testServer}, since the JPI archive already captures the same content.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getReferencedFiles();

    /** @return marker file written only on a successful Jenkins start so the task is cacheable. */
    @OutputFile
    public abstract RegularFileProperty getSuccessMarker();

    /** @return build service that allocates a free TCP port for the Jenkins test server */
    @Internal
    public abstract Property<PortAllocationService> getPortAllocationService();

    /**
     * Launches a nested Gradle build, streams its output, and fails the task
     * if Jenkins does not report a successful start within the configured timeout.
     */
    @TaskAction
    public void runTestServer() {
        var timeoutSystemProperty = System.getProperty("testServer.timeoutSeconds", "120");
        var timeout = Integer.parseInt(timeoutSystemProperty);

        clearSuccessMarker();

        Path workDir = null;
        try {
            workDir = createWorkDirectory();
            var commandLine = getCommandLine(workDir);
            var process = launchProcess(commandLine);

            var timerThread = new Thread(() -> {
                try {
                    Thread.sleep(timeout * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                getLogger().warn("Timeout reached, terminating Jenkins server");
                destroyTree(process);
            });

            timerThread.start();

            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            boolean foundSuccess = isProcessSuccessful(stdoutReader, process);

            process.waitFor();

            if (!foundSuccess) {
                throw new GradleException("Jenkins failed to report a successful start (exit code " + process.exitValue() + ")");
            }

            writeSuccessMarker();
        } catch (IOException e) {
            throw new GradleException("IO Exception", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Process interrupted", e);
        } finally {
            cleanupWorkDirectory(workDir);
        }
    }

    private void clearSuccessMarker() {
        var marker = getSuccessMarker().get().getAsFile();
        try {
            Files.deleteIfExists(marker.toPath());
        } catch (IOException e) {
            throw new GradleException("Failed to clear success marker " + marker, e);
        }
    }

    private void writeSuccessMarker() {
        var marker = getSuccessMarker().get().getAsFile();
        try {
            Files.createDirectories(marker.toPath().getParent());
            Files.writeString(marker.toPath(), "Jenkins reported successful start\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Failed to write success marker " + marker, e);
        }
    }

    @NotNull
    private Process launchProcess(List<String> commandLine) throws IOException {
        return new ProcessBuilder(commandLine).directory(new File(getRootDir().get())).redirectErrorStream(true).start();
    }

    /**
     * Forcibly terminates the launched Gradle client and any of its descendants (notably the forked
     * JavaExec JVM running {@code :server} / {@code :hplRun}, which never returns on its own). A
     * graceful {@code destroy()} (SIGTERM) on the client leaves it waiting on that never-ending task
     * while this task keeps blocking on its stdout — the source of the CI hang. Killing the client
     * forcibly (SIGKILL) closes its stdout immediately so the reader unblocks; the Gradle daemon then
     * cancels the disconnected build, which tears down the Jenkins JVM it forked.
     */
    private static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private boolean isProcessSuccessful(BufferedReader stdoutReader, Process process) throws IOException, InterruptedException {
        String stdout;

        while ((stdout = stdoutReader.readLine()) != null) {
            getLogger().lifecycle("    {}", stdout);
            if (stdout.contains("Jenkins is fully up and running")) {
                destroyTree(process);
                return true;
            }
            if (FAILURE_MESSAGES.stream().anyMatch(stdout::contains)) {
                destroyTree(process);
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
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new GradleException("Failed to clean temporary Jenkins work directory " + workDir, e);
        }
    }

    @NotNull
    private List<String> getCommandLine(@NotNull Path workDir) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(slashify(getGradleExecutable().get()));
        commandLine.add("-Dorg.gradle.java.home=" + slashify(getJavaHome().get()));

        for (var initScriptPath : getInitScriptPaths().get()) {
            commandLine.add("--init-script");
            commandLine.add(slashify(initScriptPath));
        }

        for (var includedBuild : getIncludedBuilds().get()) {
            commandLine.add("--include-build");
            commandLine.add(slashify(includedBuild));
        }

        commandLine.add("--no-daemon");
        if (getOffline().get()) commandLine.add("--offline");
        if (getBuildCacheEnabled().get()) commandLine.add("--build-cache");
        if (getRefreshDependencies().get()) commandLine.add("--refresh-dependencies");
        if (getContinueOnFailure().get()) commandLine.add("--continue");
        if (getParallelExecution().get()) commandLine.add("--parallel");
        if (getProfile().get()) commandLine.add("--profile");
        if (getRerunTasks().get()) commandLine.add("--rerun-tasks");
        if (getDryRun().get()) commandLine.add("--dry-run");

        getSystemProperties().get().forEach((k, v) -> {
            if (!k.equals(WorkDirectorySettings.PROPERTY)) {
                commandLine.add("-D" + k + "=" + slashify(v));
            }
        });
        getProjectProperties().get().forEach((k, v) -> {
            if (!k.equals(WorkDirectorySettings.PROPERTY)) {
                commandLine.add("-P" + k + "=" + slashify(v));
            }
        });

        commandLine.add(getServerTaskPath().get());
        commandLine.add("-Pserver.port=" + getPortAllocationService().get().findAndReserveFreePort());
        commandLine.add("-P" + WorkDirectorySettings.PROPERTY + "=" + slashify(workDir.toAbsolutePath().toString()));
        getLogger().info("Command: {}", commandLine);
        return commandLine;
    }

    /**
     * Backslashes in Windows paths get silently dropped somewhere between here and the spawned
     * Gradle process reading its own arguments (observed even on values, like
     * {@link #getGradleExecutable()}, that never pass through our own {@code -D}/{@code -P}
     * parsing). Forward slashes are accepted in paths on Windows, so use those everywhere a
     * path is placed on the spawned process's command line.
     */
    @NotNull
    private static String slashify(@NotNull String value) {
        return value.replace('\\', '/');
    }
}
