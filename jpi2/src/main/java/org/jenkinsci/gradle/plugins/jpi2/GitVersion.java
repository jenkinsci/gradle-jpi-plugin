package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    public record VersionResult(String version, String fullHash) implements Serializable {}

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
            Path gitRoot, String versionFormat, String versionPrefix, int abbrevLength, boolean allowDirty)
            throws IOException, InterruptedException {
        try {
            runGit(gitRoot, "rev-parse", "--git-dir");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a Git repository: " + gitRoot, e);
        }

        if (!allowDirty) {
            List<String> status = runGit(gitRoot, "status", "--porcelain");
            if (!status.isEmpty()) {
                throw new RuntimeException(
                        "Repository has uncommitted changes. Commit or stash them, or set allowDirty = true.");
            }
        }

        String depthStr = runGit(gitRoot, "rev-list", "--count", "HEAD").stream()
                .findFirst()
                .orElse("0");
        long depth = Long.parseLong(depthStr.trim());
        String abbrev = runGit(gitRoot, "rev-parse", "--short=" + abbrevLength, "HEAD").stream()
                .findFirst()
                .orElse("");
        String fullHash =
                runGit(gitRoot, "rev-parse", "HEAD").stream().findFirst().orElse("");

        String versionString = versionPrefix + String.format(versionFormat, depth, abbrev.trim());
        return new VersionResult(versionString, fullHash.trim());
    }

    static List<String> runGit(Path workDir, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        pb.command(command);
        pb.directory(workDir.toFile());
        return runCommand(pb, Duration.ofSeconds(30));
    }

    static List<String> runCommand(ProcessBuilder builder, Duration timeout) throws IOException, InterruptedException {
        var outputFile = Files.createTempFile("jpi-git-", ".log");
        Process process = null;
        try {
            // A file avoids blocking on an open stdout pipe before the timeout can be enforced.
            process = builder.redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(String.join(" ", builder.command()) + " timed out");
            }
            var output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(String.join(" ", builder.command()) + " failed: " + output);
            }
            return output.lines().toList();
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException e) {
                // Windows can retain inherited file handles briefly after termination; preserve the command failure.
                outputFile.toFile().deleteOnExit();
            }
        }
    }
}
