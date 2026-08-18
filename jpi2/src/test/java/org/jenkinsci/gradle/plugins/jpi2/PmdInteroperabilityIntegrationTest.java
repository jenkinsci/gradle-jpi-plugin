package org.jenkinsci.gradle.plugins.jpi2;

import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class PmdInteroperabilityIntegrationTest extends V2IntegrationTestBase {

    /**
     * HpiMetadataRule adds a "defaultRuntime" variant (attributed {@code ArtifactType.DEFAULT})
     * alongside the existing "runtime" variant (attributed {@code ArtifactType.PLUGIN_JAR}) to
     * every hpi/jpi-packaged dependency. Configurations the plugin controls (runtimeClasspath,
     * testRuntimeClasspath) request {@code ArtifactType.PLUGIN_JAR} explicitly to disambiguate,
     * but the `pmd` plugin resolves its own aux classpath configuration, which never requests
     * that attribute. ArtifactTypeDisambiguationRule resolves the ambiguity for such consumers
     * by defaulting to the plain jar variant when nothing is requested.
     */
    @Test
    void pmdTestResolvesAuxClasspathForJpiPluginDependency() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                plugins {
                    id("org.jenkins-ci.jpi2")
                    pmd
                }
                repositories {
                    mavenCentral()
                    jenkinsPublic()
                }
                tasks.withType(Test::class) {
                    useJUnitPlatform()
                }
                group = "com.example"
                version = "1.0.0"
                dependencies {
                    testImplementation("org.jenkins-ci.plugins:git:5.7.0")
                    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }
                """, StandardCharsets.UTF_8);
        ith.mkDirInProjectDir("src/test/java/com/example");
        Files.writeString(ith.inProjectDir("src/test/java/com/example/ExampleTest.java").toPath(), """
                package com.example;
                import org.junit.jupiter.api.Test;
                class ExampleTest {
                    @Test
                    void example() {
                    }
                }
                """, StandardCharsets.UTF_8);

        // when
        var result = ith.gradleRunner().withArguments("pmdTest").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }
}
