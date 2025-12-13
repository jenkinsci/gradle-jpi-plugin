package org.jenkinsci.gradle.plugins.jpi;

import groovy.util.Node;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomDeveloperSpec;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomLicenseSpec;
import org.gradle.api.publish.maven.MavenPomScm;
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper;
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicense;
import org.jenkinsci.gradle.plugins.jpi.internal.JpiExtensionBridge;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME;
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME;

/**
 * Adds metadata to the JPI's POM.
 * The POM is parsed by the <a href="https://github.com/jenkinsci/backend-update-center2">Jenkins Update Center
 * Generator</a> to extract the following information:
 * <ul>
 *     <li>The URL to the wiki page (<code>/project/url</code>)
 *     <li>The SCM Host (<code>/project/scm/connection</code>)
 *     <li>The project name (<code>/project/name</code>)
 *     <li>An excerpt (<code>/project/description</code>)
 * </ul>
 */
public class JpiPomCustomizer {
    private final Project project;

    /**
     * Creates a new POM customizer.
     *
     * @param project the project to customize the POM for
     */
    public JpiPomCustomizer(Project project) {
        this.project = project;
    }

    /**
     * Customizes the given Maven POM with Jenkins plugin metadata.
     *
     * @param pom the POM to customize
     */
    public void customizePom(MavenPom pom) {
        JpiExtensionBridge jpiExtension = project.getExtensions().getByType(JpiExtensionBridge.class);
        pom.setPackaging(jpiExtension.getExtension().get());
        pom.getName().set(jpiExtension.getHumanReadableName());
        pom.getUrl().set(jpiExtension.getHomePage().map(URI::toString));
        pom.getDescription().set(project.getDescription());
        String gitHub = jpiExtension.getGitHub().map(URI::toString).getOrNull();
        if (gitHub != null && !gitHub.isEmpty()) {
            pom.scm(new Action<MavenPomScm>() {
                @Override
                public void execute(MavenPomScm s) {
                    s.getUrl().set(gitHub);
                    if (gitHub.startsWith("https://github.com/")) {
                        s.getConnection().set(gitHub.replaceFirst("^https:", "scm:git:git:") + ".git");
                    }
                    s.getTag().set(jpiExtension.getScmTag());
                }
            });
        }

        List<PluginLicense> licenses = jpiExtension.getPluginLicenses().get();
        if (!licenses.isEmpty()) {
            pom.licenses(new Action<MavenPomLicenseSpec>() {
                @Override
                public void execute(MavenPomLicenseSpec s) {
                    for (PluginLicense license : licenses) {
                        s.license(new Action<MavenPomLicense>() {
                            @Override
                            public void execute(MavenPomLicense l) {
                                l.getName().set(license.getName());
                                l.getUrl().set(license.getUrl());
                                l.getDistribution().set(license.getDistribution());
                                l.getComments().set(license.getComments());
                            }
                        });
                    }
                }
            });
        }

        List<PluginDeveloper> developers = jpiExtension.getPluginDevelopers().get();
        if (!developers.isEmpty()) {
            pom.developers(new Action<MavenPomDeveloperSpec>() {
                @Override
                public void execute(MavenPomDeveloperSpec s) {
                    for (PluginDeveloper developer : developers) {
                        s.developer(new Action<MavenPomDeveloper>() {
                            @Override
                            public void execute(MavenPomDeveloper d) {
                                d.getId().set(developer.getId());
                                d.getName().set(developer.getName());
                                d.getEmail().set(developer.getEmail());
                                d.getUrl().set(developer.getUrl());
                                d.getOrganization().set(developer.getOrganization());
                                d.getOrganizationUrl().set(developer.getOrganizationUrl());
                                d.getTimezone().set(developer.getTimezone());
                                d.getRoles().set(developer.getRoles());
                                d.getProperties().set(developer.getProperties());
                            }
                        });
                    }
                }
            });
        }

        List<MavenArtifactRepository> repositories = new LinkedList<>();
        NamedDomainObjectList<MavenArtifactRepository> declared = project.getRepositories().withType(MavenArtifactRepository.class);
        for (MavenArtifactRepository repo : declared) {
            String name = repo.getName();
            if (name.contains(DEFAULT_MAVEN_CENTRAL_REPO_NAME)) {
                continue;
            }
            if (name.contains(DEFAULT_MAVEN_LOCAL_REPO_NAME)) {
                continue;
            }
            repositories.add(repo);
        }

        if (!repositories.isEmpty()) {
            pom.withXml(new Action<XmlProvider>() {
                @Override
                public void execute(XmlProvider xml) {
                    Node pom = xml.asNode();
                    List<Node> repositoryNodes = new LinkedList<>();
                    for (MavenArtifactRepository repository : repositories) {
                        Node n = new Node(null, "repository");
                        n.appendNode("id", repository.getName());
                        n.appendNode("url", repository.getUrl());
                        repositoryNodes.add(n);
                    }
                    pom.appendNode("repositories", repositoryNodes);
                }
            });
        }
    }
}
