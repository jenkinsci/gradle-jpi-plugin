package org.jenkinsci.gradle.plugins.jpi2;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;

/** Resolves the library JARs that should be bundled into the plugin or referenced by HPL. */
class RuntimeClasspathArtifacts {
    private final Project project;
    private final Configuration configuration;
    private final Configuration jenkinsCore;
    private final ComponentIdentifier ownPlugin;
    private final Map<PluginProvider, RuntimeClasspathArtifacts> pluginProviders;
    private final FileCollection bundledLibraries;
    private FileCollection resolvedBundledLibraries;
    private Selection selection;

    RuntimeClasspathArtifacts(Project project, Configuration configuration, Configuration jenkinsCore) {
        this(project, configuration, jenkinsCore, null, new HashMap<>());
    }

    private RuntimeClasspathArtifacts(
            Project project,
            Configuration configuration,
            Configuration jenkinsCore,
            ComponentIdentifier ownPlugin,
            Map<PluginProvider, RuntimeClasspathArtifacts> pluginProviders) {
        this.project = project;
        this.configuration = configuration;
        this.jenkinsCore = jenkinsCore;
        this.ownPlugin = ownPlugin;
        this.pluginProviders = pluginProviders;
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
        return bundledArtifactIds().stream()
                .map(artifact -> (ModuleComponentIdentifier) artifact.getComponentIdentifier())
                .collect(Collectors.toSet());
    }

    private Set<ComponentArtifactIdentifier> bundledArtifactIds() {
        var selected = selectLibraries();
        // Legacy artifact metadata exposes classifiers without enumerating local file trees or
        // reading artifact files. Those files may not exist until their producer tasks execute.
        return moduleArtifacts().stream()
                .map(ResolvedArtifact::getId)
                .filter(artifact -> selected.components().contains(artifact.getComponentIdentifier()))
                .filter(artifact -> !selected.providedArtifacts().contains(artifact))
                .collect(Collectors.toSet());
    }

    private Set<ResolvedArtifact> moduleArtifacts() {
        return moduleArtifacts(configuration);
    }

    private static Set<ResolvedArtifact> moduleArtifacts(Configuration configuration) {
        return configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies().stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .filter(artifact -> artifact.getExtension().equals("jar"))
                .filter(artifact -> artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)
                .collect(Collectors.toSet());
    }

    private FileCollection providedProjectFiles() {
        var selected = selectLibraries();
        var components = selected.components();
        var providedFiles = selected.providedFiles();
        return configuration
                .getIncoming()
                .artifactView(view -> {
                    view.componentFilter(id ->
                            isFileComponent(id) || id instanceof ProjectComponentIdentifier && components.contains(id));
                    view.setLenient(true);
                })
                .getFiles()
                .filter(file -> file.getName().endsWith(".jar") && !providedFiles.contains(file));
    }

    private FileCollection pluginMainFiles() {
        if (ownPlugin == null) {
            return project.files(project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile));
        }
        if (!(ownPlugin instanceof ProjectComponentIdentifier)) {
            return project.files();
        }
        // This detached graph selects only the producer's main variant. Selecting its
        // files separately leaves features requested solely by the consumer eligible.
        return configuration
                .getIncoming()
                .artifactView(view -> {
                    view.componentFilter(ownPlugin::equals);
                    view.setLenient(true);
                })
                .getFiles();
    }

    private synchronized FileCollection resolveBundledLibraries() {
        if (resolvedBundledLibraries != null) {
            return resolvedBundledLibraries;
        }
        var selected = selectLibraries();
        // These filters survive in the configuration cache. Capturing the whole selection
        // would also serialize resolved variants that older Gradle versions cannot restore.
        var components = selected.components();
        var providedFiles = selected.providedFiles();
        // Module metadata distinguishes classifiers before downloading files. Local and project
        // files must retain their live collections and producer tasks until execution.
        var moduleFiles = moduleArtifacts().stream()
                .filter(artifact -> components.contains(artifact.getId().getComponentIdentifier()))
                .filter(artifact -> !selected.providedArtifacts().contains(artifact.getId()))
                .map(ResolvedArtifact::getFile)
                .toList();
        var projectFiles = configuration
                .getIncoming()
                .artifactView(view -> view.componentFilter(id ->
                        isFileComponent(id) || id instanceof ProjectComponentIdentifier && components.contains(id)))
                .getFiles()
                .filter(file -> file.getName().endsWith(".jar") && !providedFiles.contains(file));
        resolvedBundledLibraries = project.getObjects().fileCollection().from(projectFiles, moduleFiles);
        return resolvedBundledLibraries;
    }

    private record Selection(
            Set<ComponentIdentifier> components,
            Set<ComponentArtifactIdentifier> providedArtifacts,
            FileCollection providedFiles) {}

    private record PluginProvider(Project project, Configuration core, ComponentIdentifier component) {}

    private synchronized Selection selectLibraries() {
        if (selection != null) {
            return selection;
        }
        var coreProvided = jenkinsCore.getIncoming().getResolutionResult().getAllComponents().stream()
                .map(ResolvedComponentResult::getId)
                .collect(Collectors.toCollection(HashSet::new));
        var components = configuration.getIncoming().getResolutionResult().getAllComponents();
        var plugins = components.stream()
                .filter(component -> component.getVariants().stream()
                        .anyMatch(variant -> variant.getDisplayName().equals(HpiMetadataRule.DEFAULT_RUNTIME_VARIANT)))
                .map(ResolvedComponentResult::getId)
                .collect(Collectors.toCollection(HashSet::new));
        var pluginProjects = project.getRootProject().getAllprojects().stream()
                .filter(candidate -> candidate != project)
                .filter(candidate -> candidate.getTasks().getNames().contains(V2JpiPlugin.JPI_TASK))
                .collect(Collectors.toMap(Project::getPath, candidate -> candidate));
        var root = configuration.getIncoming().getResolutionResult().getRoot();
        var currentBuild = root.getId() instanceof ProjectComponentIdentifier rootId ? rootId.getBuild() : null;
        var providedFiles = project.getObjects().fileCollection();
        if (ownPlugin != null) {
            providedFiles.from(pluginMainFiles());
        }
        var providedArtifacts = moduleArtifacts(jenkinsCore).stream()
                .map(ResolvedArtifact::getId)
                .collect(Collectors.toCollection(HashSet::new));
        collectProvidedFiles(jenkinsCore, coreProvided, providedFiles);
        // A plugin's main JAR is supplied by its archive, while a classifier requested
        // separately can belong solely to the consumer.
        moduleArtifacts().stream()
                .filter(artifact -> plugins.contains(artifact.getId().getComponentIdentifier()))
                .filter(artifact -> artifact.getClassifier() == null)
                .map(ResolvedArtifact::getId)
                .forEach(providedArtifacts::add);
        var pluginEdges = new HashSet<ResolvedDependencyResult>();
        collectPluginEdges(root.getDependencies(), plugins, pluginEdges, new HashSet<>());
        for (var dependency : pluginEdges) {
            if (dependency.getSelected().getId().equals(ownPlugin)) {
                continue;
            }
            RuntimeClasspathArtifacts upstreamArtifacts;
            if (dependency.getSelected().getId() instanceof ProjectComponentIdentifier id
                    && id.getBuild().equals(currentBuild)
                    && pluginProjects.containsKey(id.getProjectPath())) {
                var upstream = pluginProjects.get(id.getProjectPath());
                var upstreamRuntime = upstream.getConfigurations().getByName("defaultRuntime");
                var upstreamCore = upstream.getConfigurations().getByName("jenkinsCore");
                // A consumer can add paths that upstream excludes. Its merged graph does not
                // describe what the upstream archive actually provides.
                upstreamArtifacts = pluginProviders.computeIfAbsent(
                        new PluginProvider(upstream, upstreamCore, id),
                        ignored -> new RuntimeClasspathArtifacts(
                                upstream, upstreamRuntime, upstreamCore, null, pluginProviders));
            } else {
                // Included producers define their own core, so ancestors must share their
                // selection even when each ancestor has a different detached core graph.
                var key = new PluginProvider(
                        project,
                        dependency.getSelected().getId() instanceof ProjectComponentIdentifier ? null : jenkinsCore,
                        dependency.getSelected().getId());
                upstreamArtifacts = pluginProviders.get(key);
                if (upstreamArtifacts == null) {
                    // Module coordinates also allow Gradle to substitute producers in included builds.
                    var version = dependency.getSelected().getModuleVersion();
                    var coordinates = version.getGroup() + ":" + version.getName() + ":" + version.getVersion();
                    if (dependency.getSelected().getId() instanceof ProjectComponentIdentifier
                            && dependency.getRequested() instanceof ModuleComponentSelector requested) {
                        // Explicit substitutions can use an alias unrelated to the producer's
                        // published coordinates. Preserve the request that selected that project.
                        coordinates = requested.getGroup() + ":" + requested.getModule() + ":" + requested.getVersion();
                    }
                    var upstreamRuntime = project.getConfigurations()
                            .detachedConfiguration(project.getDependencies().create(coordinates));
                    upstreamRuntime
                            .getAttributes()
                            .attribute(
                                    ArtifactType.ARTIFACT_TYPE_ATTRIBUTE,
                                    project.getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));
                    upstreamRuntime
                            .getAttributes()
                            .attribute(
                                    Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                    var upstreamCore = jenkinsCore;
                    if (dependency.getSelected().getId() instanceof ProjectComponentIdentifier) {
                        // An included producer's core configuration is available through project
                        // substitution, without reaching into another build's mutable project model.
                        var coreDependency =
                                (ModuleDependency) project.getDependencies().create(coordinates);
                        coreDependency.setTargetConfiguration("jenkinsCore");
                        upstreamCore = project.getConfigurations().detachedConfiguration(coreDependency);
                    }
                    upstreamArtifacts = new RuntimeClasspathArtifacts(
                            project,
                            upstreamRuntime,
                            upstreamCore,
                            dependency.getSelected().getId(),
                            pluginProviders);
                    pluginProviders.put(key, upstreamArtifacts);
                }
            }
            // An upstream plugin's direct dependency can itself be supplied by one of its
            // plugins. Reuse packaging selection before counting its contribution here.
            providedFiles.from(upstreamArtifacts.pluginMainFiles(), upstreamArtifacts.providedProjectFiles());
            providedArtifacts.addAll(upstreamArtifacts.bundledArtifactIds());
        }
        var bundled = new HashSet<ComponentIdentifier>();
        var bundledVariants = new HashSet<Variant>();
        root.getDependencies().stream()
                .filter(edge -> !edge.isConstraint())
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .forEach(dependency -> collectBundledComponents(dependency, bundled, bundledVariants));
        selection = new Selection(bundled, providedArtifacts, providedFiles);
        return selection;
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

    private static boolean isFileComponent(ComponentIdentifier id) {
        return !(id instanceof ModuleComponentIdentifier) && !(id instanceof ProjectComponentIdentifier);
    }

    private static void collectProvidedFiles(
            Configuration configuration, Set<ComponentIdentifier> components, ConfigurableFileCollection files) {
        files.from(configuration
                .getIncoming()
                .artifactView(view -> {
                    // One component can publish separate main and feature JARs. Only artifacts
                    // present in the upstream graph are supplied by that plugin.
                    view.componentFilter(id ->
                            isFileComponent(id) || id instanceof ProjectComponentIdentifier && components.contains(id));
                    // Unresolved modules excluded by the consumer do not affect reachable local files.
                    view.setLenient(true);
                })
                .getFiles());
    }
}
