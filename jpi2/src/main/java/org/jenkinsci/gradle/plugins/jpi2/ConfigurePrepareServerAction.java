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
    private final Provider<String> projectName;
    private final Provider<String> projectVersion;
    private final Provider<String> targetExtension;

    /** Creates an action that stages the plugin and its dependencies for a Jenkins server. */
    public ConfigurePrepareServerAction(
            TaskProvider<?> jpiTaskProvider,
            Provider<String> workDir,
            Configuration defaultRuntime,
            Provider<String> projectName,
            Provider<String> projectVersion,
            Provider<String> targetExtension) {
        this.jpiTaskProvider = jpiTaskProvider;
        this.workDir = workDir;
        this.defaultRuntime = defaultRuntime;
        this.projectName = projectName;
        this.projectVersion = projectVersion;
        this.targetExtension = targetExtension;
    }

    @Override
    public void execute(@NotNull Sync sync) {
        sync.into(workDir.map(it -> it + "/plugins"));

        sync.from(
                jpiTaskProvider,
                spec -> spec.rename(
                        new DropVersionTransformer(projectName.get(), projectVersion.get(), targetExtension.get())));

        ServerPluginDependencies.configure(sync, defaultRuntime, targetExtension);
    }
}
