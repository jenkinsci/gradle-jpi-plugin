package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/** Writes the Git-derived version and full commit hash for external tools. */
@UntrackedTask(because = "Git state, including worktree and dirty-file state, must be queried on each invocation")
public abstract class GenerateGitVersionTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String NAME = "generateGitVersion";

    /** @return repository directory whose current Git state is queried */
    @Internal
    public abstract DirectoryProperty getGitRoot();

    /** @return format receiving commit depth and abbreviated hash */
    @Input
    public abstract Property<String> getVersionFormat();

    /** @return prefix prepended to the formatted version */
    @Input
    public abstract Property<String> getVersionPrefix();

    /** @return minimum abbreviated-hash length */
    @Input
    public abstract Property<Integer> getAbbrevLength();

    /** @return whether uncommitted changes are permitted */
    @Input
    public abstract Property<Boolean> getAllowDirty();

    /** @return file containing the version on the first line and full hash on the second */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** Writes a fresh version without relying on Gradle's file snapshots of Git internals. */
    @TaskAction
    public void generate() {
        try {
            var result = GitVersion.compute(
                    getGitRoot().get().getAsFile().toPath(),
                    getVersionFormat().get(),
                    getVersionPrefix().get(),
                    getAbbrevLength().get(),
                    getAllowDirty().get());
            var output = getOutputFile().get().getAsFile().toPath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, result.version() + "\n" + result.fullHash() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Unable to generate Git version", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Git version generation interrupted", e);
        }
    }
}
