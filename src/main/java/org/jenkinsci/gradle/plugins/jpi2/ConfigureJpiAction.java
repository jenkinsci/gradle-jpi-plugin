package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jenkinsci.gradle.plugins.jpi2.ArtifactType.ARTIFACT_TYPE_ATTRIBUTE;

/**
 * Action to configure the JPI task for a Jenkins plugin.
 */
@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class ConfigureJpiAction implements Action<War> {
    private final Project project;
    private final Configuration configuration;
    private final Configuration jenkinsCore;
    private final String jenkinsVersion;

    public ConfigureJpiAction(Project project, Configuration configuration, Configuration jenkinsCore, String jenkinsVersion) {
        this.project = project;
        this.configuration = configuration;
        this.jenkinsCore = jenkinsCore;
        this.jenkinsVersion = jenkinsVersion;
    }

    @Override
    public void execute(@NotNull War jpi) {
        jpi.getArchiveExtension().set("jpi");
        jpi.manifest(new ManifestAction(project, configuration, jenkinsVersion));
        jpi.from(project.getTasks().named("jar"), new Action<>() {
            @Override
            public void execute(@NotNull CopySpec copySpec) {
                copySpec.into("WEB-INF/lib");
            }
        });
        jpi.from(project.file("src/main/webapp"), new Action<>() {
            @Override
            public void execute(@NotNull CopySpec copySpec) {
                copySpec.into("");
            }
        });

        var directJarDependencies = getDirectJarDependencies();
        var detachedConfiguration = project.getConfigurations().detachedConfiguration(directJarDependencies.toArray(new Dependency[0]));
        detachedConfiguration.getAttributes().attribute(ARTIFACT_TYPE_ATTRIBUTE, project.getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR));
        detachedConfiguration.shouldResolveConsistentlyWith(configuration);

        jpi.setClasspath(detachedConfiguration);
        jpi.finalizedBy(V2JpiPlugin.EXPLODED_JPI_TASK);
    }

    @NotNull
    private List<Dependency> getDirectJarDependencies() {

        var requestedDependencies = configuration.getAllDependencies();
        var resolvedDependencies = configuration.getResolvedConfiguration().getFirstLevelModuleDependencies();
        var jenkinsCoreModules = getAllJenkinsCoreDependencies();
        var jpiPluginTransitives = getAllJpiPluginTransitiveDependencies();

        return resolvedDependencies.stream()
                .filter(dependency -> isDependencyUnseen(dependency, jenkinsCoreModules))
                .filter(dependency -> isDependencyUnseen(dependency, jpiPluginTransitives))
                .flatMap(dependency -> dependency.getModuleArtifacts().stream()
                        .filter(artifact -> "jar".equals(artifact.getExtension()))
                        .flatMap(artifact -> requestedDependencies.stream()
                                .filter(reqDep -> matches(dependency, reqDep))
                                .findFirst()
                                .stream()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @NotNull
    private java.util.Set<ResolvedDependency> getAllJenkinsCoreDependencies() {
        var allDependencies = new java.util.HashSet<ResolvedDependency>();
        var firstLevelDependencies = jenkinsCore.getResolvedConfiguration().getFirstLevelModuleDependencies();

        for (var dependency : firstLevelDependencies) {
            collectAllDependencies(dependency, allDependencies);
        }

        return allDependencies;
    }

    private void collectAllDependencies(ResolvedDependency dependency, java.util.Set<ResolvedDependency> collector) {
        if (collector.add(dependency)) {
            for (var child : dependency.getChildren()) {
                collectAllDependencies(child, collector);
            }
        }
    }

    @NotNull
    private java.util.Set<ResolvedDependency> getAllJpiPluginTransitiveDependencies() {
        var allTransitives = new java.util.HashSet<ResolvedDependency>();
        var resolvedDependencies = configuration.getResolvedConfiguration().getFirstLevelModuleDependencies();
        
        for (var dependency : resolvedDependencies) {
            if (isJpiPluginDependency(dependency)) {
                for (var child : dependency.getChildren()) {
                    collectAllDependencies(child, allTransitives);
                }
            }
        }
        
        return allTransitives;
    }
    
    private boolean isJpiPluginDependency(ResolvedDependency dependency) {
        return dependency.getModuleArtifacts().stream()
                .anyMatch(artifact -> "jpi".equals(artifact.getExtension()) || "hpi".equals(artifact.getExtension()));
    }
    
    private boolean isDependencyUnseen(ResolvedDependency dependency, java.util.Set<ResolvedDependency> dependencySet) {
        return dependencySet.stream()
                .noneMatch(setMember ->
                        Objects.equals(setMember.getModuleGroup(), dependency.getModuleGroup()) &&
                                Objects.equals(setMember.getModuleName(), dependency.getModuleName()));
    }

    private boolean matches(ResolvedDependency dependency, Dependency reqDep) {
        if (reqDep instanceof ProjectDependency projectDependency) {
            var projectPath = projectDependency.getDependencyProject().getPath();
            var dependencyProject = project.getRootProject().getChildProjects().values().stream()
                    .filter(it -> it.getPath().equals(projectPath)).findFirst();

            assert dependencyProject.isPresent();

            if (dependencyProject.get().getTasks().findByName("jpi") == null) {
                return Objects.equals(reqDep.getGroup(), dependency.getModuleGroup()) &&
                        reqDep.getName().equals(dependency.getModuleName());
            }
            return false;
        } else if (reqDep instanceof ModuleDependency moduleDependency) {
            return Objects.equals(moduleDependency.getGroup(), dependency.getModuleGroup()) &&
                    Objects.equals(moduleDependency.getName(), dependency.getModuleName());
        } else {
            return false;
        }
    }
}
