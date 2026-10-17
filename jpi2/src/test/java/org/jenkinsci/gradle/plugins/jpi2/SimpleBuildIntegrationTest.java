package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SimpleBuildIntegrationTest extends V2IntegrationTestBase {

    @Test
    void simpleGradleBuildShouldBuild() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        // when
        ith.gradleRunner().withArguments("build").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin-1.0.0.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifest).isNotNull().isNotEmpty();

        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull().containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void jpiAndJarRestoreFromBuildCache() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);
        // Archive tasks opt out of caching by default, so enable it to test the custom actions.
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                tasks.named("jar") { outputs.cacheIf { true } }
                tasks.named("jpi") { outputs.cacheIf { true } }
                """, StandardOpenOption.APPEND);
        var runner = ith.gradleRunner();

        var first = runner.withArguments("jpi", "--build-cache", "--configuration-cache")
                .build();
        var firstJar = first.task(":jar");
        var firstJpi = first.task(":jpi");
        assertThat(firstJar).isNotNull();
        assertThat(firstJpi).isNotNull();
        assertThat(firstJar.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(firstJpi.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        // Removing outputs forces Gradle to restore cached archives instead of reporting UP_TO_DATE.
        deleteDirectory(ith.inProjectDir("build"));
        var second = runner.withArguments("jpi", "--build-cache", "--configuration-cache")
                .build();
        var cachedJar = second.task(":jar");
        var cachedJpi = second.task(":jpi");
        assertThat(second.getOutput()).contains("Reusing configuration cache.");
        assertThat(cachedJar).isNotNull();
        assertThat(cachedJpi).isNotNull();
        assertThat(cachedJar.getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(cachedJpi.getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
    }

    @Test
    void simpleGradleBuildShouldGenerateHpl() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        // when
        ith.gradleRunner().withArguments("generateJenkinsServerHpl").build();

        // then
        var hpl = ith.inProjectDir("build/hpl/test-plugin.hpl");
        assertThat(hpl).exists();

        var manifestData = new Manifest(hpl.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData.getValue("Short-Name")).isEqualTo("test-plugin");
        assertThat(manifestData.getValue("Resource-Path"))
                .isEqualTo(ith.inProjectDir("src/main/webapp").getCanonicalPath());
    }

    @Test
    void simpleGradleBuildShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");
    }

    @Test
    void simpleGradleBuildShouldLaunchRun() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "hplRun");

        // then
        assertThat(ith.inProjectDir("work/plugins/test-plugin.hpl")).exists();
    }

    @Test
    void simpleGradleBuildShouldRespectWorkDirectoryOverrideForRun() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        var customWorkDir = Files.createDirectory(tempDir.toPath().resolve("custom-work"));

        testServerStarts(ith.gradleRunner(), "-P" + WorkDirectorySettings.PROPERTY + "=" + customWorkDir, "hplRun");

        assertThat(customWorkDir.resolve("plugins/test-plugin.hpl")).exists();
        assertThat(ith.inProjectDir("work/plugins/test-plugin.hpl")).doesNotExist();
    }

    @Test
    void simpleGradleBuildShouldRespectExtensionWorkDirectoryForRun() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), /* language=kotlin */ """
                jenkinsPlugin {
                    workDir = layout.projectDirectory.dir("custom-work")
                }
                """, StandardOpenOption.APPEND);

        testServerStarts(ith.gradleRunner(), "hplRun");

        assertThat(ith.inProjectDir("custom-work/plugins/test-plugin.hpl")).exists();
        assertThat(ith.inProjectDir("work/plugins/test-plugin.hpl")).doesNotExist();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testServerRetriesThenReportsAnActionableTimeout() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuildForVerification(ith);

        // when — force a timeout far shorter than any real Jenkins boot so the timeout path is deterministic
        var result = ith.gradleRunner()
                .withArguments("testServer", "-DtestServer.timeoutSeconds=5", "-DtestServer.maxAttempts=2")
                .buildAndFail();

        // then — the transient timeout is retried before the task gives up...
        assertThat(result.getOutput()).contains("Jenkins did not start within 5s (attempt 1 of 2); retrying");
        // ...and the final failure is actionable rather than a bare "exit code 143"
        assertThat(result.getOutput())
                .contains("Jenkins did not start within 5s and was terminated (exit code 143) after 2 attempts")
                .contains("concurrent launches")
                .contains("-DtestServer.maxParallelLaunches=N")
                .contains("-DtestServer.timeoutSeconds=N")
                .contains("-DtestServer.maxAttempts=N");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testServerIsCacheableAndInvalidatesOnSourceAndBuildScriptChange() throws IOException {
        assertVerificationTaskCachingBehavior("testServer", "prepareServer");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testHplRunIsCacheableAndInvalidatesOnSourceAndBuildScriptChange() throws IOException {
        assertVerificationTaskCachingBehavior("testHplRun", "prepareRun");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testServerInvalidatesOnInitScriptContentAndOrderChange() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuildForVerification(ith);

        // Init scripts forwarded to the nested build affect its behavior. Keep them outside the
        // project tree so only the init-script inputs (not the build-config fileTree) can invalidate
        // the cache, isolating that getInitScriptFiles (content) and getInitScriptPaths (order) work.
        var initDir = Files.createTempDirectory("jpi2-init-scripts");
        var scriptA = Files.writeString(initDir.resolve("a.gradle"), "// init script a v1\n")
                .toAbsolutePath()
                .toString();
        var scriptB = Files.writeString(initDir.resolve("b.gradle"), "// init script b\n")
                .toAbsolutePath()
                .toString();

        GradleRunner runner = ith.gradleRunner();

        var first = runner.withArguments(
                        "testServer", "--init-script", scriptA, "--init-script", scriptB, "--build-cache")
                .build();
        var firstTask = first.task(":testServer");
        assertThat(firstTask).isNotNull();
        assertThat(firstTask.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        var unchanged = runner.withArguments(
                        "testServer", "--init-script", scriptA, "--init-script", scriptB, "--build-cache")
                .build();
        var unchangedTask = unchanged.task(":testServer");
        assertThat(unchangedTask).isNotNull();
        assertThat(unchangedTask.getOutcome())
                .as("unchanged init scripts should hit the cache")
                .isEqualTo(TaskOutcome.UP_TO_DATE);

        // Edit an init script's content without changing its path: the content fingerprint
        // (getInitScriptFiles, PathSensitivity.NONE) must invalidate the cache.
        Files.writeString(initDir.resolve("a.gradle"), "// init script a v2\n");
        var afterContentEdit = runner.withArguments(
                        "testServer", "--init-script", scriptA, "--init-script", scriptB, "--build-cache")
                .build();
        var afterContentEditTask = afterContentEdit.task(":testServer");
        assertThat(afterContentEditTask).isNotNull();
        assertThat(afterContentEditTask.getOutcome())
                .as("editing an init script's content must invalidate the cache")
                .isEqualTo(TaskOutcome.SUCCESS);

        // Swap the order of two unchanged scripts: Gradle applies init scripts in command-line order,
        // so the ordered path input (getInitScriptPaths) must invalidate the cache.
        var afterReorder = runner.withArguments(
                        "testServer", "--init-script", scriptB, "--init-script", scriptA, "--build-cache")
                .build();
        var afterReorderTask = afterReorder.task(":testServer");
        assertThat(afterReorderTask).isNotNull();
        assertThat(afterReorderTask.getOutcome())
                .as("reordering init scripts must invalidate the cache")
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    private void assertVerificationTaskCachingBehavior(String task, String prepareTask) throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuildForVerification(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), /* language=kotlin */ """
                if (gradle.parent == null) {
                    println("outer-user-home=" + gradle.gradleUserHomeDir.absolutePath.replace('\\\\', '/'))
                }
                """, StandardOpenOption.APPEND);

        // A non-empty source set ensures the testHplRun cache must track classes/resources
        // via referencedFiles — without that wiring, removing this file would not invalidate
        // the cache (the .hpl text only references paths, not content).
        ith.mkDirInProjectDir("src/main/java/com/example");
        var source = ith.inProjectDir("src/main/java/com/example/Example.java").toPath();
        Files.writeString(
                source,
                "package com.example; public class Example { public String hello() { return \"v1\"; } }\n",
                StandardCharsets.UTF_8);

        GradleRunner runner = ith.gradleRunner();
        var taskPath = ":" + task;

        var first = runner.withArguments(task, "--build-cache").build();
        var firstTask = first.task(taskPath);
        assertThat(firstTask).isNotNull();
        assertThat(firstTask.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.getOutput()).contains("Jenkins is fully up and running");
        assertThat(first.task(":" + prepareTask))
                .as("only the nested build reads the synced plugins, so the outer build must not sync them")
                .isNull();

        var secondNoChange = runner.withArguments(task, "--build-cache").build();
        var secondNoChangeTask = secondNoChange.task(taskPath);
        assertThat(secondNoChangeTask).isNotNull();
        assertThat(secondNoChangeTask.getOutcome())
                .as("unchanged inputs should hit the cache and skip launching Jenkins")
                .isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(secondNoChange.getOutput()).doesNotContain("Jenkins is fully up and running");

        // Delete build outputs to force a cache lookup instead of an UP_TO_DATE check.
        deleteDirectory(ith.inProjectDir("build"));
        var fromCache = runner.withArguments(task, "--build-cache").build();
        var fromCacheTask = fromCache.task(taskPath);
        assertThat(fromCacheTask).isNotNull();
        assertThat(fromCacheTask.getOutcome())
                .as("after build dir is deleted, the task must be restored FROM_CACHE rather than re-executing")
                .isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(fromCache.getOutput()).doesNotContain("Jenkins is fully up and running");

        // Edit-in-place: the .class file's content changes but its path does not. For
        // testHplRun, the .hpl manifest's Libraries attribute lists paths (filtered by
        // File.exists), so editing alone does NOT change the .hpl bytes.
        Files.writeString(
                source,
                "package com.example; public class Example { public String hello() { return \"v2\"; } }\n",
                StandardCharsets.UTF_8);
        var afterEdit = runner.withArguments(task).build();
        var afterEditTask = afterEdit.task(taskPath);
        assertThat(afterEditTask).isNotNull();
        assertThat(afterEditTask.getOutcome())
                .as(
                        "editing main source must invalidate the cache (catches missing referencedFiles wiring on testHplRun)")
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(afterEdit.getOutput()).contains("Jenkins is fully up and running");

        // Append a comment to build.gradle.kts — changes file content without affecting behavior.
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(),
                "\n// cache-invalidation marker\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        var afterBuildScriptEdit = runner.withArguments(task).build();
        var afterBuildScriptEditTask = afterBuildScriptEdit.task(taskPath);
        assertThat(afterBuildScriptEditTask).isNotNull();
        assertThat(afterBuildScriptEditTask.getOutcome())
                .as("editing build.gradle.kts must invalidate the cache")
                .isEqualTo(TaskOutcome.SUCCESS);

        var rerunTasks = runner.withArguments(task, "--rerun-tasks", "--info").build();
        var rerunTask = rerunTasks.task(taskPath);
        assertThat(rerunTask).isNotNull();
        assertThat(rerunTask.getOutcome())
                .as("--rerun-tasks must force the task to run regardless of cache state")
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(rerunTasks.getOutput()).contains("Jenkins is fully up and running");
        assertThat(rerunTasks.getOutput().lines().filter(line -> line.contains("Command: ")))
                .as("--rerun-tasks must not be forwarded to the nested build, which would rebuild everything")
                .isNotEmpty()
                .noneMatch(line -> line.contains("--rerun-tasks"));
        var outerUserHome = rerunTasks
                .getOutput()
                .lines()
                .filter(line -> line.startsWith("outer-user-home="))
                .map(line -> line.substring("outer-user-home=".length()))
                .findFirst()
                .orElseThrow();
        assertThat(rerunTasks.getOutput())
                .as("the nested build must share the outer build's Gradle user home")
                .contains("--gradle-user-home, " + outerUserHome);

        var rerun = runner.withArguments(task, "--rerun").build();
        var rerunOnlyTask = rerun.task(taskPath);
        assertThat(rerunOnlyTask).isNotNull();
        assertThat(rerunOnlyTask.getOutcome())
                .as("--rerun must force a fresh launch of just this task")
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(rerun.getOutput()).contains("Jenkins is fully up and running");
    }

    @Test
    void simpleGradleBuildShouldCoexistWithApplicationRunTask() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithApplicationPlugin(ith);

        var result = ith.gradleRunner().withArguments("tasks", "--all").build();

        assertThat(result.getOutput()).contains("run - Runs this project as a JVM application");
        assertThat(result.getOutput()).contains("hplRun");
    }
}
