package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.stream.Collectors.joining;

public abstract class CheckOverlappingSourcesTask extends DefaultTask {
    public static final String NAME = "checkOverlappingSources";

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClassesDirs();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void validate() {
        var discovered = new ArrayList<File>();
        Set<String> existingSezpozFiles = new HashSet<>();
        for (File classDir : getClassesDirs().getFiles()) {
            File annotationsDir = new File(classDir, "META-INF/annotations");
            String[] files = annotationsDir.list();
            if (files == null) {
                continue;
            }
            for (String fileName : files) {
                File path = new File(annotationsDir, fileName);
                discovered.add(path);
                if (!path.isFile()) {
                    continue;
                }
                if (!existingSezpozFiles.add(fileName)) {
                    throw new GradleException("Found overlapping Sezpoz file: " + fileName + ". Use joint compilation!");
                }
            }
        }

        var pluginImpls = new ArrayList<File>();
        for (File classDir : getClassesDirs().getFiles()) {
            File plugin = new File(classDir, "META-INF/services/hudson.Plugin");
            if (plugin.exists()) {
                pluginImpls.add(plugin);
            }
        }

        if (pluginImpls.size() > 1) {
            String implementations = pluginImpls.stream()
                    .map(File::getPath)
                    .collect(joining(", "));
            throw new GradleException(
                    "Found multiple directories containing Jenkins plugin implementations ('"
                            + implementations
                            + "'). Use joint compilation to work around this problem."
            );
        }
        discovered.addAll(pluginImpls);

        Path destination = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(destination.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(destination, UTF_8, CREATE, TRUNCATE_EXISTING)) {
                for (File file : discovered) {
                    writer.append(file.getAbsolutePath()).append('\n');
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to write to " + destination, e);
        }
    }
}
