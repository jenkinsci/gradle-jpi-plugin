package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Action to configure the prepareRun task.
 */
class ConfigurePrepareRunAction implements Action<Sync> {
    private final TaskProvider<GenerateHplTask> hplTaskProvider;
    private final String projectRoot;
    private final Configuration defaultRuntime;
    private final Provider<String> targetExtension;

    ConfigurePrepareRunAction(TaskProvider<GenerateHplTask> hplTaskProvider, String projectRoot, Configuration defaultRuntime,
                              Provider<String> targetExtension) {
        this.hplTaskProvider = hplTaskProvider;
        this.projectRoot = projectRoot;
        this.defaultRuntime = defaultRuntime;
        this.targetExtension = targetExtension;
    }

    @Override
    public void execute(@NotNull Sync sync) {
        sync.into(projectRoot + "/work/plugins");
        sync.from(hplTaskProvider);

        defaultRuntime.getResolvedConfiguration().getResolvedArtifacts()
                .stream()
                .filter(artifact -> HpiMetadataRule.PLUGIN_PACKAGINGS.contains(artifact.getExtension()))
                .sorted(Comparator.comparing(ResolvedArtifact::getName))
                .forEach(artifact ->
                        sync.from(artifact.getFile())
                                .rename(new DropVersionTransformer(
                                        artifact.getModuleVersion().getId().getName(),
                                        artifact.getModuleVersion().getId().getVersion(),
                                        targetExtension.get()
                                ))
                );
    }
}
