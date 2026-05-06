package org.jenkinsci.gradle.plugins.jpi2;

import java.nio.file.Files;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class LocalizationIntegrationTest extends V2IntegrationTestBase {

    @Test
    void classesTaskCompilesGeneratedLocalizationSources() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig().getBytes(StandardCharsets.UTF_8));

        ith.mkDirInProjectDir("src/main/java/org/example");
        Files.write(ith.inProjectDir("src/main/java/org/example/UsesMessages.java").toPath(), /* language=java */ """
                package org.example;

                public class UsesMessages {
                    public String displayName() {
                        return Messages.displayName();
                    }
                }
                """.getBytes(StandardCharsets.UTF_8));
        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.write(ith.inProjectDir("src/main/resources/org/example/Messages.properties").toPath(), /* language=properties */ """
                displayName=Display Name
                """.getBytes(StandardCharsets.UTF_8));

        BuildResult result = ith.gradleRunner().withArguments("classes").build();

        assertThat(result.task(":localizeMessages")).isNotNull();
        assertThat(result.task(":localizeMessages").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        assertThat(result.task(":classes")).isNotNull();
        assertThat(result.task(":classes").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        assertThat(ith.inProjectDir("build/generated-src/localizer/org/example/Messages.java")).exists();
        assertThat(ith.inProjectDir("build/classes/java/main/org/example/Messages.class")).exists();
        assertThat(ith.inProjectDir("build/classes/java/main/org/example/UsesMessages.class")).exists();
    }

    @Test
    void localizeMessagesSupportsCustomOutputDirectory() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), (getBasePluginConfig() + /* language=kotlin */ """
                tasks.named<org.jenkinsci.gradle.plugins.jpi2.localization.LocalizationTask>("localizeMessages") {
                    outputDir.set(layout.buildDirectory.dir("custom-localizer"))
                }
                """).getBytes(StandardCharsets.UTF_8));

        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.write(ith.inProjectDir("src/main/resources/org/example/Messages.properties").toPath(), /* language=properties */ """
                key1=Value 1
                key2=Value 2
                """.getBytes(StandardCharsets.UTF_8));

        BuildResult result = ith.gradleRunner().withArguments("localizeMessages").build();

        assertThat(result.task(":localizeMessages")).isNotNull();
        assertThat(result.task(":localizeMessages").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        var generated = ith.inProjectDir("build/custom-localizer/org/example/Messages.java");
        assertThat(generated).exists();
        var generatedContent = Files.readString(generated.toPath());
        assertThat(generatedContent).contains("public static String key1()");
        assertThat(generatedContent).contains("public static String key2()");
    }

    @Test
    void sourcesJarIncludesGeneratedLocalizationSources() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig().getBytes(StandardCharsets.UTF_8));

        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.write(ith.inProjectDir("src/main/resources/org/example/Messages.properties").toPath(), /* language=properties */ """
                greeting=Hello
                """.getBytes(StandardCharsets.UTF_8));

        BuildResult result = ith.gradleRunner().withArguments("sourcesJar").build();

        assertThat(result.task(":localizeMessages")).isNotNull();
        assertThat(result.task(":localizeMessages").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        var sourcesJar = ith.inProjectDir("build/libs/test-plugin-1.0.0-sources.jar");
        assertThat(sourcesJar).exists();

        try (var jarFile = new JarFile(sourcesJar)) {
            assertThat(jarFile.getEntry("org/example/Messages.java")).isNotNull();
        }
    }

    @Test
    void localizeMessagesWorksInMultiModuleBuild() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        Files.write(ith.inProjectDir("settings.gradle.kts").toPath(), /* language=kotlin */ """
                rootProject.name = "test-plugin"
                include("plugin")
                """.getBytes(StandardCharsets.UTF_8));
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), new byte[0]);
        ith.mkDirInProjectDir("plugin");
        Files.write(ith.inProjectDir("plugin/build.gradle.kts").toPath(), getBasePluginConfig().getBytes(StandardCharsets.UTF_8));
        ith.mkDirInProjectDir("plugin/src/main/resources");
        Files.write(ith.inProjectDir("plugin/src/main/resources/Messages.properties").toPath(), /* language=properties */ """
                key3=Value 3
                key4=Value 4
                """.getBytes(StandardCharsets.UTF_8));

        BuildResult result = ith.gradleRunner().withArguments(":plugin:localizeMessages").build();

        assertThat(result.task(":plugin:localizeMessages")).isNotNull();
        assertThat(result.task(":plugin:localizeMessages").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        var generated = ith.inProjectDir("plugin/build/generated-src/localizer/Messages.java");
        assertThat(generated).exists();
        var generatedContent = Files.readString(generated.toPath());
        assertThat(generatedContent).contains("public static String key3()");
        assertThat(generatedContent).contains("public static String key4()");
    }

    @Test
    void localizeMessagesSupportsConfigurationCache() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig().getBytes(StandardCharsets.UTF_8));

        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.write(ith.inProjectDir("src/main/resources/org/example/Messages.properties").toPath(), /* language=properties */ """
                greeting=Hello
                """.getBytes(StandardCharsets.UTF_8));

        var gradleRunner = ith.gradleRunner();
        BuildResult firstRun = gradleRunner.withArguments("--configuration-cache", "localizeMessages", "-i").build();
        BuildResult secondRun = gradleRunner.withArguments("--configuration-cache", "localizeMessages", "-i").build();

        assertThat(firstRun.task(":localizeMessages")).isNotNull();
        assertThat(firstRun.task(":localizeMessages").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        assertThat(firstRun.getOutput()).contains("Configuration cache entry stored");

        assertThat(secondRun.task(":localizeMessages")).isNotNull();
        assertThat(secondRun.task(":localizeMessages").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(secondRun.getOutput()).contains("Configuration cache entry reused");
    }

    @Test
    void extensionLocalizerVersionOverridesGradleProperty() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), (getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    localizerVersion.set("1.30")
                }
                """).getBytes(StandardCharsets.UTF_8));
        Files.write(ith.inProjectDir("gradle.properties").toPath(), /* language=properties */ """
                jenkins.localizer.version=1.31
                """.getBytes(StandardCharsets.UTF_8));

        BuildResult result = ith.gradleRunner()
                .withArguments("dependencies", "--configuration=localizeMessagesRuntimeClasspath")
                .build();

        assertThat(result.getOutput()).contains("org.jvnet.localizer:localizer-maven-plugin:1.30");
        assertThat(result.getOutput()).doesNotContain("org.jvnet.localizer:localizer-maven-plugin:1.31");
    }
}
