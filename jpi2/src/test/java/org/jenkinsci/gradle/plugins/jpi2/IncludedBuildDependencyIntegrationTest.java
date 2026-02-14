package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class IncludedBuildDependencyIntegrationTest extends V2IntegrationTestBase {

    @Test
    void prepareServerShouldHandleIncludedBuildProjectDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithIncludedBuildLibraryDependency(ith);

        // when
        var result = ith.gradleRunner()
                .withArguments("prepareServer")
                .build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var rootPlugin = ith.inProjectDir("work/plugins/test-plugin.jpi");
        assertThat(rootPlugin).exists();

        var includedBuildPlugin = ith.inProjectDir("work/plugins/lib.jpi");
        assertThat(includedBuildPlugin).doesNotExist();
    }

    private static void configureBuildWithIncludedBuildLibraryDependency(IntegrationTestHelper ith) throws IOException {
        Files.write(/* language=kotlin */ """
                rootProject.name = "test-plugin"
                includeBuild("included-build")
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("settings.gradle.kts"));

        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.example:lib:1.0.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.mkDirInProjectDir("included-build/lib/src/main/java/com/example/lib");

        Files.write(/* language=kotlin */ """
                rootProject.name = "included-build"
                include("lib")
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("included-build/settings.gradle.kts"));

        Files.write(/* language=kotlin */ """
                allprojects {
                    group = "com.example"
                    version = "1.0.0"
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("included-build/build.gradle.kts"));

        Files.write(/* language=kotlin */ """
                plugins {
                    id("java-library")
                }
                repositories {
                    mavenCentral()
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("included-build/lib/build.gradle.kts"));

        Files.write(/* language=java */ """
                package com.example.lib;
                public class IncludedBuildLibrary {
                    public String hello() {
                        return "hello";
                    }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("included-build/lib/src/main/java/com/example/lib/IncludedBuildLibrary.java"));
    }
}
