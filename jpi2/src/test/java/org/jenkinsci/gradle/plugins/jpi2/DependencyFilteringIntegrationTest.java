package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class DependencyFilteringIntegrationTest extends V2IntegrationTestBase {

    @Test
    void skipsJenkinsCoreDependencies() throws IOException {
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
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void skipsJarDependenciesOfPluginDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                    implementation("org.jenkins-ci.plugins:jackson2-api:2.19.0-404.vb_b_0fd2fea_e10")
                    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0") // This is a jar dependency that should not be included in the JPI
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
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void skipsJarDependenciesOfPluginDependenciesFromTransients() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                    implementation("org.jenkins-ci.plugins:jackson2-api:2.19.0-404.vb_b_0fd2fea_e10") // This will provide jackson-databind transitively
                    implementation("com.auth0:java-jwt:4.5.0") // This also depends on jackson-databind. We should include this jar, but not jackson-databind in the WEB-INF/lib dir.
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
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar", "java-jwt-4.5.0.jar");
    }

    @Test
    void playsWellWithGitPlugin() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import hudson.plugins.git.Branch;
                public class PluginAction {
                    private String name;
                    public PluginAction(String name) {
                        Branch branch;
                        this.name = name;
                    }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void playsWellWithLombok() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                }
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
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }
}
