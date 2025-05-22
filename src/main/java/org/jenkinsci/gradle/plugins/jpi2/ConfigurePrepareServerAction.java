package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
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

        sync.from(jpi.getOutputs().getFiles())
                .into(projectRoot + "/work/plugins");

        defaultRuntime.getResolvedConfiguration().getResolvedArtifacts()
                .stream()
                .filter(artifact -> HpiMetadataRule.PLUGIN_PACKAGINGS.contains(artifact.getExtension()))
                .sorted(Comparator.comparing(ResolvedArtifact::getName))
                .forEach(artifact ->
                        sync.from(artifact.getFile()).into(projectRoot + "/work/plugins").rename(new Transformer<>() {
                            @NotNull
                            @Override
                            public String transform(@NotNull String s) {
                                return s.replace("-" + artifact.getModuleVersion().getId().getVersion(), "") // remove version from filename
                                        .replace(".hpi", ".jpi") // change extension to jpi to prevent warnings
                                        ;
                            }
                        }));

        runtimeClasspath.getResolvedConfiguration().getResolvedArtifacts()
                .stream()
                .filter(it -> it.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier)
                .sorted(Comparator.comparing(ResolvedArtifact::getName))
                .forEach(it -> {
                    ComponentIdentifier componentIdentifier = it.getId().getComponentIdentifier();
                    if (componentIdentifier instanceof ProjectComponentIdentifier p) {
                        System.err.println("Dependency project: " + p.getProjectPath());
                        var dependencyProject = project.getRootProject().getAllprojects().stream()
                                .filter(c -> c.getPath().equals(p.getProjectPath()))
                                .findFirst();

                        assert dependencyProject.isPresent();

                        var jpiTask = dependencyProject.get().getTasks().findByName("jpi");
                        if (jpiTask != null) {
                            sync.from(jpiTask.getOutputs().getFiles())
                                    .into(projectRoot + "/work/plugins");
                        }
                    }
                });
    }
}
