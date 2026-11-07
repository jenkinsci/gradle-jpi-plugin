package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the prepareServer task.
 */
class ConfigurePrepareServerAction implements Action<Sync> {
    private final TaskProvider<?> jpiTaskProvider;
    private final Provider<String> workDir;
    private final Configuration defaultRuntime;
    private final Provider<String> archiveFileName;
    private final Provider<String> pluginId;
    private final Provider<String> targetExtension;

    /** Creates an action that stages the plugin and its dependencies for a Jenkins server. */
    public ConfigurePrepareServerAction(
            TaskProvider<?> jpiTaskProvider,
            Provider<String> workDir,
            Configuration defaultRuntime,
            Provider<String> archiveFileName,
            Provider<String> pluginId,
            Provider<String> targetExtension) {
        this.jpiTaskProvider = jpiTaskProvider;
        this.workDir = workDir;
        this.defaultRuntime = defaultRuntime;
        this.archiveFileName = archiveFileName;
        this.pluginId = pluginId;
        this.targetExtension = targetExtension;
    }

    @Override
    public void execute(@NotNull Sync sync) {
        sync.into(workDir.map(it -> it + "/plugins"));

        sync.from(
                jpiTaskProvider,
                spec -> spec.rename(new PluginArchiveRenamer(archiveFileName, pluginId, targetExtension)));

        ServerPluginDependencies.configure(sync, defaultRuntime, targetExtension);
    }
}
