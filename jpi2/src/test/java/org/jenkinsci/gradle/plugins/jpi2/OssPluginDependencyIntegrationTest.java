package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class OssPluginDependencyIntegrationTest extends V2IntegrationTestBase {

    @Test
    void gradleBuildWithOssPluginDependencyShouldBuild() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssPluginDependency(ith);

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
        assertThat(manifestData.getValue("Plugin-Dependencies")).isEqualTo("git:5.7.0");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void gradleBuildWithOssPluginDependencyShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssPluginDependency(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");

        // the selected plugin
        var pluginsDir = ith.inProjectDir("work/plugins");
        assertThat(pluginsDir).exists();

        var files = Arrays.stream(Objects.requireNonNull(pluginsDir.list())).sorted().toList();
        assertThat(files).isNotNull()
                .containsExactly(
                        "apache-httpcomponents-client-4-api.jpi",
                        "asm-api.jpi",
                        "bouncycastle-api.jpi",
                        "caffeine-api.jpi",
                        "credentials-binding.jpi",
                        "credentials.jpi",
                        "display-url-api.jpi",
                        "git-client.jpi",
                        "git.jpi",
                        "gson-api.jpi",
                        "instance-identity.jpi",
                        "jakarta-activation-api.jpi",
                        "jakarta-mail-api.jpi",
                        "mailer.jpi",
                        "mina-sshd-api-common.jpi",
                        "mina-sshd-api-core.jpi",
                        "plain-credentials.jpi",
                        "scm-api.jpi",
                        "script-security.jpi",
                        "ssh-credentials.jpi",
                        "structs.jpi",
                        "test-plugin.jpi",
                        "variant.jpi",
                        "workflow-scm-step.jpi",
                        "workflow-step-api.jpi"
                );
    }
}
