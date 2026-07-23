package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(manifestData.getValue("Jenkins-Version"))
                .isEqualTo("2.492.3");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
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

        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), /* language=kotlin */ """
                jenkinsPlugin {
                    workDir = layout.projectDirectory.dir("custom-work")
                }
                """, StandardOpenOption.APPEND);

        testServerStarts(ith.gradleRunner(), "hplRun");

        assertThat(ith.inProjectDir("custom-work/plugins/test-plugin.hpl")).exists();
        assertThat(ith.inProjectDir("work/plugins/test-plugin.hpl")).doesNotExist();
    }

    @Test
    void simpleGradleBuildShouldVerifyRun() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuildForVerification(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerVerificationTask(gradleRunner, "testHplRun");
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
        assertThat(result.getOutput())
                .contains("Jenkins did not start within 5s (attempt 1 of 2); retrying");
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
    void testServerIsCacheableAndInvalidatesOnSourceChange() throws IOException {
        assertVerificationTaskCachingBehavior("testServer");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testHplRunIsCacheableAndInvalidatesOnSourceChange() throws IOException {
        assertVerificationTaskCachingBehavior("testHplRun");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testServerInvalidatesOnBuildScriptChange() throws IOException {
        assertVerificationTaskInvalidatesOnBuildScriptChange("testServer");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testHplRunInvalidatesOnBuildScriptChange() throws IOException {
        assertVerificationTaskInvalidatesOnBuildScriptChange("testHplRun");
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
        var scriptA = Files.writeString(initDir.resolve("a.gradle"), "// init script a v1\n").toAbsolutePath().toString();
        var scriptB = Files.writeString(initDir.resolve("b.gradle"), "// init script b\n").toAbsolutePath().toString();

        GradleRunner runner = ith.gradleRunner();

        var first = runner.withArguments("testServer", "--init-script", scriptA, "--init-script", scriptB, "--build-cache").build();
        assertThat(first.task(":testServer").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        var unchanged = runner.withArguments("testServer", "--init-script", scriptA, "--init-script", scriptB, "--build-cache").build();
        assertThat(unchanged.task(":testServer").getOutcome())
                .as("unchanged init scripts should hit the cache")
                .isEqualTo(TaskOutcome.UP_TO_DATE);

        // Edit an init script's content without changing its path: the content fingerprint
        // (getInitScriptFiles, PathSensitivity.NONE) must invalidate the cache.
        Files.writeString(initDir.resolve("a.gradle"), "// init script a v2\n");
        var afterContentEdit = runner.withArguments("testServer", "--init-script", scriptA, "--init-script", scriptB, "--build-cache").build();
        assertThat(afterContentEdit.task(":testServer").getOutcome())
                .as("editing an init script's content must invalidate the cache")
                .isEqualTo(TaskOutcome.SUCCESS);

        // Swap the order of two unchanged scripts: Gradle applies init scripts in command-line order,
        // so the ordered path input (getInitScriptPaths) must invalidate the cache.
        var afterReorder = runner.withArguments("testServer", "--init-script", scriptB, "--init-script", scriptA, "--build-cache").build();
        assertThat(afterReorder.task(":testServer").getOutcome())
                .as("reordering init scripts must invalidate the cache")
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    private void assertVerificationTaskInvalidatesOnBuildScriptChange(String task) throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuildForVerification(ith);

        GradleRunner runner = ith.gradleRunner();
        var taskPath = ":" + task;

        var first = runner.withArguments(task).build();
        assertThat(first.task(taskPath).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        var secondNoChange = runner.withArguments(task).build();
        assertThat(secondNoChange.task(taskPath).getOutcome())
                .as("unchanged inputs should hit the cache")
                .isEqualTo(TaskOutcome.UP_TO_DATE);

        // Append a comment to build.gradle.kts — changes file content without affecting behavior.
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(),
                "\n// cache-invalidation marker\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        var afterBuildScriptEdit = runner.withArguments(task).build();
        assertThat(afterBuildScriptEdit.task(taskPath).getOutcome())
                .as("editing build.gradle.kts must invalidate the cache")
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    private void assertVerificationTaskCachingBehavior(String task) throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuildForVerification(ith);

        // A non-empty source set ensures the testHplRun cache must track classes/resources
        // via referencedFiles — without that wiring, removing this file would not invalidate
        // the cache (the .hpl text only references paths, not content).
        ith.mkDirInProjectDir("src/main/java/com/example");
        var source = ith.inProjectDir("src/main/java/com/example/Example.java").toPath();
        Files.writeString(source,
                "package com.example; public class Example { public String hello() { return \"v1\"; } }\n",
                StandardCharsets.UTF_8);

        GradleRunner runner = ith.gradleRunner();
        var taskPath = ":" + task;

        var first = runner.withArguments(task, "--build-cache").build();
        assertThat(first.task(taskPath).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.getOutput()).contains("Jenkins is fully up and running");

        var secondNoChange = runner.withArguments(task, "--build-cache").build();
        assertThat(secondNoChange.task(taskPath).getOutcome())
                .as("unchanged inputs should hit the cache and skip launching Jenkins")
                .isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(secondNoChange.getOutput()).doesNotContain("Jenkins is fully up and running");

        // Delete build outputs to force a cache lookup instead of an UP_TO_DATE check.
        deleteDirectory(ith.inProjectDir("build"));
        var fromCache = runner.withArguments(task, "--build-cache").build();
        assertThat(fromCache.task(taskPath).getOutcome())
                .as("after build dir is deleted, the task must be restored FROM_CACHE rather than re-executing")
                .isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(fromCache.getOutput()).doesNotContain("Jenkins is fully up and running");

        // Edit-in-place: the .class file's content changes but its path does not. For
        // testHplRun, the .hpl manifest's Libraries attribute lists paths (filtered by
        // File.exists), so editing alone does NOT change the .hpl bytes.
        Files.writeString(source,
                "package com.example; public class Example { public String hello() { return \"v2\"; } }\n",
                StandardCharsets.UTF_8);
        var afterEdit = runner.withArguments(task).build();
        assertThat(afterEdit.task(taskPath).getOutcome())
                .as("editing main source must invalidate the cache (catches missing referencedFiles wiring on testHplRun)")
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(afterEdit.getOutput()).contains("Jenkins is fully up and running");

        var rerunTasks = runner.withArguments(task, "--rerun-tasks").build();
        assertThat(rerunTasks.task(taskPath).getOutcome())
                .as("--rerun-tasks must force the task to run regardless of cache state")
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(rerunTasks.getOutput()).contains("Jenkins is fully up and running");
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
