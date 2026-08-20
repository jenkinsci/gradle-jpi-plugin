package org.jenkinsci.gradle.plugins.jpi2;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Action to update the POM file with resolved dependencies, repositories, plugin metadata,
 * developers, and licenses.
 */
class PomBuilder implements Action<XmlProvider> {
    private final Configuration runtimeClasspath;
    private final Project project;
    private final JenkinsPluginExtension extension;
    private final Logger logger;

    public PomBuilder(Configuration runtimeClasspath, Project project, JenkinsPluginExtension extension, Logger logger) {
        this.runtimeClasspath = runtimeClasspath;
        this.project = project;
        this.extension = extension;
        this.logger = logger;
    }

    private static Optional<String> getNodeElement(Element dependencyNode, String elementName) {
        return firstChild(dependencyNode, elementName)
                .map(Node::getTextContent)
                .filter(value -> !value.isBlank());
    }

    private static final String POM_NS = "http://maven.apache.org/POM/4.0.0";

    @Override
    public void execute(@NotNull XmlProvider xmlProvider) {
        var root = xmlProvider.asElement();
        resolveDependencyVersions(root);
        addRepositories(root);
        addDevelopers(root);
        addLicenses(root);
        fixPackaging(root);
    }

    private void resolveDependencyVersions(Element root) {
        var resolvedDependencies = runtimeClasspath.getResolvedConfiguration()
                .getFirstLevelModuleDependencies();

        final var dependencies = firstChildOrAppend(root, "dependencies");
        final var dependencyNodes = new ArrayList<Element>();
        childElements(dependencies, "dependency").forEach(dependencyNodes::add);
        final var dependencyManagement = firstChild(root, "dependencyManagement");
        dependencyManagement.ifPresent(dm -> {
            var dmDependencies = firstChild(dm, "dependencies");
            dmDependencies.ifPresent(element -> childElements(element, "dependency").forEach(dependencyNodes::add));
        });

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
                    firstChild(dependencyNode, "version")
                            .ifPresent(dependencyNode::removeChild);
                }
                appendChildElement(dependencyNode, "version", resolvedDependency.get().getModuleVersion());
            } else {
                logger.warn("Dependency not found: {}:{}", groupId, artifactId);
            }
        });
    }

    private void addRepositories(Element root) {
        var repositories = firstChildOrAppend(root, "repositories");

        project.getRepositories().forEach(it -> {
            if (it instanceof MavenArtifactRepository m) {
                var repository = appendChildElement(repositories, "repository");
                appendChildElement(repository, "id", it.getName());
                appendChildElement(repository, "url", m.getUrl().toString());
            }
        });
    }

    private void addDevelopers(Element root) {
        var devs = extension.getPluginDevelopers().get();
        if (devs.isEmpty()) {
            return;
        }
        var developersNode = appendChildElement(root, "developers");
        for (var dev : devs) {
            var developerNode = appendChildElement(developersNode, "developer");
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

    private void addDeveloperRoles(Element developerNode, PluginDeveloper dev) {
        var roles = dev.getRoles().get();
        if (roles.isEmpty()) {
            return;
        }
        var rolesNode = appendChildElement(developerNode, "roles");
        for (var role : roles) {
            appendChildElement(rolesNode, "role", role);
        }
    }

    private void addDeveloperProperties(Element developerNode, PluginDeveloper dev) {
        var properties = dev.getProperties().get();
        if (properties.isEmpty()) {
            return;
        }
        var propertiesNode = appendChildElement(developerNode, "properties");
        for (var entry : properties.entrySet()) {
            appendChildElement(propertiesNode, entry.getKey(), entry.getValue());
        }
    }

    private void addLicenses(Element root) {
        var licenses = extension.getPluginLicenses().get();
        if (licenses.isEmpty()) {
            return;
        }
        var licensesNode = appendChildElement(root, "licenses");
        for (var license : licenses) {
            var licenseNode = appendChildElement(licensesNode, "license");
            appendIfPresent(licenseNode, "name", license.getName().getOrNull());
            appendIfPresent(licenseNode, "url", license.getUrl().getOrNull());
            appendIfPresent(licenseNode, "distribution", license.getDistribution().getOrNull());
            appendIfPresent(licenseNode, "comments", license.getComments().getOrNull());
        }
    }

    private void fixPackaging(Element root) {
        var packaging = extension.getArchiveExtension().get();
        var packagingNode = firstChild(root, "packaging");
        if (packagingNode.isPresent()) {
            packagingNode.get().setTextContent(packaging);
        } else {
            appendChildElement(root, "packaging", packaging);
        }
    }

    private static void appendIfPresent(Element parent, String name, String value) {
        if (value != null) {
            appendChildElement(parent, name, value);
        }
    }

    private static Optional<Element> firstChild(Element parent, String childName) {
        return childElements(parent, childName).findFirst();
    }

    private static Stream<Element> childElements(Element parent, String childName) {
        NodeList childNodes = parent.getChildNodes();
        return IntStream.range(0, childNodes.getLength())
                .mapToObj(childNodes::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(element -> matches(element, childName));
    }

    private static Element firstChildOrAppend(Element parent, String childName) {
        return firstChild(parent, childName).orElseGet(() -> appendChildElement(parent, childName));
    }

    private static Element appendChildElement(Element parent, String childName) {
        return appendChildElement(parent, childName, null);
    }

    private static Element appendChildElement(Element parent, String childName, String value) {
        Document document = parent.getOwnerDocument();
        Element child = document.createElementNS(POM_NS, childName);
        if (value != null) {
            child.setTextContent(value);
        }
        parent.appendChild(child);
        return child;
    }

    private static boolean matches(Element element, String childName) {
        var localName = element.getLocalName();
        if (localName != null) {
            return POM_NS.equals(element.getNamespaceURI()) && childName.equals(localName);
        }
        return childName.equals(element.getTagName());
    }
}
