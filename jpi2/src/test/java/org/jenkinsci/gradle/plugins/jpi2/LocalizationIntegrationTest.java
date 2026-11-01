package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class LocalizationIntegrationTest extends V2IntegrationTestBase {

    @Test
    void removesGeneratedSourcesAndClassesWhenBundlesAreDeletedOrRenamed() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);
        var first =
                ith.inProjectDir("src/main/resources/first/Messages.properties").toPath();
        var second = ith.inProjectDir("src/main/resources/second/Messages.properties")
                .toPath();
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.writeString(first, "greeting=Hello\n");
        Files.writeString(second, "greeting=Goodbye\n");
        var runner = ith.gradleRunner();
        runner.withArguments("classes", "--configuration-cache").build();
        assertThat(ith.inProjectDir("build/generated-src/localizer/first/Messages.java"))
                .exists();
        assertThat(ith.inProjectDir("build/classes/java/main/first/Messages.class"))
                .exists();

        Files.delete(first);
        runner.withArguments("classes", "--configuration-cache").build();
        assertThat(ith.inProjectDir("build/generated-src/localizer/first/Messages.java"))
                .doesNotExist();
        assertThat(ith.inProjectDir("build/classes/java/main/first/Messages.class"))
                .doesNotExist();
        assertThat(ith.inProjectDir("build/generated-src/localizer/second/Messages.java"))
                .exists();

        var renamed = ith.inProjectDir("src/main/resources/renamed/Messages.properties")
                .toPath();
        Files.createDirectories(renamed.getParent());
        Files.move(second, renamed);
        runner.withArguments("classes", "--configuration-cache").build();
        assertThat(ith.inProjectDir("build/generated-src/localizer/second/Messages.java"))
                .doesNotExist();
        assertThat(ith.inProjectDir("build/classes/java/main/second/Messages.class"))
                .doesNotExist();
        assertThat(ith.inProjectDir("build/generated-src/localizer/renamed/Messages.java"))
                .exists();
        assertThat(ith.inProjectDir("build/classes/java/main/renamed/Messages.class"))
                .exists();

        Files.delete(renamed);
        runner.withArguments("classes", "--configuration-cache").build();
        assertThat(ith.inProjectDir("build/generated-src/localizer/renamed/Messages.java"))
                .doesNotExist();
        assertThat(ith.inProjectDir("build/classes/java/main/renamed/Messages.class"))
                .doesNotExist();
    }

    @Test
    void classesTaskCompilesGeneratedLocalizationSources() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());

        ith.mkDirInProjectDir("src/main/java/org/example");
        Files.writeString(
                ith.inProjectDir("src/main/java/org/example/UsesMessages.java").toPath(), /* language=java */ """
                package org.example;

                public class UsesMessages {
                    public String displayName() {
                        return Messages.displayName();
                    }
                }
                """);
        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.writeString(
                ith.inProjectDir("src/main/resources/org/example/Messages.properties")
                        .toPath(), /* language=properties */
                """
                displayName=Display Name
                """);

        BuildResult result = ith.gradleRunner().withArguments("classes").build();

        var localizeMessagesTask = result.task(":localizeMessages");
        assertThat(localizeMessagesTask).isNotNull();
        assertThat(localizeMessagesTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        var classesTask = result.task(":classes");
        assertThat(classesTask).isNotNull();
        assertThat(classesTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        assertThat(ith.inProjectDir("build/generated-src/localizer/org/example/Messages.java"))
                .exists();
        assertThat(ith.inProjectDir("build/classes/java/main/org/example/Messages.class"))
                .exists();
        assertThat(ith.inProjectDir("build/classes/java/main/org/example/UsesMessages.class"))
                .exists();
    }

    @Test
    void localizeMessagesSupportsCustomOutputDirectory() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                tasks.named<org.jenkinsci.gradle.plugins.jpi2.localization.LocalizationTask>("localizeMessages") {
                    outputDir.set(layout.buildDirectory.dir("custom-localizer"))
                }
                """);

        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.writeString(
                ith.inProjectDir("src/main/resources/org/example/Messages.properties")
                        .toPath(), /* language=properties */
                """
                key1=Value 1
                key2=Value 2
                """);

        BuildResult result =
                ith.gradleRunner().withArguments("localizeMessages").build();

        var localizeMessagesTask = result.task(":localizeMessages");
        assertThat(localizeMessagesTask).isNotNull();
        assertThat(localizeMessagesTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
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
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());

        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.writeString(
                ith.inProjectDir("src/main/resources/org/example/Messages.properties")
                        .toPath(), /* language=properties */
                """
                greeting=Hello
                """);

        BuildResult result = ith.gradleRunner().withArguments("sourcesJar").build();

        var localizeMessagesTask = result.task(":localizeMessages");
        assertThat(localizeMessagesTask).isNotNull();
        assertThat(localizeMessagesTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        var sourcesJar = ith.inProjectDir("build/libs/test-plugin-1.0.0-sources.jar");
        assertThat(sourcesJar).exists();

        try (var jarFile = new JarFile(sourcesJar)) {
            assertThat(jarFile.getEntry("org/example/Messages.java")).isNotNull();
        }
    }

    @Test
    void localizeMessagesWorksInMultiModuleBuild() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        Files.writeString(ith.inProjectDir("settings.gradle.kts").toPath(), /* language=kotlin */ """
                rootProject.name = "test-plugin"
                include("plugin")
                """);
        Files.write(ith.inProjectDir("build.gradle.kts").toPath(), new byte[0]);
        ith.mkDirInProjectDir("plugin");
        Files.writeString(ith.inProjectDir("plugin/build.gradle.kts").toPath(), getBasePluginConfig());
        ith.mkDirInProjectDir("plugin/src/main/resources");
        Files.writeString(
                ith.inProjectDir("plugin/src/main/resources/Messages.properties")
                        .toPath(), /* language=properties */
                """
                key3=Value 3
                key4=Value 4
                """);

        BuildResult result =
                ith.gradleRunner().withArguments(":plugin:localizeMessages").build();

        var localizeMessagesTask = result.task(":plugin:localizeMessages");
        assertThat(localizeMessagesTask).isNotNull();
        assertThat(localizeMessagesTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
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
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());

        ith.mkDirInProjectDir("src/main/resources/org/example");
        Files.writeString(
                ith.inProjectDir("src/main/resources/org/example/Messages.properties")
                        .toPath(), /* language=properties */
                """
                greeting=Hello
                """);

        var gradleRunner = ith.gradleRunner();
        BuildResult firstRun = gradleRunner
                .withArguments("--configuration-cache", "localizeMessages", "-i")
                .build();
        BuildResult secondRun = gradleRunner
                .withArguments("--configuration-cache", "localizeMessages", "-i")
                .build();

        var firstTask = firstRun.task(":localizeMessages");
        assertThat(firstTask).isNotNull();
        assertThat(firstTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE);
        assertThat(firstRun.getOutput()).contains("Configuration cache entry stored");

        var secondTask = secondRun.task(":localizeMessages");
        assertThat(secondTask).isNotNull();
        assertThat(secondTask.getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(secondRun.getOutput()).contains("Configuration cache entry reused");
    }

    @Test
    void extensionLocalizerVersionOverridesGradleProperty() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    localizerVersion.set("1.30")
                }
                """);
        Files.writeString(ith.inProjectDir("gradle.properties").toPath(), /* language=properties */ """
                jenkins.localizer.version=1.31
                """);

        BuildResult result = ith.gradleRunner()
                .withArguments("dependencies", "--configuration=localizeMessagesRuntimeClasspath")
                .build();

        assertThat(result.getOutput()).contains("org.jvnet.localizer:localizer-maven-plugin:1.30");
        assertThat(result.getOutput()).doesNotContain("org.jvnet.localizer:localizer-maven-plugin:1.31");
    }
}
