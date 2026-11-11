package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/** Queries Git once for a provider, with Gradle revalidating the value across cached configurations. */
public abstract class GitVersionValueSource
        implements ValueSource<GitVersion.VersionResult, GitVersionValueSource.Parameters> {
    /** Inputs determining the Git-derived version. */
    public interface Parameters extends ValueSourceParameters {
        /** @return repository directory */
        DirectoryProperty getGitRoot();
        /** @return version format */
        Property<String> getVersionFormat();
        /** @return version prefix */
        Property<String> getVersionPrefix();
        /** @return abbreviated hash length */
        Property<Integer> getAbbrevLength();
        /** @return whether uncommitted changes are allowed */
        Property<Boolean> getAllowDirty();
    }

    @Override
    public GitVersion.VersionResult obtain() {
        var parameters = getParameters();
        try {
            return GitVersion.compute(
                    parameters.getGitRoot().get().getAsFile().toPath(),
                    parameters.getVersionFormat().get(),
                    parameters.getVersionPrefix().get(),
                    parameters.getAbbrevLength().get(),
                    parameters.getAllowDirty().get());
        } catch (IOException e) {
            throw new GradleException("Unable to compute Git version", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Git version computation interrupted", e);
        }
    }
}
