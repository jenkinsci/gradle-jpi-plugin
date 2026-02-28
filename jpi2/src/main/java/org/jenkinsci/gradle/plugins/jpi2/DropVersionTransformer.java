package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Transformer;
import org.jetbrains.annotations.NotNull;

/**
 * Renames plugin files by stripping the version from the filename and normalising the extension to the target (e.g. {@code .jpi} or {@code .hpi}).
 */
public class DropVersionTransformer implements Transformer<String, String> {
    private final String name;
    private final String version;
    private final String targetExtension;

    public DropVersionTransformer(String name, String version, String targetExtension) {
        this.name = name;
        this.version = version;
        this.targetExtension = targetExtension;
    }

    @NotNull
    @Override
    public String transform(@NotNull String s) {
        return s.replace(name + "-" + version, name)
                .replace(".hpi", "." + targetExtension)
                .replace(".jpi", "." + targetExtension);
    }
}
