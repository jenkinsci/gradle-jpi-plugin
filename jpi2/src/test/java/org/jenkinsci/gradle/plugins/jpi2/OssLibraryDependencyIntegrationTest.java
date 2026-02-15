package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class OssLibraryDependencyIntegrationTest extends V2IntegrationTestBase {

    @Test
    void gradleBuildWithOssLibraryDependencyShouldBuild() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssLibraryDependency(ith);

        // when
        ith.gradleRunner().withArguments("build", "publish").build();

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
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("nothing-java-0.2.0.jar",
                        "test-plugin-1.0.0.jar",
                        "commons-math3-3.6.1.jar",
                        "commons-lang3-3.12.0.jar");
    }

    @Test
    void gradleBuildWithOssLibraryDependencyShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssLibraryDependency(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "testServer");
    }
}
