package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.CORE_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.PLUGINS_DEPENDENCY_CONFIGURATION_NAME

/**
 * Adds metadata to the JPI's POM.
 *
 * The POM is parsed by the <a href="https://github.com/jenkinsci/backend-update-center2">Jenkins Update Center
 * Generator</a> to extract the following information:
 * <ul>
 *     <li>The URL to the wiki page (<code>/project/url</code>)
 *     <li>The SCM Host (<code>/project/scm/connection</code>)
 *     <li>The project name (<code>/project/name</code>)
 *     <li>An excerpt (<code>/project/description</code>)
 * </ul>
 */
class JpiPomCustomizer {
    private final Project project
    private final JpiExtension jpiExtension

    JpiPomCustomizer(Project project) {
        this.project = project
        this.jpiExtension = project.extensions.findByType(JpiExtension)
    }

    void customizePom(Node pom) {
        pom.appendNode('name', jpiExtension.displayName)
        if (jpiExtension.url) {
            pom.appendNode('url', jpiExtension.url)
        }
        if (project.description) {
            pom.appendNode('description', project.description)
        }
        if (jpiExtension.gitHubUrl) {
            pom.append(makeScmNode())
        }
        if (!jpiExtension.licenses.isEmpty()) {
            pom.appendNode('licenses', jpiExtension.licenses.collect { JpiLicense l -> makeLicenseNode(l) })
        }
        if (!jpiExtension.developers.isEmpty()) {
            pom.appendNode('developers', jpiExtension.developers.collect { JpiDeveloper d -> makeDeveloperNode(d) })
        }
        if (repositories) {
            pom.appendNode('repositories', repositories.collect { makeRepositoryNode(it) })
        }
        fixDependencies(pom)
    }

    private void fixDependencies(Node pom) {
        DependencySet pluginDependencies = project.configurations.
                getByName(PLUGINS_DEPENDENCY_CONFIGURATION_NAME).dependencies
        DependencySet optionalPluginDependencies = project.configurations.
                getByName(OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME).dependencies
        DependencySet coreDependencies = project.configurations.
                getByName(CORE_DEPENDENCY_CONFIGURATION_NAME).dependencies
        DependencySet compileDependencies = project.configurations.
                getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).dependencies
        Set<Dependency> allPluginDependencies = pluginDependencies + optionalPluginDependencies

        Node dependenciesNode = pom.dependencies[0] as Node
        dependenciesNode = dependenciesNode ?: pom.appendNode('dependencies')
        (compileDependencies - coreDependencies + pluginDependencies + optionalPluginDependencies).each {
            Node dependency = dependenciesNode.appendNode('dependency')
            dependency.appendNode('groupId', it.group)
            dependency.appendNode('artifactId', it.name)
            dependency.appendNode('version', it.version)
        }
        dependenciesNode.each { Node dependency ->
            String groupId = dependency.groupId.text()
            String artifactId = dependency.artifactId.text()
            Node scope = dependency.scope[0] as Node
            Node exclusions = dependency.exclusions[0] as Node

            // add the optional element for all optional plugin dependencies
            if (optionalPluginDependencies.any { it.group == groupId && it.name == artifactId }) {
                dependency.appendNode('optional', true)
            }

            // remove the scope for all plugin and compile dependencies
            if (scope && (allPluginDependencies + compileDependencies).any {
                it.group == groupId && it.name == artifactId
            }) {
                dependency.remove(scope)
            }

            // remove exclusions from all plugin dependencies
            if (exclusions && allPluginDependencies.any { it.group == groupId && it.name == artifactId }) {
                dependency.remove(exclusions)
            }

            compileDependencies.withType(ModuleDependency).find {
                it.group == groupId && it.name == artifactId && it.excludeRules
            }.each {
                exclusions = dependency.appendNode('exclusions')
                it.excludeRules.each {
                    Node exclusion = exclusions.appendNode('exclusion')
                    exclusion.appendNode('groupId', it.group)
                    exclusion.appendNode('artifactId', it.module)
                }
            }
        }

        coreDependencies.each {
            Node dependency = dependenciesNode.appendNode('dependency')
            dependency.appendNode('groupId', it.group)
            dependency.appendNode('artifactId', it.name)
            dependency.appendNode('version', it.version)
            dependency.appendNode('scope', 'provided')
        }
    }

    private Node makeScmNode() {
        Node scmNode = new Node(null, 'scm')
        scmNode.appendNode('url', jpiExtension.gitHubUrl)
        if (jpiExtension.gitHubUrl =~ /^https:\/\/github\.com/) {
            scmNode.appendNode('connection', jpiExtension.gitHubUrl.replaceFirst(~/https:/, 'scm:git:git:') + '.git')
        }
        scmNode
    }

    private List<MavenArtifactRepository> getRepositories() {
        project.repositories.withType(MavenArtifactRepository).findAll {
            !(it.name =~ "${DEFAULT_MAVEN_CENTRAL_REPO_NAME}\\d*" || it.name =~ "${DEFAULT_MAVEN_LOCAL_REPO_NAME}\\d*")
        }
    }

    private static Node makeRepositoryNode(MavenArtifactRepository repository) {
        Node repositoryNode = new Node(null, 'repository')
        repositoryNode.appendNode('id', repository.name)
        repositoryNode.appendNode('url', repository.url)
        repositoryNode
    }

    private static Node makeDeveloperNode(JpiDeveloper developer) {
        Node developerNode = new Node(null, 'developer')
        JpiDeveloper.LEGAL_FIELDS.each { String key ->
            def value = developer[key]
            if (value) {
                developerNode.appendNode(key, value)
            }
        }
        developerNode
    }

    private static Node makeLicenseNode(JpiLicense license) {
        Node licenseNode = new Node(null, 'license')
        JpiLicense.LEGAL_FIELDS.each { String key ->
            def value = license[key]
            if (value) {
                licenseNode.appendNode(key, value)
            }
        }
        licenseNode
    }
}
