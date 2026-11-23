package org.jenkinsci.gradle.plugins.jpi2.localization;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.workers.WorkAction;
import org.jvnet.localizer.ClassGenerator;
import org.jvnet.localizer.Generator;
import org.jvnet.localizer.GeneratorConfig;
import org.jvnet.localizer.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Work action that runs the localizer generator.
 */
public abstract class RunGenerator implements WorkAction<LocalizationParameters> {

    @Override
    public void execute() {
        File file = getParameters().getSourceFile().get().getAsFile();
        File outputDir = getParameters().getOutputDir().get().getAsFile();
        String relativePath = getParameters().getRelativePath().get();
        GeneratorConfig config = GeneratorConfig.of(outputDir, null, new InfoReporter(), null, false);
        ClassGenerator generator = new ClosingGenerator(config);

        try {
            generator.generate(file, relativePath);
            generator.build();
        } catch (IOException e) {
            throw new GradleException("Failed to generate Java source file from " + file.getAbsolutePath(), e);
        }
    }

    // Localizer 1.31 closes the input stream only on failure. Owning the stream here
    // lets Windows builds delete or rename message bundles after generation.
    private static final class ClosingGenerator extends Generator {
        private ClosingGenerator(GeneratorConfig config) {
            super(config);
        }

        @Override
        public void generate(File source, String relativePath) throws IOException {
            var properties = new Properties();
            try (var input = Files.newInputStream(source.toPath())) {
                properties.load(input);
            }
            properties.stringPropertyNames().forEach(this::assertKeyPatternMatched);
            generateImpl(toClassName(relativePath), properties);
        }
    }

    private static class InfoReporter implements Reporter {
        private static final Logger LOGGER = LoggerFactory.getLogger(InfoReporter.class);

        @Override
        public void debug(String msg) {
            LOGGER.debug(msg);
        }
    }
}
