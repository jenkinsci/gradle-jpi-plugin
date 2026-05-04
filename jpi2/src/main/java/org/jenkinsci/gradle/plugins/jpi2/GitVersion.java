package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Computes a version string from the Git repository (commit depth + abbreviated hash).
 */
public final class GitVersion {

    private GitVersion() {}

    /**
     * Result of computing the Git-derived version (version string and full hash for file output).
     */
    public record VersionResult(String version, String fullHash) {}

    /**
     * Computes the version string and full hash from the given Git repository and format options.
     *
     * @param gitRoot       root of the Git repository
     * @param versionFormat format string (e.g. "%d.%s" for depth.hash)
     * @param versionPrefix prefix prepended to the formatted version
     * @param abbrevLength  length of the abbreviated hash
     * @param allowDirty    if false, throws when there are uncommitted changes
     * @return version string and full hash
     * @throws IOException          if a git subprocess cannot be started or its output cannot be read
     * @throws InterruptedException if the thread is interrupted while waiting for git to finish
     */
    public static VersionResult compute(
            Path gitRoot,
            String versionFormat,
            String versionPrefix,
            int abbrevLength,
            boolean allowDirty)
            throws IOException, InterruptedException {
        if (!Files.isDirectory(gitRoot.resolve(".git"))) {
            throw new RuntimeException("Not a Git repository: " + gitRoot);
        }

        if (!allowDirty) {
            List<String> status = runGit(gitRoot, "status", "--porcelain");
            if (!status.isEmpty()) {
                throw new RuntimeException(
                        "Repository has uncommitted changes. Commit or stash them, or set allowDirty = true.");
            }
        }

        String depthStr =
                runGit(gitRoot, "rev-list", "--count", "HEAD").stream().findFirst().orElse("0");
        long depth = Long.parseLong(depthStr.trim());
        String abbrev = runGit(gitRoot, "rev-parse", "--short=" + abbrevLength, "HEAD")
                .stream()
                .findFirst()
                .orElse("");
        String fullHash = runGit(gitRoot, "rev-parse", "HEAD").stream().findFirst().orElse("");

        String versionString =
                versionPrefix + String.format(versionFormat, depth, abbrev.trim());
        return new VersionResult(versionString, fullHash.trim());
    }

    static List<String> runGit(Path workDir, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        pb.command(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git " + String.join(" ", args) + " timed out");
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " failed: " + output);
        }
        return List.of(output.split("\n"));
    }
}
