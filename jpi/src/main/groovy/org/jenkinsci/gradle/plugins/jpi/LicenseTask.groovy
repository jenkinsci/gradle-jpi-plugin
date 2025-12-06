package org.jenkinsci.gradle.plugins.jpi

import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jenkinsci.gradle.plugins.jpi.internal.DependencyLicenseValidator
import org.jenkinsci.gradle.plugins.jpi.internal.JpiExtensionBridge
import org.jenkinsci.gradle.plugins.jpi.internal.LicenseDataExtractor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LicenseTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseTask)

    @Classpath
    Configuration libraryConfiguration

    @OutputDirectory
    File outputDirectory

    @Internal
    final Property<JpiExtensionBridge> jpiExtension = project.objects.property(JpiExtensionBridge)

    @Internal
    final Property<String> projectVersion = project.objects.property(String)

    @Internal
    final Property<String> projectName = project.objects.property(String)

    @Internal
    final Property<String> projectGroup = project.objects.property(String)

    @Internal
    final Property<String> projectDescription = project.objects.property(String)

    @Internal
    final Provider<Object> configurations = project.provider { project.configurations }

    @Internal
    final Provider<Object> dependencies = project.provider { project.dependencies }

    @Internal
    final Provider<Object> objects = project.provider { project.objects }

    LicenseTask() {
        jpiExtension.set(project.extensions.getByType(JpiExtension))
        projectVersion.set(project.provider { project.version.toString() })
        projectName.set(project.provider { project.name })
        projectGroup.set(project.provider { project.group.toString() })
        projectDescription.set(project.provider { project.description ?: '' })
    }

    @TaskAction
    void generateLicenseInfo() {
        Set<ResolvedArtifact> pomArtifacts = collectPomArtifacts()
        LicenseDataExtractor extractor = new LicenseDataExtractor()

        new File(outputDirectory, 'licenses.xml').withWriter { Writer writer ->
            MarkupBuilder xmlMarkup = new MarkupBuilder(writer)

            xmlMarkup.'l:dependencies'(
                    'xmlns:l': 'licenses',
                    version: projectVersion.get(),
                    artifactId: projectName.get(),
                    groupId: projectGroup.get(),
            ) {
                'l:dependency'(
                        version: projectVersion.get(),
                        artifactId: projectName.get(),
                        groupId: projectGroup.get(),
                        name: jpiExtension.get().humanReadableName.get(),
                        url: jpiExtension.get().homePage.orNull?.toASCIIString(),
                ) {
                    'l:description'(projectDescription.get())
                    jpiExtension.get().pluginLicenses.get().each { license ->
                        'l:license'(url: license.url.orNull, name: license.name.orNull)
                    }
                }

                pomArtifacts.each { ResolvedArtifact pomArtifact ->
                    pomArtifact.file.withReader { reader ->
                        def data = extractor.extractFrom(reader)
                        ModuleVersionIdentifier gav = pomArtifact.moduleVersion.id
                        String name = data.name
                        String description = data.description
                        String url = data.url

                        'l:dependency'(
                                version: gav.version, artifactId: gav.name, groupId: gav.group, name: name, url: url,
                        ) {
                            'l:description'(description)
                            data.licenses.each { license ->
                                'l:license'(url: license.url, name: license.name)
                            }
                        }
                    }
                }
            }
        }
    }

    private Set<ResolvedArtifact> collectPomArtifacts() {
        def deps = collectDependencies()
        def detached = configurations.get().detachedConfiguration(deps)
        detached.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.get().named(Usage, Usage.JAVA_RUNTIME))
        def lenient = detached.resolvedConfiguration.lenientConfiguration
        // unresolvedModuleDependencies comes back empty even with a failed pom
        // so we must do set difference between everything requested and what was resolved
        def requested = lenient.allModuleDependencies.collect { it.module.toString() }.toSet()
        def resolved = lenient.artifacts.collect { it.moduleVersion.toString() }.toSet()
        def destination = outputDirectory.toPath().resolve('licenses.xml')
        def result = DependencyLicenseValidator.validate(requested, resolved, destination)
        if (result.isUnresolved()) {
            LOGGER.warn(result.message)
        }
        lenient.artifacts
    }

    private Dependency[] collectDependencies() {
        libraryConfiguration.resolvedConfiguration.resolvedArtifacts.findAll { ResolvedArtifact artifact ->
            artifact.id.componentIdentifier instanceof ModuleComponentIdentifier
        }.collect { ResolvedArtifact artifact ->
            ModuleVersionIdentifier id = artifact.moduleVersion.id
            dependencies.get().create("${id.group}:${id.name}:${id.version}@pom")
        }
    }
}
