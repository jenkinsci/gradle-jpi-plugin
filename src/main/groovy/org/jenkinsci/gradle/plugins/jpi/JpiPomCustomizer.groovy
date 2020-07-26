package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomLicense
import org.gradle.api.publish.maven.MavenPomLicenseSpec
import org.gradle.api.publish.maven.MavenPomScm
import org.jenkinsci.gradle.plugins.jpi.manifest.JpiPomDeveloper

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME

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

    @CompileStatic
    void customizePom(MavenPom pom) {
        pom.packaging = jpiExtension.fileExtension
        pom.name.set(jpiExtension.displayName)
        pom.url.set(jpiExtension.url)
        pom.description.set(project.description)
        def github = jpiExtension.gitHubUrl
        if (github) {
            pom.scm { MavenPomScm s ->
                s.url.set(github)
                if (github =~ /^https:\/\/github\.com/) {
                    s.connection.set(github.replaceFirst(~/https:/, 'scm:git:git:') + '.git')
                }
            }
        }
        if (!jpiExtension.licenses.isEmpty()) {
            pom.licenses { MavenPomLicenseSpec s ->
                jpiExtension.licenses.each { JpiLicense declared ->
                    s.license { MavenPomLicense l ->
                        ['name'        : l.name,
                         'url'         : l.url,
                         'distribution': l.distribution,
                         'comments'    : l.comments,
                        ].each {
                            mapExtensionToProperty(declared, it.key, it.value)
                        }
                    }
                }
            }
        }
        def developers = jpiExtension.developerListProperty.get()
        pom.developers { spec ->
            for (JpiPomDeveloper dev : developers) {
                spec.developer { d ->
                    d.id.set(dev.id)
                    d.name.set(dev.name)
                    d.email.set(dev.email)
                    d.url.set(dev.url)
                    d.organization.set(dev.organization)
                    d.organizationUrl.set(dev.organizationUrl)
                    d.timezone.set(dev.timezone)
                }
            }
        }
        pom.withXml { XmlProvider xmlProvider -> additionalCustomizations(xmlProvider.asNode()) }
    }

    private static mapExtensionToProperty(Object from, String property, Property<String> to) {
        to.set(from.getProperty(property) as String)
    }

    void additionalCustomizations(Node pom) {
        if (repositories) {
            pom.appendNode('repositories', repositories.collect { makeRepositoryNode(it) })
        }
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
}
