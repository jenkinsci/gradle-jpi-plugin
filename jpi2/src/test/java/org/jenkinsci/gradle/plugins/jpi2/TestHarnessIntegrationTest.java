package org.jenkinsci.gradle.plugins.jpi2;

import java.nio.file.Files;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class TestHarnessIntegrationTest extends V2IntegrationTestBase {

    @Test
    void shouldHaveTestDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.writeString(ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java").toPath(), /* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import org.jvnet.hudson.test.JenkinsRule;
                import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
                import static org.junit.jupiter.api.Assertions.*;
                @WithJenkins
                public class PluginTest {
                    /** @noinspection JUnitMalformedDeclaration*/
                    @Test
                    void test(JenkinsRule j) {
                        var injector = j.jenkins.getInjector();
                        //noinspection DataFlowIssue
                        assertNotNull(injector);
                    }
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2414.v185474555e66")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.3");
    }

    @Test
    void testCanAccessJpiDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """);
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.writeString(ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java").toPath(), /* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                import hudson.plugins.git.Branch;
                public class PluginTest {
                    @Test
                    void test() {
                        Branch branch = null;
                        assertNull(branch);
                    }
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2414.v185474555e66")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.3");
    }

    @Test
    void shouldCustomizeJenkinsVersionAndTestHarnessVersion() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("gradle.properties").toPath(), /* language=properties */ """
                jenkins.version=2.492.1
                jenkins.testharness.version=2411.v1e79b_0dc94b_7
                """);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.writeString(ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java").toPath(), /* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import org.jvnet.hudson.test.JenkinsRule;
                import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
                import static org.junit.jupiter.api.Assertions.*;
                @WithJenkins
                public class PluginTest {
                    /** @noinspection JUnitMalformedDeclaration*/
                    @Test
                    void test(JenkinsRule j) {
                        var injector = j.jenkins.getInjector();
                        //noinspection DataFlowIssue,ObviousNullCheck
                        assertNotNull(injector);
                    }
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2411.v1e79b_0dc94b_7")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.1");
    }

    @Test
    void shouldCustomizeValuesViaExtensionAndSetters() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    jenkinsVersion.set("2.492.1")
                    testHarnessVersion.set("2411.v1e79b_0dc94b_7")
                    pluginId.set("custom-plugin-id")
                    displayName.set("Custom Plugin Name")
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();

        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData).isNotNull().isNotEmpty();
        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.1");
        assertThat(manifestData.getValue("Short-Name")).isEqualTo("custom-plugin-id");
        assertThat(manifestData.getValue("Extension-Name")).isEqualTo("custom-plugin-id");
        assertThat(manifestData.getValue("Long-Name")).isEqualTo("Custom Plugin Name");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2411.v1e79b_0dc94b_7")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.1");
    }

    @Test
    void shouldCustomizeValuesViaExtension() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    jenkinsVersion = "2.492.1"
                    testHarnessVersion = "2411.v1e79b_0dc94b_7"
                    pluginId = "custom-plugin-id"
                    displayName = "Custom Plugin Name"
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();

        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData).isNotNull().isNotEmpty();
        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.1");
        assertThat(manifestData.getValue("Short-Name")).isEqualTo("custom-plugin-id");
        assertThat(manifestData.getValue("Extension-Name")).isEqualTo("custom-plugin-id");
        assertThat(manifestData.getValue("Long-Name")).isEqualTo("Custom Plugin Name");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2411.v1e79b_0dc94b_7")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.1");
    }

    @Test
    void shouldCustomizeValuesViaExtensionAndGroovy() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle").toPath(), /* language=groovy */ """
                plugins {
                    id("org.jenkins-ci.jpi2")
                }
                repositories {
                    mavenCentral()
                    jenkinsPublic()
                }
                tasks.named("server") {
                    args("--httpPort=%d")
                }
                tasks.withType(Test) {
                    useJUnitPlatform()
                }
                jenkinsPlugin {
                    jenkinsVersion = "2.492.1"
                    testHarnessVersion = "2411.v1e79b_0dc94b_7"
                    pluginId = "custom-plugin-id"
                    displayName = "Custom Plugin Name"
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();

        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData).isNotNull().isNotEmpty();
        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.1");
        assertThat(manifestData.getValue("Short-Name")).isEqualTo("custom-plugin-id");
        assertThat(manifestData.getValue("Extension-Name")).isEqualTo("custom-plugin-id");
        assertThat(manifestData.getValue("Long-Name")).isEqualTo("Custom Plugin Name");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2411.v1e79b_0dc94b_7")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.1");
    }

    @Test
    void jpiDependenciesShouldBeInstalledAsPluginsNotJustOnClasspath() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """);
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.writeString(ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java").toPath(), /* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import org.jvnet.hudson.test.JenkinsRule;
                import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
                import static org.junit.jupiter.api.Assertions.*;
                @WithJenkins
                public class PluginTest {
                    /** @noinspection JUnitMalformedDeclaration*/
                    @Test
                    void test(JenkinsRule j) {
                        // The git plugin is only usable at runtime if the Jenkins
                        // instance under test actually has it installed, not merely
                        // available on the classpath.
                        assertNotNull(j.jenkins.getPluginManager().getPlugin("git"),
                                "git plugin should be installed in the JenkinsRule instance");
                    }
                }
                """);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }
}
