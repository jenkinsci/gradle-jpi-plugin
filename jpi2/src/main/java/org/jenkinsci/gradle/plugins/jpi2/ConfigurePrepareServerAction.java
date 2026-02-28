package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Action to configure the prepareServer task.
 */
class ConfigurePrepareServerAction implements Action<Sync> {
    private final TaskProvider<?> jpiTaskProvider;
    private final String projectRoot;
    private final Configuration defaultRuntime;
    private final Configuration runtimeClasspath;
    private final Project project;

    public ConfigurePrepareServerAction(TaskProvider<?> jpiTaskProvider, String projectRoot, Configuration defaultRuntime, Configuration runtimeClasspath, Project project) {
        this.jpiTaskProvider = jpiTaskProvider;
        this.projectRoot = projectRoot;
        this.defaultRuntime = defaultRuntime;
        this.runtimeClasspath = runtimeClasspath;
        this.project = project;
    }

    @Override
    public void execute(@NotNull Sync sync) {
        var jpi = jpiTaskProvider.get();
        var extension = project.getExtensions().getByType(JenkinsPluginExtension.class);
        var targetExtension = extension.getArchiveExtension().get();

        sync.into(projectRoot + "/work/plugins");

        sync.from(jpi)
                .rename(new DropVersionTransformer(
                        project.getName(),
                        project.getVersion().toString(),
                        targetExtension
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
                                        targetExtension
                                ))
                );

        runtimeClasspath.getResolvedConfiguration().getResolvedArtifacts()
                .stream()
                .filter(it -> it.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier)
                .sorted(Comparator.comparing(ResolvedArtifact::getName))
                .forEach(it -> {
                    ComponentIdentifier componentIdentifier = it.getId().getComponentIdentifier();
                    if (componentIdentifier instanceof ProjectComponentIdentifier p) {
                        var dependencyProject = project.getRootProject().getAllprojects().stream()
                                .filter(c -> c.getPath().equals(p.getProjectPath()))
                                .findFirst();

                        if (dependencyProject.isPresent()) {
                            var jpiTask = dependencyProject.get().getTasks().findByName("jpi");
                            if (jpiTask != null) {
                                sync.from(jpiTask)
                                        .rename(new DropVersionTransformer(
                                                it.getModuleVersion().getId().getName(),
                                                it.getModuleVersion().getId().getVersion(),
                                                targetExtension
                                        ));
                            }
                        }
                    }
                });
    }

}
