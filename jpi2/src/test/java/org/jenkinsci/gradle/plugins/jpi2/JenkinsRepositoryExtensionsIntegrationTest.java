package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class JenkinsRepositoryExtensionsIntegrationTest extends V2IntegrationTestBase {

    @Test
    void publishingRepositoryDoesNotResolveGitForUnrelatedTasks() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                import org.jenkinsci.gradle.plugins.jpi2.publishToJenkins
                import org.jenkinsci.gradle.plugins.jpi2.VersionSource

                plugins { id("org.jenkins-ci.jpi2") }
                jenkinsPlugin {
                    versionSource.set(VersionSource.GIT)
                    gitVersion {
                        gitRoot.set(layout.projectDirectory.dir("missing-repository"))
                    }
                }
                publishing { repositories { publishToJenkins() } }
                """);
        var runner = ith.gradleRunner().withArguments("help", "--configuration-cache");

        var first = runner.build();
        var second = runner.build();

        assertThat(first.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(second.getOutput()).contains("Configuration cache entry reused");
    }

    @TestFactory
    Stream<DynamicTest> publishingRepositoryFollowsEffectiveVersion() {
        record Scenario(String projectVersion, String fixedVersion, String repository) {}
        return Stream.of(
                        new Scenario("1.0.0", "2.0.0-SNAPSHOT", "snapshots"),
                        new Scenario("1.0.0", "2.0.0-rc1.abcdef", "incrementals"),
                        new Scenario("1.0.0-SNAPSHOT", "2.0.0", "releases"),
                        new Scenario("1.0.0-SNAPSHOT", null, "snapshots"))
                .map(scenario -> DynamicTest.dynamicTest(scenario.toString(), () -> {
                    var directory = Files.createTempDirectory(tempDir.toPath(), "repository-")
                            .toFile();
                    var ith = new IntegrationTestHelper(directory, "8.14");
                    initBuild(ith);
                    var fixedVersion = scenario.fixedVersion() == null ? "" : """
                            jenkinsPlugin {
                                versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.FIXED)
                                fixedVersion.set("%s")
                            }
                            """.formatted(scenario.fixedVersion());
                    Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                            import org.jenkinsci.gradle.plugins.jpi2.publishToJenkins

                            plugins { id("org.jenkins-ci.jpi2") }
                            version = "%s"
                            %s
                            publishing { repositories { publishToJenkins() } }
                            afterEvaluate {
                                val repository = publishing.repositories.getByName("jenkinsPublish") as MavenArtifactRepository
                                check(repository.url.toString() == "https://repo.jenkins-ci.org/%s/")
                            }
                            """.formatted(
                                    scenario.projectVersion(), fixedVersion, scenario.repository()));
                    ith.gradleRunner().withArguments("help").build();
                }));
    }

    @Test
    void kotlinDslRegistersIncrementalsAndSnapshotsRepositories() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), /* language=kotlin */ """
                import java.net.URI
                import org.gradle.kotlin.dsl.jenkinsIncrementals
                import org.gradle.kotlin.dsl.jenkinsSnapshots

                plugins {
                    id("org.jenkins-ci.jpi2")
                }

                repositories {
                    val incrementals = jenkinsIncrementals()
                    check(incrementals.name == "jenkinsIncrementals")
                    check(incrementals.url == URI("https://repo.jenkins-ci.org/incrementals/"))
                    check(findByName("jenkinsIncrementals") === incrementals)

                    val snapshots = jenkinsSnapshots()
                    check(snapshots.name == "jenkinsSnapshots")
                    check(snapshots.url == URI("https://repo.jenkins-ci.org/snapshots/"))
                    check(findByName("jenkinsSnapshots") === snapshots)
                }
                """);

        var result = ith.gradleRunner().withArguments("help").build();

        var task = result.task(":help");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }
}
