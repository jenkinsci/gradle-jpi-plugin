package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the prepareRun task.
 */
class ConfigurePrepareRunAction implements Action<Sync> {
    private static final String JPI_EXTENSION = "jpi";

    private final TaskProvider<GenerateHplTask> hplTaskProvider;
    private final Provider<String> workDir;
    private final Configuration defaultRuntime;

    ConfigurePrepareRunAction(
            TaskProvider<GenerateHplTask> hplTaskProvider, Provider<String> workDir, Configuration defaultRuntime) {
        this.hplTaskProvider = hplTaskProvider;
        this.workDir = workDir;
        this.defaultRuntime = defaultRuntime;
    }

    @Override
    public void execute(@NotNull Sync sync) {
        sync.into(workDir.map(it -> it + "/plugins"));
        sync.from(hplTaskProvider);

        ServerPluginDependencies.configure(
                sync, defaultRuntime, sync.getProject().provider(() -> JPI_EXTENSION));
    }
}
