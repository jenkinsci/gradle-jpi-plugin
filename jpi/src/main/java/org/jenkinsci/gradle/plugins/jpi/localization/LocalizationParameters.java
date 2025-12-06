package org.jenkinsci.gradle.plugins.jpi.localization;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

import java.io.File;

public interface LocalizationParameters extends WorkParameters {
    RegularFileProperty getSourceFile();

    Property<File> getOutputDir();

    Property<String> getRelativePath();
}
