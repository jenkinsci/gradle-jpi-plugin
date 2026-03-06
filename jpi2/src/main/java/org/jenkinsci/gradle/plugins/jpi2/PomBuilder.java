package org.jenkinsci.gradle.plugins.jpi2;

import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Action to update the POM file with resolved dependencies, repositories, plugin metadata,
 * developers, and licenses.
 */
class PomBuilder implements Action<XmlProvider> {
    private final Configuration runtimeClasspath;
    private final Project project;
    private final JenkinsPluginExtension extension;

    public PomBuilder(Configuration runtimeClasspath, Project project, JenkinsPluginExtension extension) {
        this.runtimeClasspath = runtimeClasspath;
        this.project = project;
        this.extension = extension;
    }

    private static Optional<String> getNodeElement(Node dependencyNode, String elementName) {
        return dependencyNode.getAt(new QName("http://maven.apache.org/POM/4.0.0", elementName))
                .stream().findFirst()
                .filter(it -> it instanceof Node)
                .map(it -> (((Node) it).value()))
                .filter(it -> it instanceof List)
                .flatMap(it -> ((List) it).stream().findFirst())
                .filter(it -> it instanceof String);
    }

    private static final String POM_NS = "http://maven.apache.org/POM/4.0.0";

    @Override
    public void execute(@NotNull XmlProvider xmlProvider) {
        var root = xmlProvider.asNode();
        resolveDependencyVersions(root);
        addRepositories(root);
        addDevelopers(root);
        addLicenses(root);
        fixPackaging(root);
    }

    private void resolveDependencyVersions(Node root) {
        var resolvedDependencies = runtimeClasspath.getResolvedConfiguration()
                .getFirstLevelModuleDependencies();

        final var originalDependencies = root.getAt(new QName(POM_NS, "dependencies"));
        final var dependencies = originalDependencies.isEmpty()
                ? root.appendNode("dependencies")
                : (Node) originalDependencies.get(0);
        final var dependencyNodes = new ArrayList<Node>(dependencies.getAt(new QName(POM_NS, "dependency")));
        final var dependencyManagement = root.getAt(new QName(POM_NS, "dependencyManagement"));
        if (!dependencyManagement.isEmpty()) {
            NodeList dmDependencies = dependencyManagement.getAt(new QName(POM_NS, "dependencies"));
            dependencyNodes.addAll(dmDependencies.getAt(new QName(POM_NS, "dependency")));
        }

        dependencyNodes.forEach(dependencyNode -> {
            var groupId = getNodeElement(dependencyNode, "groupId");
            var artifactId = getNodeElement(dependencyNode, "artifactId");
            var version = getNodeElement(dependencyNode, "version");

            assert groupId.isPresent();
            assert artifactId.isPresent();

            var resolvedDependency = resolvedDependencies.stream()
                    .filter(it -> it.getModuleGroup().equals(groupId.get()) && it.getModuleName().equals(artifactId.get()))
                    .findFirst();

            if (resolvedDependency.isPresent()) {
                if (version.isPresent()) {
                    var versionNode = (Node) dependencyNode.getAt(new QName(POM_NS, "version")).get(0);
                    dependencyNode.remove(versionNode);
                }
                dependencyNode.appendNode(new QName(POM_NS, "version"), resolvedDependency.get().getModuleVersion());
            } else {
                System.err.println("Dependency not found: " + groupId + ":" + artifactId);
            }
        });
    }

    private void addRepositories(Node root) {
        final var originalRepositories = root.getAt(new QName(POM_NS, "repositories"));
        var repositories = originalRepositories.isEmpty()
                ? root.appendNode("repositories")
                : (Node) originalRepositories.get(0);

        project.getRepositories().forEach(it -> {
            if (it instanceof MavenArtifactRepository m) {
                var repository = repositories.appendNode("repository");
                repository.appendNode("id", it.getName());
                repository.appendNode("url", m.getUrl());
            }
        });
    }

    private void addDevelopers(Node root) {
        var devs = extension.getPluginDevelopers().get();
        if (devs.isEmpty()) {
            return;
        }
        var developersNode = root.appendNode(new QName(POM_NS, "developers"));
        for (var dev : devs) {
            var developerNode = developersNode.appendNode(new QName(POM_NS, "developer"));
            appendIfPresent(developerNode, "id", dev.getId().getOrNull());
            appendIfPresent(developerNode, "name", dev.getName().getOrNull());
            appendIfPresent(developerNode, "email", dev.getEmail().getOrNull());
            appendIfPresent(developerNode, "url", dev.getUrl().getOrNull());
            appendIfPresent(developerNode, "organization", dev.getOrganization().getOrNull());
            appendIfPresent(developerNode, "organizationUrl", dev.getOrganizationUrl().getOrNull());
            appendIfPresent(developerNode, "timezone", dev.getTimezone().getOrNull());
            addDeveloperRoles(developerNode, dev);
            addDeveloperProperties(developerNode, dev);
        }
    }

    private void addDeveloperRoles(Node developerNode, PluginDeveloper dev) {
        var roles = dev.getRoles().get();
        if (roles.isEmpty()) {
            return;
        }
        var rolesNode = developerNode.appendNode(new QName(POM_NS, "roles"));
        for (var role : roles) {
            rolesNode.appendNode(new QName(POM_NS, "role"), role);
        }
    }

    private void addDeveloperProperties(Node developerNode, PluginDeveloper dev) {
        var properties = dev.getProperties().get();
        if (properties.isEmpty()) {
            return;
        }
        var propertiesNode = developerNode.appendNode(new QName(POM_NS, "properties"));
        for (var entry : properties.entrySet()) {
            propertiesNode.appendNode(new QName(POM_NS, entry.getKey()), entry.getValue());
        }
    }

    private void addLicenses(Node root) {
        var licenses = extension.getPluginLicenses().get();
        if (licenses.isEmpty()) {
            return;
        }
        var licensesNode = root.appendNode(new QName(POM_NS, "licenses"));
        for (var license : licenses) {
            var licenseNode = licensesNode.appendNode(new QName(POM_NS, "license"));
            appendIfPresent(licenseNode, "name", license.getName().getOrNull());
            appendIfPresent(licenseNode, "url", license.getUrl().getOrNull());
            appendIfPresent(licenseNode, "distribution", license.getDistribution().getOrNull());
            appendIfPresent(licenseNode, "comments", license.getComments().getOrNull());
        }
    }

    private void fixPackaging(Node root) {
        var packagingList = root.getAt(new QName(POM_NS, "packaging"));
        var packaging = extension.getArchiveExtension().get();
        if (!packagingList.isEmpty()) {
            ((Node) packagingList.get(0)).setValue(packaging);
        } else {
            root.appendNode(new QName(POM_NS, "packaging"), packaging);
        }
    }

    private static void appendIfPresent(Node parent, String name, String value) {
        if (value != null) {
            parent.appendNode(new QName(POM_NS, name), value);
        }
    }
}
