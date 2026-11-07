package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class GitVersionValueSourceIntegrationTest extends V2IntegrationTestBase {
    @Test
    void revalidatesGitStateBeforeReusingConfigurationCache() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        var root = tempDir.toPath();
        Files.writeString(root.resolve(".gitignore"), "build/\n.gradle/\nversion.txt\n");
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                jenkinsPlugin { versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.GIT) }
                val gitVersion = jenkinsPlugin.getEffectiveVersion().get()
                tasks.register("writeVersion") {
                    val output = layout.projectDirectory.file("version.txt")
                    val version = gitVersion
                    doLast { output.asFile.writeText(version) }
                }
                """);
        var runner = ith.gradleRunner().withArguments("writeVersion", "--configuration-cache");
        GitVersion.runGit(root, "init");
        GitVersion.runGit(root, "config", "user.name", "Plugin Tests");
        GitVersion.runGit(root, "config", "user.email", "tests@example.invalid");
        GitVersion.runGit(root, "add", ".");
        GitVersion.runGit(root, "-c", "commit.gpgSign=false", "commit", "-m", "Initial commit");

        runner.build();
        assertThat(Files.readString(root.resolve("version.txt"))).startsWith("1.");
        assertThat(runner.build().getOutput()).contains("Reusing configuration cache");

        GitVersion.runGit(root, "-c", "commit.gpgSign=false", "commit", "--allow-empty", "-m", "Next commit");
        var changed = runner.build();
        assertThat(changed.getOutput()).doesNotContain("Reusing configuration cache");
        assertThat(Files.readString(root.resolve("version.txt"))).startsWith("2.");

        Files.writeString(root.resolve("untracked.txt"), "dirty");
        assertThat(runner.buildAndFail().getOutput()).contains("uncommitted changes");
    }
}
