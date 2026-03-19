package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
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

        testServerStarts(ith.gradleRunner(), "-D" + WorkDirectorySettings.SYSTEM_PROPERTY + "=" + customWorkDir, "hplRun");

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
    void simpleGradleBuildShouldCoexistWithApplicationRunTask() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithApplicationPlugin(ith);

        var result = ith.gradleRunner().withArguments("tasks", "--all").build();

        assertThat(result.getOutput()).contains("run - Runs this project as a JVM application");
        assertThat(result.getOutput()).contains("hplRun");
    }
}
