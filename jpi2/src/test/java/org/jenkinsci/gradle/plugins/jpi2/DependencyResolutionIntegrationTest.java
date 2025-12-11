package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class DependencyResolutionIntegrationTest extends V2IntegrationTestBase {

    @Test
    void generatesExtensionsWithSezpoz() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig()).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write((/* language=java */ """
                package com.example.plugin;
                @hudson.Extension
                public class SomeExtension {
                    public static void init() {
                        System.out.println("Hello from SomeExtension");
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/SomeExtension.java"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        gradleRunner.withArguments("build").build();

        // then
        var extensionsList = ith.inProjectDir("build/classes/java/main/META-INF/annotations/hudson.Extension.txt");
        assertThat(extensionsList).exists();

        var lines = Files.readLines(extensionsList, StandardCharsets.UTF_8);
        assertThat(lines.subList(1, lines.size())).containsExactlyInAnyOrder("com.example.plugin.SomeExtension");
    }

    @Test
    void getsCorrectGuiceVersion() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                    runtimeOnly("com.google.inject:guice:5.1.0") // This is an older version of Guice that should get upgraded
                }
                configurations.getByName("compileClasspath").shouldResolveConsistentlyWith(configurations.getByName("runtimeClasspath"))
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import lombok.*;
                import hudson.Extension;
                import jakarta.inject.Inject;
                import hudson.model.RootAction;
                @Extension
                @NoArgsConstructor
                public class PluginAction implements RootAction {
                    private String name;
                    @Inject
                    public PluginAction(String name) {
                        this.name = name;
                    }
                    public String getUrlName() { return "example"; }
                    public String getDisplayName() { return "Example plugin"; }
                    public String getIconFileName() { return null; }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("dependencies", "--configuration=defaultRuntime").build();

        // then
        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("com.google.inject:guice:5.1.0 -> 6.0.0");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=compileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("com.google.inject:guice:6.0.0");


        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=runtimeClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("com.google.inject:guice:5.1.0 -> 6.0.0");

    }

    @Test
    void dependencyTreeIsCorrectForRuntimeClasspath() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        var gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("dependencies", "--configuration=runtimeClasspath").build();

        // then
        var expected = getClass().getClassLoader().getResourceAsStream("org/jenkinsci/gradle/plugins/jpi2/runtimeClasspath.txt");

        List<String> actualList = Arrays.stream(result.getOutput().split("\n")).toList();
        Assertions.assertNotNull(expected);
        List<String> expectedList = IOUtils.readLines(expected, StandardCharsets.UTF_8);
        assertDependencyTreesMatch(actualList, expectedList);
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void dependencyTreeIsCorrectForCompileClasspath() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        var gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("dependencies", "--configuration=compileClasspath").build();

        // then
        var expected = getClass().getClassLoader().getResourceAsStream("org/jenkinsci/gradle/plugins/jpi2/compileClasspath.txt");

        List<String> actualList = Arrays.stream(result.getOutput().split("\n")).toList();
        Assertions.assertNotNull(expected);
        List<String> expectedList = IOUtils.readLines(expected, StandardCharsets.UTF_8);
        assertDependencyTreesMatch(actualList, expectedList);
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void respectsExclusions() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0") {
                        exclude(group = "org.apache.commons", module = "commons-lang3")
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        var gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("jpi").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
        var libs = ith.inProjectDir("build/jpi/WEB-INF/lib");

        assertThat(libs).exists();

        var jpiLibs = libs.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("nothing-java-0.2.0.jar",
                        "test-plugin-1.0.0.jar",
                        "commons-math3-3.6.1.jar");
    }
}
