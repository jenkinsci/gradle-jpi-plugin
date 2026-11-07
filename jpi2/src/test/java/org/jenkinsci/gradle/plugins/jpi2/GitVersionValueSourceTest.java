package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitVersionValueSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void repeatedReadsShareTheSameGitQueryResult() throws Exception {
        GitVersion.runGit(tempDir, "init");
        GitVersion.runGit(tempDir, "config", "user.name", "Plugin Tests");
        GitVersion.runGit(tempDir, "config", "user.email", "tests@example.invalid");
        GitVersion.runGit(tempDir, "-c", "commit.gpgSign=false", "commit", "--allow-empty", "-m", "Initial commit");
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var extension = project.getObjects()
                .newInstance(
                        GitVersionExtension.class, project.getObjects(), project.getLayout(), project.getProviders());
        extension.getAllowDirty().set(true);
        var first = extension.getVersion().get();

        GitVersion.runGit(tempDir, "-c", "commit.gpgSign=false", "commit", "--allow-empty", "-m", "Next commit");

        assertThat(extension.getVersion().get()).isEqualTo(first).startsWith("1.");
        assertThat(GitVersion.compute(tempDir, "%d.%s", "", 12, true).version()).startsWith("2.");
    }
}
