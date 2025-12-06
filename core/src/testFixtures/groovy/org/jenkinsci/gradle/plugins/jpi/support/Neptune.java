package org.jenkinsci.gradle.plugins.jpi.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Neptune {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neptune.class);
    private final ProjectFile root;
    private final List<ProjectFile> subprojects;
    private final SettingsFile settings;
    private final Indenter indenter;

    public Neptune(ProjectFile root, List<ProjectFile> subprojects, SettingsFile settings, Indenter indenter) {
        this.root = root;
        this.subprojects = subprojects;
        this.settings = settings;
        this.indenter = indenter;
    }

    public void writeTo(File directory) {
        writeTo(directory.toPath());
    }

    public void writeTo(Path directory) {
        write(directory.resolve("build.gradle"), root.emit(indenter));
        write(directory.resolve("settings.gradle"), settings.emit(indenter));
        for (ProjectFile sub : subprojects) {
            Path subFile = directory.resolve(sub.getName()).resolve("build.gradle");
            write(subFile, sub.emit(indenter));
        }
    }

    private static void write(Path path, String contents) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Failed to write to " + path, e);
        }
    }

    public static Neptune.Builder newBuilder() {
        return new Neptune.Builder();
    }

    public static Neptune.Builder newBuilder(ProjectFile root) {
        return new Neptune.Builder()
                .withRootProject(root);
    }

    public static class Builder {
        private ProjectFile root;
        private final List<ProjectFile> subprojects = new LinkedList<>();
        private final Indenter indenter = FourSpaceIndenter.create();

        private Builder() {
        }

        public Builder withRootProject(ProjectFile root) {
            this.root = root;
            return this;
        }

        public Builder addSubproject(ProjectFile sub) {
            subprojects.add(sub);
            return this;
        }

        public Neptune build() {
            SettingsFile settings = SettingsFile.builder()
                    .withRootProjectName(root.getName())
                    .addSubprojects(subprojects.stream()
                            .map(ProjectFile::getName)
                            .collect(Collectors.toSet()))
                    .build();
            return new Neptune(root, subprojects, settings, indenter);
        }
    }
}
