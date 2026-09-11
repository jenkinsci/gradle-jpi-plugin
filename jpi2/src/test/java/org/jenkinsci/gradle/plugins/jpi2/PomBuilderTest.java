package org.jenkinsci.gradle.plugins.jpi2;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PomBuilderTest {
    private static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>example-plugin</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>library</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.acme</groupId>
                            <artifactId>platform</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
            """;

    private Project project;
    private JenkinsPluginExtension extension;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().withName("example-plugin").build();
        extension = project.getObjects().newInstance(JenkinsPluginExtension.class, project);
    }

    @Test
    void writesResolvedDependencyVersionsAndRepositories() throws IOException, XmlPullParserException {
        var library = resolvedDependency("com.example", "library", "2.0.0");
        var platform = resolvedDependency("com.acme", "platform", "3.0.0");
        var runtimeClasspath = runtimeClasspath(library, platform);
        project.getRepositories().maven(repository -> {
            repository.setName("example");
            repository.setUrl("https://repo.example.com/releases");
        });

        var model = transform(runtimeClasspath);

        assertThat(model.getDependencies())
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("com.example", "library", "2.0.0"));
        assertThat(model.getDependencyManagement().getDependencies())
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("com.acme", "platform", "3.0.0"));
        assertThat(model.getRepositories())
                .extracting(Repository::getId, Repository::getUrl)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("example", "https://repo.example.com/releases"));
    }

    @Test
    void writesPluginMetadataAndPackaging() throws IOException, XmlPullParserException {
        extension.getArchiveExtension().set("hpi");
        var developer = project.getObjects().newInstance(PluginDeveloper.class);
        developer.getId().set("developer-id");
        developer.getName().set("Developer Name");
        developer.getEmail().set("developer@example.com");
        developer.getUrl().set("https://example.com/developer");
        developer.getOrganization().set("Example Organization");
        developer.getOrganizationUrl().set("https://example.com");
        developer.getTimezone().set("America/Los_Angeles");
        developer.getRoles().addAll("developer", "maintainer");
        developer.getProperties().put("chat", "developer-chat");
        extension.getPluginDevelopers().add(developer);

        var license = project.getObjects().newInstance(PluginLicense.class);
        license.getName().set("Apache License, Version 2.0");
        license.getUrl().set("https://www.apache.org/licenses/LICENSE-2.0");
        license.getDistribution().set("repo");
        license.getComments().set("Example license comment");
        extension.getPluginLicenses().add(license);

        var model = transform(runtimeClasspath(
                resolvedDependency("com.example", "library", "2.0.0"),
                resolvedDependency("com.example", "platform", "3.0.0")));

        assertThat(model.getPackaging()).isEqualTo("hpi");
        assertThat(model.getDevelopers()).singleElement().satisfies(PomBuilderTest::assertDeveloper);
        assertThat(model.getLicenses()).singleElement().satisfies(PomBuilderTest::assertLicense);
    }

    private static void assertDeveloper(Developer developer) {
        assertThat(developer.getId()).isEqualTo("developer-id");
        assertThat(developer.getName()).isEqualTo("Developer Name");
        assertThat(developer.getEmail()).isEqualTo("developer@example.com");
        assertThat(developer.getUrl()).isEqualTo("https://example.com/developer");
        assertThat(developer.getOrganization()).isEqualTo("Example Organization");
        assertThat(developer.getOrganizationUrl()).isEqualTo("https://example.com");
        assertThat(developer.getTimezone()).isEqualTo("America/Los_Angeles");
        assertThat(developer.getRoles()).containsExactlyInAnyOrder("developer", "maintainer");
        assertThat(developer.getProperties()).containsEntry("chat", "developer-chat");
    }

    private static void assertLicense(License license) {
        assertThat(license.getName()).isEqualTo("Apache License, Version 2.0");
        assertThat(license.getUrl()).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0");
        assertThat(license.getDistribution()).isEqualTo("repo");
        assertThat(license.getComments()).isEqualTo("Example license comment");
    }

    private Model transform(Configuration runtimeClasspath)
            throws IOException, XmlPullParserException {
        var transformer = new XmlTransformer();
        transformer.addAction(new PomBuilder(runtimeClasspath, project, extension, project.getLogger()));
        return new MavenXpp3Reader().read(new StringReader(transformer.transform(POM)));
    }

    private static Configuration runtimeClasspath(ResolvedDependency... dependencies) {
        var resolvedConfiguration = mock(ResolvedConfiguration.class);
        when(resolvedConfiguration.getFirstLevelModuleDependencies()).thenReturn(Set.of(dependencies));
        var runtimeClasspath = mock(Configuration.class);
        when(runtimeClasspath.getResolvedConfiguration()).thenReturn(resolvedConfiguration);
        return runtimeClasspath;
    }

    private static ResolvedDependency resolvedDependency(String group, String name, String version) {
        var dependency = mock(ResolvedDependency.class);
        when(dependency.getModuleGroup()).thenReturn(group);
        when(dependency.getModuleName()).thenReturn(name);
        when(dependency.getModuleVersion()).thenReturn(version);
        return dependency;
    }
}
