package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

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

    public ConfigurePrepareServerAction(TaskProvider<?> jpiTaskProvider, Provider<String> workDir, Configuration defaultRuntime,
                                       Provider<String> projectName, Provider<String> projectVersion,
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
        var jpi = jpiTaskProvider.get();
        sync.into(workDir.map(it -> it + "/plugins"));

        sync.from(jpi)
                .rename(new DropVersionTransformer(
                        projectName.get(),
                        projectVersion.get(),
                        targetExtension.get()
                ));

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
