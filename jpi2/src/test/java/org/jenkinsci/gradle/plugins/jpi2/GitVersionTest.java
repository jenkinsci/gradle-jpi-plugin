package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class GitVersionTest {
    @TempDir
    Path tempDir;

    @TestFactory
    Stream<DynamicTest> computesCleanRepositoryVersion() {
        return Stream.of("checkout", "worktree", "subdirectory")
                .map(layout -> dynamicTest(layout, () -> {
                    var repository = initRepository(Files.createDirectory(tempDir.resolve(layout)));
                    var root = switch (layout) {
                        case "worktree" -> {
                            var worktree = tempDir.resolve("linked-worktree");
                            GitVersion.runGit(repository, "worktree", "add", "--detach", worktree.toString());
                            yield worktree;
                        }
                        case "subdirectory" -> Files.createDirectory(repository.resolve("module"));
                        default -> repository;
                    };

                    var result = GitVersion.compute(root, "%d.%s", "", 12, false);

                    assertThat(result.version())
                            .isEqualTo("1." + result.fullHash().substring(0, 12));
                    assertThat(result.fullHash()).matches("[a-f0-9]{40}");
                }));
    }

    @Test
    void rejectsDirtyRepositoryUnlessAllowed() throws Exception {
        var repository = initRepository(tempDir);
        Files.writeString(repository.resolve("untracked.txt"), "dirty");

        assertThatThrownBy(() -> GitVersion.compute(repository, "%d.%s", "", 12, false))
                .hasMessageContaining("uncommitted changes");
        assertThat(GitVersion.compute(repository, "%d.%s", "", 12, true).version())
                .matches("1\\.[a-f0-9]{12}");
    }

    @Test
    void reportsNonRepository() {
        assertThatThrownBy(() -> GitVersion.compute(tempDir, "%d.%s", "", 12, false))
                .hasMessageContaining("Not a Git repository");
    }

    private static Path initRepository(Path root) throws Exception {
        GitVersion.runGit(root, "init");
        GitVersion.runGit(root, "config", "user.name", "Plugin Tests");
        GitVersion.runGit(root, "config", "user.email", "plugin-tests@example.invalid");
        GitVersion.runGit(root, "-c", "commit.gpgSign=false", "commit", "--allow-empty", "-m", "Initial commit");
        return root;
    }
}
