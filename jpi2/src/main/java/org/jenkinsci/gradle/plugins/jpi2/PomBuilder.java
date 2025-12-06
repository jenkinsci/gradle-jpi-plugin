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
 * Action to update the POM file with the resolved dependencies and repositories.
 */
class PomBuilder implements Action<XmlProvider> {
    private final Configuration runtimeClasspath;
    private final Project project;

    public PomBuilder(Configuration runtimeClasspath, Project project) {
        this.runtimeClasspath = runtimeClasspath;
        this.project = project;
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

    @Override
    public void execute(@NotNull XmlProvider xmlProvider) {
        var resolvedDependencies = runtimeClasspath.getResolvedConfiguration()
                .getFirstLevelModuleDependencies();

        final var pom = "http://maven.apache.org/POM/4.0.0";

        final var originalDependencies = xmlProvider.asNode().getAt(new QName(pom, "dependencies"));
        final var dependencies = originalDependencies.isEmpty() ? xmlProvider.asNode().appendNode("dependencies") : (Node) originalDependencies.get(0);
        final var dependencyNodes = new ArrayList<Node>(dependencies.getAt(new QName(pom, "dependency")));
        final var dependencyManagement = xmlProvider.asNode().getAt(new QName(pom, "dependencyManagement"));
        if (!dependencyManagement.isEmpty()) {
            NodeList dmDependencies = dependencyManagement.getAt(new QName(pom, "dependencies"));
            dependencyNodes.addAll(dmDependencies.getAt(new QName(pom, "dependency")));
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
                var dependency = resolvedDependency.get();
                if (version.isPresent()) {
                    var versionNode = (Node) dependencyNode.getAt(new QName(pom, "version")).get(0);
                    dependencyNode.remove(versionNode);
                }
                dependencyNode.appendNode(new QName(pom, "version"), resolvedDependency.get().getModuleVersion());
            } else {
                System.err.println("Dependency not found: " + groupId + ":" + artifactId);
            }
        });

        final var originalRepositories = xmlProvider.asNode()
                .getAt(new QName(pom, "repositories"));
        var repositories = originalRepositories.isEmpty() ? xmlProvider.asNode().appendNode("repositories") : (Node) originalRepositories.get(0);

        project.getRepositories().forEach(it -> {
            if (it instanceof MavenArtifactRepository m) {
                var repository = repositories.appendNode("repository");
                repository.appendNode("id", it.getName());
                repository.appendNode("url", m.getUrl());
            }
        });
    }
}
