package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.gradle.workers.WorkAction;
import org.kohsuke.accmod.impl.Checker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

public abstract class CheckAccess implements WorkAction<CheckAccessParameters> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckAccess.class);

    @Inject
    public CheckAccess() {
    }

    @Override
    public void execute() {
        List<URL> toScan = getParameters().getClasspathToScan().getFiles().stream()
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not convert file to URL: " + file, e);
                    }
                })
                .toList();
        URL[] urls = toScan.toArray(new URL[0]);

        URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader());
        InternalErrorListener listener = new InternalErrorListener();
        Properties props = new Properties();
        getParameters().getPropertiesForAccessModifier().get().forEach(props::put);

        try {
            Checker checker = new Checker(loader, listener, props, new MavenLoggingBridge());
            checker.check(getParameters().getDirToCheck().get().getAsFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run restricted API checks", e);
        }

        try {
            var outputFile = getParameters().getOutputFile().get().getAsFile().toPath();
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, listener.errorMessage(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write access modifier report", e);
        }

        if (listener.hasErrors()) {
            if (getParameters().getIgnoreFailures().get()) {
                LOGGER.warn(listener.errorMessage());
            } else {
                LOGGER.error(listener.errorMessage());
                throw new RestrictedApiException();
            }
        }
    }
}
