package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class RuntimeClasspathArtifactsTest {

    @Test
    void bundlesOnlyDirectLibrariesNotProvidedByCoreOrPlugins() {
        var core = resolvedDependency("org.example", "core-library", "jar", "core-library.jar", Set.of());
        var provided = resolvedDependency("org.example", "provided-library", "jar", "provided-library.jar", Set.of());
        var plugin = resolvedDependency("org.example", "other-plugin", "hpi", "other-plugin.hpi", Set.of(provided));
        var bundled = resolvedDependency("org.example", "bundled-library", "jar", "bundled-library.jar", Set.of());
        var coreRequest = requestedDependency("org.example", "core-library");
        var providedRequest = requestedDependency("org.example", "provided-library");
        var bundledRequest = requestedDependency("org.example", "bundled-library");
        var fixture = fixture(
                Set.of(core, plugin, provided, bundled),
                Set.of(core),
                Set.of(coreRequest, providedRequest, bundledRequest));

        fixture.artifacts().getBundledLibraries();

        var dependencies = ArgumentCaptor.<Dependency[]>captor();
        verify(fixture.configurations()).detachedConfiguration(dependencies.capture());
        assertThat(dependencies.getValue()).containsExactly(bundledRequest);
    }

    @Test
    void excludesJarsAlreadyPackagedByPluginDependencies() {
        var provided = resolvedDependency("org.example", "provided-library", "jar", "provided-library.jar", Set.of());
        var plugin = resolvedDependency("org.example", "other-plugin", "jpi", "other-plugin.jpi", Set.of(provided));
        var fixture = fixture(Set.of(plugin), Set.of(), Set.of());

        fixture.artifacts().getBundledLibraries();

        var filter = ArgumentCaptor.<Spec<File>>captor();
        verify(fixture.detached()).filter(filter.capture());
        assertThat(filter.getValue().isSatisfiedBy(new File("provided-library.jar")))
                .isFalse();
        assertThat(filter.getValue().isSatisfiedBy(new File("unrelated-library.jar")))
                .isTrue();
    }

    @Test
    void doesNotRealizeProjectJpiTaskWhenCheckingDependencyType() {
        var rootProject = ProjectBuilder.builder().withName("root").build();
        var pluginProject = ProjectBuilder.builder()
                .withParent(rootProject)
                .withName("upstream-plugin")
                .build();
        var libraryProject = ProjectBuilder.builder()
                .withParent(rootProject)
                .withName("upstream-library")
                .build();
        var project = mock(Project.class);
        when(project.getRootProject()).thenReturn(rootProject);

        var realizedTasks = new ArrayList<String>();
        var jpiTask = pluginProject.getTasks().register("jpi", DefaultTask.class);
        jpiTask.configure(task -> realizedTasks.add(task.getName()));

        var pluginDependency = projectDependency("org.example", "upstream-plugin");
        var libraryDependency = projectDependency("org.example", "upstream-library");
        var fixture = fixture(
                project,
                Set.of(
                        resolvedDependency("org.example", "upstream-plugin", "jar", "upstream-plugin.jar", Set.of()),
                        resolvedDependency("org.example", "upstream-library", "jar", "upstream-library.jar", Set.of())),
                Set.of(),
                Set.of(pluginDependency, libraryDependency),
                Set.of(
                        projectArtifact("org.example", "upstream-plugin", pluginProject.getPath()),
                        projectArtifact("org.example", "upstream-library", libraryProject.getPath())));

        fixture.artifacts().getBundledLibraries();

        assertThat(realizedTasks).isEmpty();
        var dependencies = ArgumentCaptor.<Dependency[]>captor();
        verify(fixture.configurations()).detachedConfiguration(dependencies.capture());
        assertThat(dependencies.getValue()).containsExactly(libraryDependency);
    }

    private static Fixture fixture(
            Set<ResolvedDependency> runtimeDependencies,
            Set<ResolvedDependency> coreDependencies,
            Set<Dependency> requestedDependencies) {
        return fixture(mock(Project.class), runtimeDependencies, coreDependencies, requestedDependencies, Set.of());
    }

    private static Fixture fixture(
            Project project,
            Set<ResolvedDependency> runtimeDependencies,
            Set<ResolvedDependency> coreDependencies,
            Set<Dependency> requestedDependencies,
            Set<ResolvedArtifact> runtimeArtifacts) {
        var configurations = mock(ConfigurationContainer.class);
        var runtime = configuration(runtimeDependencies, runtimeArtifacts);
        var core = configuration(coreDependencies);
        var dependencySet = mock(DependencySet.class);
        var detached = mock(Configuration.class);
        var attributes = mock(AttributeContainer.class);
        when(project.getConfigurations()).thenReturn(configurations);
        when(project.getObjects()).thenReturn(ProjectBuilder.builder().build().getObjects());
        when(runtime.getAllDependencies()).thenReturn(dependencySet);
        when(dependencySet.stream()).thenAnswer(ignored -> requestedDependencies.stream());
        when(configurations.detachedConfiguration(any(Dependency[].class))).thenReturn(detached);
        when(detached.getAttributes()).thenReturn(attributes);
        when(detached.filter(ArgumentMatchers.<Spec<File>>any())).thenReturn(mock(FileCollection.class));
        return new Fixture(new RuntimeClasspathArtifacts(project, runtime, core), configurations, detached);
    }

    private static Configuration configuration(Set<ResolvedDependency> dependencies) {
        return configuration(dependencies, Set.of());
    }

    private static Configuration configuration(
            Set<ResolvedDependency> dependencies, Set<ResolvedArtifact> resolvedArtifacts) {
        var configuration = mock(Configuration.class);
        var resolved = mock(ResolvedConfiguration.class);
        when(configuration.getResolvedConfiguration()).thenReturn(resolved);
        when(resolved.getFirstLevelModuleDependencies()).thenReturn(dependencies);
        when(resolved.getResolvedArtifacts()).thenReturn(resolvedArtifacts);
        return configuration;
    }

    private static ResolvedArtifact projectArtifact(String group, String name, String projectPath) {
        var artifact = mock(ResolvedArtifact.class);
        var componentIdentifier = mock(ProjectComponentIdentifier.class);
        when(componentIdentifier.getProjectPath()).thenReturn(projectPath);
        var artifactIdentifier = mock(ComponentArtifactIdentifier.class);
        when(artifactIdentifier.getComponentIdentifier()).thenReturn(componentIdentifier);
        when(artifact.getId()).thenReturn(artifactIdentifier);
        var moduleVersionIdentifier = mock(ModuleVersionIdentifier.class);
        when(moduleVersionIdentifier.getGroup()).thenReturn(group);
        when(moduleVersionIdentifier.getName()).thenReturn(name);
        var moduleVersion = mock(ResolvedModuleVersion.class);
        when(moduleVersion.getId()).thenReturn(moduleVersionIdentifier);
        when(artifact.getModuleVersion()).thenReturn(moduleVersion);
        return artifact;
    }

    private static ResolvedDependency resolvedDependency(
            String group, String name, String extension, String fileName, Set<ResolvedDependency> children) {
        var dependency = mock(ResolvedDependency.class);
        var artifact = mock(ResolvedArtifact.class);
        when(dependency.getModuleGroup()).thenReturn(group);
        when(dependency.getModuleName()).thenReturn(name);
        when(dependency.getChildren()).thenReturn(children);
        when(dependency.getModuleArtifacts()).thenReturn(Set.of(artifact));
        when(artifact.getExtension()).thenReturn(extension);
        when(artifact.getFile()).thenReturn(new File(fileName));
        return dependency;
    }

    private static Dependency requestedDependency(String group, String name) {
        var dependency = mock(org.gradle.api.artifacts.ModuleDependency.class);
        when(dependency.getGroup()).thenReturn(group);
        when(dependency.getName()).thenReturn(name);
        return dependency;
    }

    private static ProjectDependency projectDependency(String group, String name) {
        var dependency = mock(ProjectDependency.class);
        when(dependency.getGroup()).thenReturn(group);
        when(dependency.getName()).thenReturn(name);
        return dependency;
    }

    private record Fixture(
            RuntimeClasspathArtifacts artifacts, ConfigurationContainer configurations, Configuration detached) {}
}
