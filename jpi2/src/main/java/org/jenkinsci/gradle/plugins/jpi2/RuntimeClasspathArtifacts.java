package org.jenkinsci.gradle.plugins.jpi2;

import static org.jenkinsci.gradle.plugins.jpi2.ArtifactType.ARTIFACT_TYPE_ATTRIBUTE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the library JARs that should be bundled into the plugin or referenced by HPL.
 */
class RuntimeClasspathArtifacts {
    private final Project project;
    private final Configuration configuration;
    private final Configuration jenkinsCore;
    private final FileCollection bundledLibraries;
    private FileCollection resolvedBundledLibraries;

    RuntimeClasspathArtifacts(Project project, Configuration configuration, Configuration jenkinsCore) {
        this.project = project;
        this.configuration = configuration;
        this.jenkinsCore = jenkinsCore;
        bundledLibraries = project.getObjects().fileCollection().from(project.provider(this::resolveBundledLibraries));
    }

    @NotNull
    FileCollection getBundledLibraries() {
        return bundledLibraries;
    }

    Set<ModuleComponentIdentifier> getBundledModules() {
        configuration.getIncoming().getResolutionResult().getAllDependencies().stream()
                .filter(UnresolvedDependencyResult.class::isInstance)
                .map(UnresolvedDependencyResult.class::cast)
                .forEach(dependency -> project.getLogger()
                        .warn(
                                "License metadata is incomplete: {}: {}",
                                dependency.getAttempted().getDisplayName(),
                                dependency.getFailure().getMessage()));
        var root = configuration.getIncoming().getResolutionResult().getRoot();
        var components = configuration.getIncoming().getResolutionResult().getAllComponents();
        var core = jenkinsCore.getIncoming().getResolutionResult().getAllComponents().stream()
                .map(ResolvedComponentResult::getId)
                .collect(Collectors.toSet());
        var currentBuild = ((ProjectComponentIdentifier) root.getId()).getBuild();
        var pluginPaths = project.getRootProject().getAllprojects().stream()
                .filter(candidate -> candidate != project)
                .filter(candidate -> candidate.getTasks().getNames().contains(V2JpiPlugin.JPI_TASK))
                .map(Project::getPath)
                .collect(Collectors.toSet());
        var plugins = components.stream()
                .filter(component -> {
                    if (component.getId() instanceof ProjectComponentIdentifier id) {
                        return id.getBuild().equals(currentBuild) && pluginPaths.contains(id.getProjectPath());
                    }
                    return component.getVariants().stream()
                            .anyMatch(variant ->
                                    variant.getDisplayName().equals(HpiMetadataRule.DEFAULT_RUNTIME_VARIANT));
                })
                .map(ResolvedComponentResult::getId)
                .collect(Collectors.toSet());
        var pluginEdges = new HashSet<ResolvedDependencyResult>();
        collectPluginEdges(root.getDependencies(), plugins, pluginEdges, new HashSet<>());
        var provided = new HashSet<ComponentIdentifier>();
        var providedVariants = new HashSet<Variant>();
        pluginEdges.forEach(edge -> collectProvidedComponents(edge, Set.of(), plugins, provided, providedVariants));
        var bundled = new HashSet<ComponentIdentifier>();
        var bundledVariants = new HashSet<Variant>();
        root.getDependencies().stream()
                .filter(edge -> !edge.isConstraint())
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .filter(edge ->
                        !core.contains(edge.getSelected().getId()) && !providedVariants.contains(Variant.of(edge)))
                .forEach(edge -> collectBundledComponents(edge, bundled, bundledVariants));
        bundled.removeAll(provided);
        // Artifact enumeration also evaluates local file collections, even with a component
        // filter. Metadata lookup must not snapshot generated files before their producers run.
        return bundledVariants.stream()
                .filter(variant -> bundled.contains(variant.component()))
                .filter(variant -> variant.component() instanceof ModuleComponentIdentifier)
                .filter(variant -> {
                    var attributes = variant.variant().getAttributes();
                    var category = attributes.getAttribute(Category.CATEGORY_ATTRIBUTE);
                    var elements = attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
                    return (category == null || Category.LIBRARY.equals(category.getName()))
                            && (elements == null || LibraryElements.JAR.equals(elements.getName()));
                })
                .map(variant -> (ModuleComponentIdentifier) variant.component())
                .collect(Collectors.toSet());
    }

    private synchronized FileCollection resolveBundledLibraries() {
        if (resolvedBundledLibraries != null) {
            return resolvedBundledLibraries;
        }
        var roots = configuration.getResolvedConfiguration().getFirstLevelModuleDependencies();
        var pluginTransitives = getAllJpiPluginTransitiveDependencies(roots);
        var provided = getAllJenkinsCoreDependencies();
        provided.addAll(pluginTransitives);
        var providedModules = provided.stream().map(Module::of).collect(Collectors.toSet());
        var directJarDependencies = getDirectJarDependencies(roots, providedModules);
        var detachedConfiguration =
                project.getConfigurations().detachedConfiguration(directJarDependencies.toArray(new Dependency[0]));
        detachedConfiguration
                .getAttributes()
                .attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        project.getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR));
        detachedConfiguration.shouldResolveConsistentlyWith(configuration);

        var jpiProvidedJars = pluginTransitives.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .filter(artifact -> "jar".equals(artifact.getExtension()))
                .map(artifact -> artifact.getFile().getName())
                .collect(Collectors.toSet());
        resolvedBundledLibraries = detachedConfiguration.filter(file -> !jpiProvidedJars.contains(file.getName()));
        return resolvedBundledLibraries;
    }

    private record Module(String group, String name) {
        static Module of(ResolvedDependency dependency) {
            return new Module(dependency.getModuleGroup(), dependency.getModuleName());
        }
    }

    @NotNull
    private List<Dependency> getDirectJarDependencies(
            Set<ResolvedDependency> resolvedDependencies, Set<Module> providedModules) {
        var requestedDependencies = configuration.getAllDependencies();

        var projectPathMap = buildProjectPathMap();

        return resolvedDependencies.stream()
                .filter(dependency -> !providedModules.contains(Module.of(dependency)))
                .flatMap(dependency -> dependency.getModuleArtifacts().stream()
                        .filter(artifact -> "jar".equals(artifact.getExtension()))
                        .flatMap(
                                artifact -> requestedDependencies.stream()
                                        .filter(reqDep -> matches(dependency, reqDep, projectPathMap))
                                        .findFirst()
                                        .stream()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @NotNull
    private Map<String, String> buildProjectPathMap() {
        var projectPathMap = new HashMap<String, String>();

        configuration.getResolvedConfiguration().getResolvedArtifacts().stream()
                .filter(it -> it.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier)
                .forEach(artifact -> {
                    ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
                    if (componentIdentifier instanceof ProjectComponentIdentifier p) {
                        var moduleGroup = artifact.getModuleVersion().getId().getGroup();
                        var moduleName = artifact.getModuleVersion().getId().getName();
                        var key = moduleGroup + ":" + moduleName;
                        projectPathMap.put(key, p.getProjectPath());
                    }
                });

        return projectPathMap;
    }

    @NotNull
    private Set<ResolvedDependency> getAllJenkinsCoreDependencies() {
        var allDependencies = new HashSet<ResolvedDependency>();
        var firstLevelDependencies = jenkinsCore.getResolvedConfiguration().getFirstLevelModuleDependencies();

        for (var dependency : firstLevelDependencies) {
            collectAllDependencies(dependency, allDependencies);
        }

        return allDependencies;
    }

    private void collectAllDependencies(ResolvedDependency dependency, Set<ResolvedDependency> collector) {
        if (collector.add(dependency)) {
            for (var child : dependency.getChildren()) {
                collectAllDependencies(child, collector);
            }
        }
    }

    @NotNull
    private Set<ResolvedDependency> getAllJpiPluginTransitiveDependencies(
            Set<ResolvedDependency> resolvedDependencies) {
        var allTransitives = new HashSet<ResolvedDependency>();

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

    private boolean matches(ResolvedDependency dependency, Dependency reqDep, Map<String, String> projectPathMap) {
        if (reqDep instanceof ProjectDependency projectDependency) {
            var key = (projectDependency.getGroup() == null ? "" : projectDependency.getGroup()) + ":"
                    + projectDependency.getName();
            var projectPath = projectPathMap.get(key);

            if (projectPath == null) {
                return false;
            }

            var dependencyProject = project.getRootProject().getAllprojects().stream()
                    .filter(it -> it.getPath().equals(projectPath))
                    .findFirst();

            assert dependencyProject.isPresent();

            if (!dependencyProject.get().getTasks().getNames().contains("jpi")) {
                return Objects.equals(reqDep.getGroup(), dependency.getModuleGroup())
                        && reqDep.getName().equals(dependency.getModuleName());
            }
            return false;
        } else if (reqDep instanceof ModuleDependency moduleDependency) {
            return Objects.equals(moduleDependency.getGroup(), dependency.getModuleGroup())
                    && Objects.equals(moduleDependency.getName(), dependency.getModuleName());
        } else {
            return false;
        }
    }

    private record Variant(ComponentIdentifier component, ResolvedVariantResult variant) {
        static Variant of(ResolvedDependencyResult dependency) {
            return new Variant(dependency.getSelected().getId(), dependency.getResolvedVariant());
        }
    }

    private static void collectPluginEdges(
            Collection<? extends DependencyResult> dependencies,
            Set<ComponentIdentifier> plugins,
            Set<ResolvedDependencyResult> pluginEdges,
            Set<Variant> visited) {
        for (var edge : dependencies) {
            if (edge.isConstraint()
                    || !(edge instanceof ResolvedDependencyResult dependency)
                    || !visited.add(Variant.of(dependency))) {
                continue;
            }
            if (plugins.contains(dependency.getSelected().getId())) {
                pluginEdges.add(dependency);
            }
            collectPluginEdges(
                    dependency.getSelected().getDependenciesForVariant(dependency.getResolvedVariant()),
                    plugins,
                    pluginEdges,
                    visited);
        }
    }

    private static void collectBundledComponents(
            ResolvedDependencyResult dependency, Set<ComponentIdentifier> bundled, Set<Variant> visited) {
        if (!visited.add(Variant.of(dependency))) {
            return;
        }
        var component = dependency.getSelected();
        bundled.add(component.getId());
        component.getDependenciesForVariant(dependency.getResolvedVariant()).stream()
                .filter(edge -> !edge.isConstraint())
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .forEach(child -> collectBundledComponents(child, bundled, visited));
    }

    private static void collectProvidedComponents(
            ResolvedDependencyResult dependency,
            Set<ComponentIdentifier> coreProvided,
            Set<ComponentIdentifier> plugins,
            Set<ComponentIdentifier> provided,
            Set<Variant> visited) {
        if (!visited.add(Variant.of(dependency))) {
            return;
        }
        var component = dependency.getSelected();
        provided.add(component.getId());
        component.getDependenciesForVariant(dependency.getResolvedVariant()).stream()
                .filter(edge -> !edge.isConstraint())
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .filter(child -> !plugins.contains(component.getId())
                        || !coreProvided.contains(child.getSelected().getId()))
                .forEach(child -> collectProvidedComponents(child, coreProvided, plugins, provided, visited));
    }
}
