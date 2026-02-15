package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class ConfigureSynthesizeTestServerSourceAction implements Action<Task> {
    private static final String SYNTHESIZED_TEST_SOURCE_RESOURCE =
            "org/jenkinsci/gradle/plugins/jpi2/SynthesizedTestServerTest.template";
    private final File sourceFile;

    ConfigureSynthesizeTestServerSourceAction(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public void execute(@NotNull Task task) {
        task.setGroup("verification");
        task.setDescription("Generate the synthesized JUnit 5 test used by testServer");
        task.getOutputs().file(sourceFile);
        task.doLast(new Action<>() {
            @Override
            public void execute(@NotNull Task ignored) {
                writeSource();
            }
        });
    }

    private void writeSource() {
        try {
            var parentDir = sourceFile.getParentFile();
            if (parentDir != null) {
                Files.createDirectories(parentDir.toPath());
            }
            Files.writeString(sourceFile.toPath(), synthesizedTestSource(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Unable to synthesize testServer JUnit source", e);
        }
    }

    private static String synthesizedTestSource() {
        try (var sourceStream = ConfigureSynthesizeTestServerSourceAction.class
                .getClassLoader()
                .getResourceAsStream(SYNTHESIZED_TEST_SOURCE_RESOURCE)) {
            if (sourceStream == null) {
                throw new GradleException("Unable to find synthesized test source template: "
                        + SYNTHESIZED_TEST_SOURCE_RESOURCE);
            }
            return new String(sourceStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Unable to read synthesized test source template: "
                    + SYNTHESIZED_TEST_SOURCE_RESOURCE, e);
        }
    }
}
