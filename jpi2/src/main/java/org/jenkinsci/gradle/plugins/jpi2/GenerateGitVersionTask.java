package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generates a version string from the Git repository (commit depth + abbreviated hash)
 * and writes it to a file (first line = version, second line = full hash).
 * Compatible with the JPI plugin's {@code generateGitVersion} output format.
 */
public abstract class GenerateGitVersionTask extends DefaultTask {

    public static final String TASK_NAME = "generateGitVersion";

    public GenerateGitVersionTask() {
        getOutputs().doNotCacheIf("Caching would require .git to be an input", t -> true);
        getOutputs().upToDateWhen(t -> false);
    }

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Internal
    public abstract DirectoryProperty getGitRoot();

    @Input
    public abstract Property<String> getVersionFormat();

    @Input
    public abstract Property<String> getVersionPrefix();

    @Input
    public abstract Property<Integer> getAbbrevLength();

    @Input
    public abstract Property<Boolean> getAllowDirty();

    @TaskAction
    public void generate() throws IOException, InterruptedException {
        Path gitRoot = getGitRoot().get().getAsFile().toPath();
        if (!Files.isDirectory(gitRoot.resolve(".git"))) {
            throw new RuntimeException("Not a Git repository: " + gitRoot);
        }

        if (!getAllowDirty().get()) {
            List<String> status = runGit(gitRoot, "status", "--porcelain");
            if (!status.isEmpty()) {
                throw new RuntimeException("Repository has uncommitted changes. Commit or stash them, or set allowDirty = true.");
            }
        }

        String depthStr = runGit(gitRoot, "rev-list", "--count", "HEAD").stream().findFirst().orElse("0");
        long depth = Long.parseLong(depthStr.trim());
        String abbrev = runGit(gitRoot, "rev-parse", "--short=" + getAbbrevLength().get(), "HEAD").stream().findFirst().orElse("");
        String fullHash = runGit(gitRoot, "rev-parse", "HEAD").stream().findFirst().orElse("");

        String versionString = getVersionPrefix().get() + String.format(getVersionFormat().get(), depth, abbrev.trim());
        String content = versionString + "\n" + fullHash.trim() + "\n";

        Path outputPath = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
    }

    private static List<String> runGit(Path workDir, String... args) throws IOException, InterruptedException {
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
