package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

@CompileStatic
class DependencyAnalysis {

    private class JpiConfigurations {
        Configuration consumableLibraries
        Configuration consumablePlugins
        Configuration resolvablePlugins

        JpiConfigurations(Configuration consumableLibraries,
                          Configuration consumablePlugins,
                          Configuration resolvablePlugins) {
            this.consumableLibraries = consumableLibraries
            this.consumablePlugins = consumablePlugins
            this.resolvablePlugins = resolvablePlugins
        }
    }

    final Configuration allLibraryDependencies

    private static final Attribute CATEGORY_ATTRIBUTE =
            Attribute.of(Category.CATEGORY_ATTRIBUTE.name, String)
    private static final Attribute LIBRARY_ELEMENTS_ATTRIBUTE =
            Attribute.of(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, String)
    private final List<JpiConfigurations> jpiConfigurations = []

    private DependencyAnalysisResult analysisResult

    DependencyAnalysis(Project project) {
        this.allLibraryDependencies = project.configurations.detachedConfiguration()
        this.allLibraryDependencies.attributes.attribute(Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage, Usage.JAVA_RUNTIME))
        this.allLibraryDependencies.withDependencies {
            // do the analysis when this configuration is resolved
            analyse()
        }
    }

    void registerJpiConfigurations(Configuration consumableLibraries,
                                   Configuration consumablePlugins,
                                   Configuration resolvablePlugins) {
        jpiConfigurations.add(new JpiConfigurations(consumableLibraries, consumablePlugins, resolvablePlugins))
    }

    DependencyAnalysisResult analyse() {
        if (analysisResult) {
            return analysisResult
        }

        def manifestEntry = new StringBuilder()

        jpiConfigurations.each { confs ->
            analyseDependencies(confs, allLibraryDependencies, manifestEntry)
        }
        analysisResult = new DependencyAnalysisResult(manifestEntry.toString())
        analysisResult
    }

    private analyseDependencies(JpiConfigurations configurations,
                                Configuration allLibraries, StringBuilder manifestEntry) {
        def optional = configurations.resolvablePlugins.name != JpiPlugin.JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME

        List<ModuleVersionIdentifier> processedComponents = []
        configurations.resolvablePlugins.incoming.resolutionResult.root.dependencies.each { DependencyResult result ->
            def selected = getSelectedComponent(result, processedComponents)
            selected?.variants?.each { variant ->
                def attributes = variant.attributes
                if (attributes.getAttribute(JpiVariantRule.EMPTY_VARIANT) == Boolean.TRUE
                        || attributes.getAttribute(CATEGORY_ATTRIBUTE) != Category.LIBRARY
                        || attributes.getAttribute(LIBRARY_ELEMENTS_ATTRIBUTE) != JpiPlugin.JPI) {
                    // Skip dependencies that are not libraries with JPI files.
                    // We request these in the setup in JpiPlugin.configureConfigurations().
                    // However, an individual dependency can override attributes, for example 'category=platform'.
                    return
                }

                def moduleVersion = selected.moduleVersion
                if (isMainFeature(moduleVersion, variant)) {
                    addToManifestEntry(manifestEntry, selected, optional)
                } else {
                    selected.getDependenciesForVariant(variant).each { featureDependency ->
                        // add dependencies of the selected optional feature
                        addToManifestEntry(manifestEntry,
                                getSelectedComponent(featureDependency, processedComponents), optional)
                    }
                }

                def moduleDependencies = configurations.resolvablePlugins.allDependencies.findAll {
                    it instanceof ModuleDependency && it.group == moduleVersion.group && it.name == moduleVersion.name
                }
                configurations.consumablePlugins.dependencies.addAll(moduleDependencies)
            }
        }
        allLibraries.dependencies.addAll(configurations.consumableLibraries.allDependencies
                - configurations.consumablePlugins.allDependencies)
    }

    private static ResolvedComponentResult getSelectedComponent(DependencyResult dependency,
                                                                List<ModuleVersionIdentifier> processedComponents) {
        if (dependency.constraint || !(dependency instanceof ResolvedDependencyResult)) {
            return null
        }
        def selected = ((ResolvedDependencyResult) dependency).selected
        def moduleVersion = selected.moduleVersion
        if (moduleVersion == null || processedComponents.contains(moduleVersion)) {
            // If feature variants are used, it is common to have multiple dependencies to the same component.
            // These then turn up in the result multiple times.
            return null
        }
        processedComponents.add(moduleVersion)
        selected
    }

    static boolean isMainFeature(ModuleVersionIdentifier component, ResolvedVariantResult variant) {
        // either no capability definition of main capability is explicitly defined
        variant.capabilities.isEmpty() || variant.capabilities.any {
            it.group == component.group && it.name == component.name
        }
    }

    private static void addToManifestEntry(StringBuilder manifestEntry,
                                           ResolvedComponentResult selected,
                                           boolean optional) {
        if (selected) {
            if (manifestEntry.length() > 0) {
                manifestEntry.append(',')
            }
            manifestEntry.append(selected.moduleVersion.name)
            manifestEntry.append(':')
            manifestEntry.append(selected.moduleVersion.version)
            if (optional) {
                manifestEntry.append(';resolution:=optional')
            }
        }
    }
}
