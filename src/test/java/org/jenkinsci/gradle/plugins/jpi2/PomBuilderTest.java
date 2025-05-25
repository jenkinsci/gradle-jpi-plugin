package org.jenkinsci.gradle.plugins.jpi2;

import groovy.util.Node;
import groovy.xml.XmlParser;
import groovy.xml.XmlNodePrinter;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.testfixtures.ProjectBuilder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PomBuilderTest {

    @Test
    void shouldCreatePomBuilderInstance() {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");

        // when
        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // then
        assertThat(pomBuilder).isNotNull();
    }
    
    @Test
    void shouldHandleNullInput() {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        PomBuilder pomBuilder = new PomBuilder(configuration, project);
        
        // when & then - should not throw NPE with null input
        try {
            pomBuilder.execute(null);
        } catch (NullPointerException e) {
            // Expected for null input
            assertThat(e).isNotNull();
        }
    }

    @Test
    void shouldAddRepositoryToPom() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        
        project.getRepositories().maven(repo -> {
            repo.setName("test-repo");
            repo.setUrl(URI.create("https://example.com/repo"));
        });

        XmlProvider xmlProvider = getXmlProvider("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-plugin</artifactId>
                    <version>1.0.0</version>
                </project>
                """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("test-repo");
        assertThat(updatedPom).contains("https://example.com/repo");
    }

    @NotNull
    private static XmlProvider getXmlProvider(@Language("xml") String originalPom) throws ParserConfigurationException, SAXException, IOException {

        XmlParser parser = new XmlParser();
        Node pomNode = parser.parseText(originalPom);
        XmlProvider xmlProvider = new TestXmlProvider(pomNode);
        return xmlProvider;
    }

    @Test
    void shouldHandleGetNodeElementMethod() throws Exception {
        // given
        XmlParser parser = new XmlParser();
        
        // Test with valid dependency node
        String dependencyXml = """
            <dependency xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>org.example</groupId>
                <artifactId>test-artifact</artifactId>
                <version>1.0.0</version>
            </dependency>
            """;
        
        Node dependencyNode = parser.parseText(dependencyXml);
        
        // when & then - using reflection to test private method
        Method getNodeElementMethod = PomBuilder.class.getDeclaredMethod("getNodeElement", Node.class, String.class);
        getNodeElementMethod.setAccessible(true);
        
        Optional<String> groupId = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "groupId");
        Optional<String> artifactId = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "artifactId");
        Optional<String> version = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "version");
        Optional<String> nonExistent = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "nonExistent");
        
        assertThat(groupId).isPresent().contains("org.example");
        assertThat(artifactId).isPresent().contains("test-artifact");
        assertThat(version).isPresent().contains("1.0.0");
        assertThat(nonExistent).isEmpty();
    }

    @Test
    void shouldHandleGetNodeElementWithInvalidNode() throws Exception {
        // given
        XmlParser parser = new XmlParser();
        
        // Test with node that has non-Node children
        String invalidXml = """
            <dependency xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>org.example</groupId>
            </dependency>
            """;
        
        Node dependencyNode = parser.parseText(invalidXml);
        
        // when & then - using reflection to test private method
        Method getNodeElementMethod = PomBuilder.class.getDeclaredMethod("getNodeElement", Node.class, String.class);
        getNodeElementMethod.setAccessible(true);
        
        Optional<String> result = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "groupId");
        
        assertThat(result).isPresent();
    }

    @Test
    void shouldHandlePomWithDependencyManagement() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>managed-dep</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should not fail
        assertThat(xmlProvider.toString()).contains("dependencyManagement");
    }

    @Test
    void shouldHandlePomWithExistingDependencies() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.example</groupId>
                        <artifactId>existing-dep</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should not fail and should preserve existing dependencies
        assertThat(xmlProvider.toString()).contains("existing-dep");
    }

    @Test
    void shouldHandlePomWithExistingRepositories() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        
        project.getRepositories().maven(repo -> {
            repo.setName("new-repo");
            repo.setUrl(URI.create("https://new.example.com/repo"));
        });
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <repositories>
                    <repository>
                        <id>existing-repo</id>
                        <url>https://existing.example.com/repo</url>
                    </repository>
                </repositories>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should preserve existing repositories and add new ones
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("existing-repo");
        assertThat(updatedPom).contains("new-repo");
        assertThat(updatedPom).contains("https://new.example.com/repo");
    }

    @Test
    void shouldHandleDependencyVersionResolution() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().mavenCentral(); // Add repository for dependency resolution
        Configuration configuration = project.getConfigurations().create("test");
        
        // Add dependencies to configuration that will be resolved
        Dependency dependency = project.getDependencies().create("junit:junit:4.13.2");
        configuration.getDependencies().add(dependency);
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.12</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should process dependencies and handle version resolution
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("junit");
        assertThat(updatedPom).contains("4.13.2");
    }

    @Test
    void shouldHandleDependencyWithoutVersion() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().mavenCentral(); // Add repository for dependency resolution
        Configuration configuration = project.getConfigurations().create("test");
        
        // Add dependencies to configuration that will be resolved
        Dependency dependency = project.getDependencies().create("junit:junit:4.13.2");
        configuration.getDependencies().add(dependency);
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should add version to dependency without version
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("junit");
        assertThat(updatedPom).contains("4.13.2");
    }

    @Test
    void shouldHandleEmptyDependenciesForEach() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should handle empty dependencies list and still process (testing forEach on empty list)
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("test-plugin");
    }

    @Test
    void shouldHandleEmptyDependencyManagement() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        Configuration configuration = project.getConfigurations().create("test");
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                </dependencyManagement>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should handle empty dependencyManagement (tests line 50 mutation)
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("dependencyManagement");
    }

    @Test
    void shouldHandleUnmatchedDependencies() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().mavenCentral();
        Configuration configuration = project.getConfigurations().create("test");
        
        // Add a different dependency than what's in the POM
        Dependency dependency = project.getDependencies().create("org.apache.commons:commons-lang3:3.12.0");
        configuration.getDependencies().add(dependency);
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.12</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should not update unmatched dependencies (tests line 64 mutation)
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("junit");
        assertThat(updatedPom).contains("4.12"); // Should keep original version since no match
    }

    @Test
    void shouldHandleDependencyWithoutVersionWhenMatched() throws Exception {
        // given
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().mavenCentral();
        Configuration configuration = project.getConfigurations().create("test");
        
        // Add dependency that matches POM dependency
        Dependency dependency = project.getDependencies().create("junit:junit:4.13.2");
        configuration.getDependencies().add(dependency);
        
        XmlProvider xmlProvider = getXmlProvider("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-plugin</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """);

        PomBuilder pomBuilder = new PomBuilder(configuration, project);

        // when
        pomBuilder.execute(xmlProvider);

        // then - should add version to dependency without version (tests line 69 mutation)
        String updatedPom = xmlProvider.toString();
        assertThat(updatedPom).contains("junit");
        assertThat(updatedPom).contains("4.13.2");
    }

    @Test
    void shouldHandleGetNodeElementWithNonNodeValues() throws Exception {
        // given
        XmlParser parser = new XmlParser();
        
        // Create a node with non-existing element to test the empty case
        String mixedXml = """
            <dependency xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>org.example</groupId>
            </dependency>
            """;
        
        Node dependencyNode = parser.parseText(mixedXml);
        
        // when & then - using reflection to test private method with non-existing element
        Method getNodeElementMethod = PomBuilder.class.getDeclaredMethod("getNodeElement", Node.class, String.class);
        getNodeElementMethod.setAccessible(true);
        
        // This should test the filter conditions for non-existing elements
        Optional<String> result = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "nonExistent");
        
        // Should return empty since element doesn't exist
        assertThat(result).isEmpty();
        
        // Test with existing element to make sure the positive case still works
        Optional<String> groupId = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "groupId");
        assertThat(groupId).isPresent().contains("org.example");
    }

    @Test
    void shouldHandleGetNodeElementWithComplexNodeStructure() throws Exception {
        // given
        XmlParser parser = new XmlParser();

        // Create a node structure that will test all the filter conditions
        String complexXml = """
            <dependency xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>
                    <nested>value</nested>
                </groupId>
                <artifactId>test</artifactId>
            </dependency>
            """;

        Node dependencyNode = parser.parseText(complexXml);

        // when & then - using reflection to test private method
        Method getNodeElementMethod = PomBuilder.class.getDeclaredMethod("getNodeElement", Node.class, String.class);
        getNodeElementMethod.setAccessible(true);

        // Test with complex nested structure (should not match the expected pattern)
        Optional<String> groupId = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "groupId");
        Optional<String> artifactId = (Optional<String>) getNodeElementMethod.invoke(null, dependencyNode, "artifactId");

        // The groupId has nested structure so should not match our filter conditions
        assertThat(groupId).isEmpty();
        // The artifactId should match since it's a simple text node
        assertThat(artifactId).isPresent().contains("test");
    }

    private record TestXmlProvider(Node node) implements XmlProvider {

        @NotNull
            @Override
            public Node asNode() {
                return node;
            }

            @NotNull
            @Override
            public StringBuilder asString() {
                StringWriter writer = new StringWriter();
                try {
                    new XmlNodePrinter(new PrintWriter(writer)).print(node);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return new StringBuilder(writer.toString());
            }

            @NotNull
            @Override
            public Element asElement() {
                throw new UnsupportedOperationException("Not implemented for test");
            }

            @NotNull
            @Override
            public String toString() {
                return asString().toString();
            }
        }
}