package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;

/** Maps a produced archive's actual filename to the plugin ID Jenkins installs. */
final class PluginArchiveRenamer implements Transformer<String, String> {
    private final Provider<String> archiveFileName;
    private final Provider<String> pluginId;
    private final Provider<String> targetExtension;

    PluginArchiveRenamer(
            Provider<String> archiveFileName, Provider<String> pluginId, Provider<String> targetExtension) {
        this.archiveFileName = archiveFileName;
        this.pluginId = pluginId;
        this.targetExtension = targetExtension;
    }

    @NotNull
    @Override
    public String transform(@NotNull String fileName) {
        return fileName.equals(archiveFileName.get()) ? pluginId.get() + "." + targetExtension.get() : fileName;
    }
}
