package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Transformer;
import org.jetbrains.annotations.NotNull;

/**
 * Renames plugin files by stripping the version from the filename and normalising the extension to {@code .jpi}.
 */
public class DropVersionTransformer implements Transformer<String, String> {
    private final String name;
    private final String version;

    public DropVersionTransformer(String name, String version) {
        this.name = name;
        this.version = version;
    }

    @NotNull
    @Override
    public String transform(@NotNull String s) {
        return s.replace(name + "-" + version, name)
                .replace(".hpi", ".jpi");
    }
}
