package org.jenkinsci.gradle.plugins.jpi2;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/** Configures isolated Jenkins startup checks and their modeled build inputs. */
final class ServerVerificationConfigurer {
    private ServerVerificationConfigurer() {}

    static void configure(
            Project project,
            Configuration serverTaskClasspath,
            TaskProvider<Sync> prepareServer,
            TaskProvider<Sync> prepareRun,
            SourceSet main,
            RuntimeClasspathArtifacts runtimeClasspathArtifacts) {
        BuildServiceRegistry buildServices = project.getGradle().getSharedServices();

        // Cap how many Jenkins servers boot at once. Each launch is a nested Gradle build plus a
        // Jenkins JVM, so unbounded parallelism saturates the machine and Jenkins misses its startup
        // timeout (killed → exit 143). The cap governs only the launch tasks, so the rest of the
        // build keeps full parallelism. Default scales with CPUs (~1 launch per 3 cores); override
        // with -DtestServer.maxParallelLaunches, which accepts an absolute count or a processor-
        // relative C/D so one setting survives moving to a machine with a different core count.
        var availableProcessors = Runtime.getRuntime().availableProcessors();
        Provider<Integer> maxParallelLaunches = project.getProviders()
                .systemProperty(JenkinsLaunchThrottle.MAX_PARALLEL_LAUNCHES_PROPERTY)
                .map(spec -> JenkinsLaunchThrottle.resolveMaxParallelLaunches(spec, availableProcessors))
                .orElse(JenkinsLaunchThrottle.resolveMaxParallelLaunches(null, availableProcessors));
        var launchThrottle = buildServices.registerIfAbsent(
                "jenkinsLaunchThrottle",
                JenkinsLaunchThrottle.class,
                spec -> spec.getMaxParallelUsages().set(maxParallelLaunches));

        var gradle = project.getGradle();
        var startParameter = gradle.getStartParameter();
        var gradleHome = gradle.getGradleHomeDir();
        var isWindows =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        var gradleScriptName = isWindows ? "bin/gradle.bat" : "bin/gradle";
        var gradleExecutable = gradleHome != null ? new File(gradleHome, gradleScriptName).getAbsolutePath() : "gradle";
        var isRootProject = project == project.getRootProject();
        var projectPath = project.getPath();

        var testServerTask = registerTestTask(
                project,
                launchThrottle,
                maxParallelLaunches,
                gradleExecutable,
                startParameter,
                isRootProject,
                projectPath,
                "testServer",
                "Launch Jenkins server and terminate after success or first error",
                ":server");
        // Fingerprint the source files that prepareServer would sync (jpi, plugin dependencies,
        // project-dependency jpis), not its destination — prepareServer and prepareRun both write
        // to workDir/plugins, so snapshotting the destination would create an implicit dependency
        // between the two test tasks. The nested build runs its own prepareServer into a fresh
        // work directory, so syncing here too would only copy plugins nothing reads.
        testServerTask.configure(task -> {
            task.getPluginFiles().from(syncSourceWithoutSyncing(project, prepareServer));
            task.getJenkinsClasspath().from(serverTaskClasspath);
        });

        var testHplRunTask = registerTestTask(
                project,
                launchThrottle,
                maxParallelLaunches,
                gradleExecutable,
                startParameter,
                isRootProject,
                projectPath,
                "testHplRun",
                "Launch Jenkins hplRun task and terminate after success or first error",
                ":hplRun");
        testHplRunTask.configure(task -> {
            task.getPluginFiles().from(syncSourceWithoutSyncing(project, prepareRun));
            task.getJenkinsClasspath().from(serverTaskClasspath);
            // The .hpl manifest points at these directories on disk by absolute path; their
            // contents must be fingerprinted for caching to be correct, even though only the
            // generated .hpl file (whose text doesn't change when sources change) is an input
            // to prepareRun.
            task.getReferencedFiles().from(main.getResources().getSrcDirs());
            task.getReferencedFiles().from(main.getOutput().getClassesDirs());
            task.getReferencedFiles().from(project.provider(main.getOutput()::getResourcesDir));
            task.getReferencedFiles().from(runtimeClasspathArtifacts.getBundledLibraries());
            task.dependsOn(project.getTasks().named("classes"));
        });
    }

    /**
     * A mapped task provider carries a dependency on the task itself; going through a plain
     * provider keeps only the dependencies of the source files (e.g. the {@code jpi} task).
     */
    @NotNull
    private static Provider<FileCollection> syncSourceWithoutSyncing(
            @NotNull Project project, @NotNull TaskProvider<Sync> sync) {
        return project.provider(() -> sync.get().getSource());
    }

    @NotNull
    private static TaskProvider<TestServerTask> registerTestTask(
            @NotNull Project project,
            @NotNull Provider<JenkinsLaunchThrottle> launchThrottle,
            @NotNull Provider<Integer> maxParallelLaunches,
            @NotNull String gradleExecutable,
            @NotNull StartParameter startParameter,
            boolean isRootProject,
            @NotNull String projectPath,
            @NotNull String taskName,
            @NotNull String description,
            @NotNull String taskSuffix) {
        return project.getTasks().register(taskName, TestServerTask.class, task -> {
            task.setGroup("verification");
            task.setDescription(description);
            task.getRootDir().set(project.getRootDir().getAbsolutePath());
            task.getGradleExecutable().set(gradleExecutable);
            task.getJavaHome().set(System.getProperty("java.home"));
            var initScripts = startParameter.getAllInitScripts();
            task.getInitScriptFiles().from(initScripts);
            task.getInitScriptPaths()
                    .set(initScripts.stream().map(File::getAbsolutePath).toList());
            addBuildConfigFiles(project, task);
            task.getIncludedBuilds()
                    .set(startParameter.getIncludedBuilds().stream()
                            .map(File::getPath)
                            .toList());
            task.getGradleUserHome().set(startParameter.getGradleUserHomeDir().getAbsolutePath());
            task.getOffline().set(startParameter.isOffline());
            task.getBuildCacheEnabled().set(startParameter.isBuildCacheEnabled());
            task.getRefreshDependencies().set(startParameter.isRefreshDependencies());
            task.getContinueOnFailure().set(startParameter.isContinueOnFailure());
            task.getParallelExecution().set(startParameter.isParallelProjectExecutionEnabled());
            task.getProfile().set(startParameter.isProfile());
            task.getDryRun().set(startParameter.isDryRun());
            task.getSystemProperties().set(startParameter.getSystemPropertiesArgs());
            task.getProjectProperties().set(startParameter.getProjectProperties());
            task.getServerTaskPath().set(isRootProject ? taskSuffix : projectPath + taskSuffix);
            task.getSuccessMarker()
                    .set(project.getLayout().getBuildDirectory().file("test-server/" + taskName + ".success"));
            // Bounds concurrent Jenkins launches across the whole build (see JenkinsLaunchThrottle).
            task.usesService(launchThrottle);
            task.getMaxParallelLaunches().set(maxParallelLaunches);
        });
    }

    /**
     * Registers the build logic that can change how the nested {@code :server} / {@code :hplRun}
     * build behaves: build &amp; settings scripts, {@code gradle.properties}, version catalogs and
     * {@code buildSrc} sources.
     *
     * <p>The files are enumerated per project instead of scanned from the root directory. A file
     * tree rooted at the root directory is an ancestor of every other task's output in the build
     * (a sibling module's Jenkins work directory, an npm install directory, …), and Gradle reports
     * such an input as an undeclared dependency on those producing tasks and, since Gradle 9, fails
     * the build for it. Naming the files keeps the fingerprint without claiming to read the whole
     * tree.
     *
     * <p>This cannot be exhaustive — the nested build re-evaluates everything — so see the
     * cacheability note on {@link TestServerTask} for what is and isn't guaranteed.
     */
    private static void addBuildConfigFiles(@NotNull Project project, @NotNull TestServerTask task) {
        var rootDir = project.getRootDir();
        var files = findBuildConfigFiles(project, rootDir);
        task.getBuildConfigFiles().from(files);
        // The `gradle` and `buildSrc/src` directories hold no task outputs, so they are safe to
        // walk as trees: version catalogs, script plugins and precompiled build logic.
        task.getBuildConfigFiles()
                .from(project.fileTree(
                        new File(rootDir, "gradle"),
                        tree -> tree.include("**/*.versions.toml", "**/*.gradle", "**/*.gradle.kts")));
        task.getBuildConfigFiles().from(project.fileTree(new File(rootDir, "buildSrc/src")));
    }

    @NonNull
    private static LinkedHashSet<File> findBuildConfigFiles(@NonNull Project project, File rootDir) {
        var files = new LinkedHashSet<File>();
        files.add(new File(rootDir, "settings.gradle"));
        files.add(new File(rootDir, "settings.gradle.kts"));
        // buildSrc is a separate build, so it is not part of getAllprojects().
        for (var name : List.of(
                "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties")) {
            files.add(new File(rootDir, "buildSrc/" + name));
        }
        for (var candidate : project.getRootProject().getAllprojects()) {
            files.add(candidate.getBuildFile());
            files.add(new File(candidate.getProjectDir(), "gradle.properties"));
        }
        return files;
    }
}
