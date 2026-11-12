package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ProjectArchiveStagingIntegrationTest extends V2IntegrationTestBase {
    @TestFactory
    Stream<DynamicTest> stagesActualArchiveNamesUsingPluginIds() {
        return Stream.of("fixed", "git", "custom-archive", "matching-filenames")
                .map(mode -> dynamicTest(mode, () -> {
                    var root = Files.createDirectory(tempDir.toPath().resolve(mode));
                    var producer = Files.createDirectory(root.resolve("producer"));
                    var ith = new IntegrationTestHelper(root.toFile(), "8.14");
                    Files.writeString(root.resolve("settings.gradle.kts"), """
                    rootProject.name = "consumer"
                    include("producer")
                    """);
                    Files.writeString(root.resolve("build.gradle.kts"), getBasePluginConfig() + """
                    dependencies { implementation(project(":producer")) }
                    jenkinsPlugin {
                        pluginId.set("consumer-id")
                        archiveExtension.set("hpi")
                    }
                    """);
                    if (mode.equals("matching-filenames")) {
                        Files.writeString(root.resolve("build.gradle.kts"), """
                        tasks.named<War>("jpi") { archiveFileName.set("identical.jpi") }
                        """, StandardOpenOption.APPEND);
                    }
                    var versionConfig = mode.equals("git") ? """
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.GIT)
                    gitVersion { allowDirty.set(true) }
                    """ : """
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.FIXED)
                    fixedVersion.set("2.0-beta")
                    """;
                    Files.writeString(
                            producer.resolve("build.gradle.kts"),
                            getBasePluginConfig()
                                    + """
                    jenkinsPlugin {
                        pluginId.set("upstream-id")
                        %s
                    }
                    tasks.named<War>("jpi") {
                        archiveBaseName.set("renamed-archive")
                        archiveClassifier.set("linux")
                        %s
                    }
                    """.formatted(
                                                    versionConfig,
                                                    mode.equals("custom-archive")
                                                            ? "archiveVersion.set(\"custom\")"
                                                            : mode.equals("matching-filenames")
                                                                    ? "archiveFileName.set(\"identical.jpi\")"
                                                                    : ""));
                    if (mode.equals("git")) {
                        GitVersion.runGit(root, "init");
                        GitVersion.runGit(
                                root,
                                "-c",
                                "user.name=Tests",
                                "-c",
                                "user.email=tests@example.invalid",
                                "-c",
                                "commit.gpgSign=false",
                                "commit",
                                "--allow-empty",
                                "-m",
                                "Initial commit");
                    }
                    var runner = ith.gradleRunner()
                            .withArguments(
                                    "prepareServer",
                                    mode.equals("git") ? "--no-configuration-cache" : "--configuration-cache");

                    runner.build();
                    var second = runner.build();

                    if (!mode.equals("git")) {
                        assertThat(second.getOutput()).contains("Reusing configuration cache");
                    }
                    assertThat(root.resolve("work/plugins").toFile().list())
                            .containsExactlyInAnyOrder("consumer-id.hpi", "upstream-id.hpi");
                }));
    }
}
