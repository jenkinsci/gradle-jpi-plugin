package org.jenkinsci.gradle.plugins.jpi.version;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jenkinsci.gradle.plugins.jpi.GitVersionExtension;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gradle task that generates a version string based on Git repository information.
 * <p>
 * This task uses Git commands to determine the current version based on tags,
 * commits, and other repository information. The generated version is written
 * to an output file.
 */
public abstract class GenerateGitVersionTask extends DefaultTask {

    /** The name of this task as registered in the Gradle build. */
    public static final String TASK_NAME = "generateGitVersion";

    private final GitVersionExtension gitVersionExtension;

    /**
     * Gets the classpath used for executing the Git version generation.
     *
     * @return A collection of files representing the classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Gets the output file where the generated version will be written.
     *
     * @return A property containing the output file location
     */
    @OutputFile
    public RegularFileProperty getOutputFile() {
        return gitVersionExtension.getOutputFile();
    }

    /**
     * Gets the worker executor used for running the version generation in isolation.
     *
     * @return The worker executor
     */
    @Inject
    abstract public WorkerExecutor getWorkerExecutor();

    /**
     * Gets the project layout for accessing project directories.
     *
     * @return The project layout
     */
    @Inject
    abstract public ProjectLayout getProjectLayout();

    /**
     * Constructs a new task with the given Git version extension.
     *
     * @param gitVersionExtension The extension containing Git version configuration
     */
    @Inject
    public GenerateGitVersionTask(GitVersionExtension gitVersionExtension) {
        this.gitVersionExtension = gitVersionExtension;
        getOutputs().doNotCacheIf("Caching would require `.git` to be an input", t -> true);
        getOutputs().upToDateWhen(t -> false);
    }

    /**
     * Executes the task to generate the Git version.
     * <p>
     * This method submits a worker action to generate the version in a separate
     * classloader to isolate the Git operations.
     */
    @TaskAction
    public void generate() {
        WorkQueue queue = getWorkerExecutor().classLoaderIsolation(classLoaderWorkerSpec -> {
            classLoaderWorkerSpec.getClasspath().from(getClasspath());
        });
        queue.submit(GenerateGitVersion.class, p -> {
            p.getGitRoot().set(gitVersionExtension.getGitRoot());
            p.getAbbrevLength().set(gitVersionExtension.getAbbrevLength());
            p.getVersionPrefix().set(gitVersionExtension.getVersionPrefix());
            p.getVersionFormat().set(gitVersionExtension.getVersionFormat());
            p.getSanitize().set(gitVersionExtension.getSanitize());
            p.getAllowDirty().set(gitVersionExtension.getAllowDirty());
            p.getOutputFile().set(getOutputFile());
        });
    }

    /**
     * Parameters for the Git version generation worker action.
     * <p>
     * This interface defines the inputs needed for generating a Git version.
     */
    public interface GenerateGitVersionParameters extends WorkParameters {

        /**
         * Gets the root directory of the Git repository.
         *
         * @return A property containing the Git root directory
         */
        DirectoryProperty getGitRoot();

        /**
         * Gets the prefix to use for the version string.
         *
         * @return A property containing the version prefix
         */
        Property<String> getVersionPrefix();

        /**
         * Gets the format string for the version.
         *
         * @return A property containing the version format
         */
        Property<String> getVersionFormat();

        /**
         * Gets whether to sanitize the version string.
         *
         * @return A property indicating whether to sanitize the version
         */
        Property<Boolean> getSanitize();

        /**
         * Gets whether to allow dirty working directory.
         *
         * @return A property indicating whether to allow dirty working directory
         */
        Property<Boolean> getAllowDirty();

        /**
         * Gets the abbreviation length for commit hashes.
         *
         * @return A property containing the abbreviation length
         */
        Property<Integer> getAbbrevLength();

        /**
         * Gets the output file where the generated version will be written.
         *
         * @return A property containing the output file location
         */
        RegularFileProperty getOutputFile();
    }

    /**
     * Worker action that generates the Git version.
     * <p>
     * This action runs in a separate classloader and generates a version string
     * based on the Git repository information.
     */
    public abstract static class GenerateGitVersion implements WorkAction<GenerateGitVersionParameters> {
        @Override
        public void execute() {
            GenerateGitVersionParameters p = getParameters();
            Path outputFile = p.getOutputFile().get().getAsFile().toPath();
            try {
                GitVersionGenerator.GitVersion version = new GitVersionGenerator(
                        p.getGitRoot().get().getAsFile().toPath(),
                        p.getAbbrevLength().get(),
                        p.getVersionPrefix().get(),
                        p.getVersionFormat().get(),
                        p.getAllowDirty().get(),
                        p.getSanitize().get()).generate();
                Files.write(outputFile, version.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException("Fail to write version file at " + outputFile, e);
            }
        }
    }

}
