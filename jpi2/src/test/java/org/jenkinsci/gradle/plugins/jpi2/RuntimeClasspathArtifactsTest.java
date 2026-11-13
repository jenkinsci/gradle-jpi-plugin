package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jenkinsci.gradle.plugins.jpi2.MavenDependencyFixture.dependency;
import static org.jenkinsci.gradle.plugins.jpi2.MavenDependencyFixture.writeModule;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testfixtures.internal.ProjectBuilderImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class RuntimeClasspathArtifactsTest {
    @TempDir
    Path tempDir;

    private final List<Project> projects = new ArrayList<>();

    @AfterEach
    void closeProjects() {
        projects.forEach(ProjectBuilderImpl::stop);
    }

    @Test
    void preservesLocalJarDependenciesAndProducerTasks() throws Exception {
        var fixture = fixture("local");
        var local = Files.createFile(fixture.root().resolve("local.jar"));
        var producer = fixture.project().getTasks().register("produceLocalJar");
        fixture.runtime()
                .getDependencies()
                .add(fixture.project()
                        .getDependencies()
                        .create(fixture.project().files(local.toFile()).builtBy(producer)));
        var libraries = fixture.artifacts().getBundledLibraries();

        assertThat(libraries.getFiles()).containsExactly(local.toFile());
        assertThat(libraries.getBuildDependencies().getDependencies(null))
                .extracting(Task::getName)
                .contains("produceLocalJar");
    }

    @Test
    void resolvesLazilyWithoutCreatingDetachedConfigurations() throws Exception {
        var fixture = fixture("lazy");
        var count = fixture.project().getConfigurations().size();
        var libraries = fixture.artifacts().getBundledLibraries();
        assertThat(fixture.runtime().getState()).isEqualTo(Configuration.State.UNRESOLVED);

        libraries.getFiles();
        libraries.getFiles();

        assertThat(fixture.project().getConfigurations()).hasSize(count);
    }

    @TestFactory
    Stream<DynamicTest> omitsExcludedAndProvidedTransitiveLibraries() {
        return Stream.of("exclude", "core", "hpi", "jpi")
                .map(kind -> dynamicTest(kind, () -> {
                    var fixture = fixture(kind);
                    writeModule(fixture.root(), "example", "provided", "jar", "");
                    var providedDependency = dependency("example", "provided");
                    writeModule(fixture.root(), "example", "library", "jar", providedDependency);
                    fixture.project().getDependencies().add(fixture.runtime().getName(), "example:library:1");
                    if (kind.equals("exclude")) {
                        fixture.runtime().exclude(Map.of("group", "example", "module", "provided"));
                    } else if (kind.equals("core")) {
                        fixture.project().getDependencies().add(fixture.core().getName(), "example:provided:1");
                    } else {
                        writeModule(fixture.root(), "example", "plugin", kind, providedDependency);
                        Files.delete(fixture.root().resolve("repo/example/plugin/1/plugin-1." + kind));
                        fixture.project()
                                .getDependencies()
                                .add(fixture.runtime().getName(), "example:plugin:1");
                    }

                    assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                            .extracting(File::getName)
                            .containsExactly("library-1.jar");
                }));
    }

    @Test
    void distinguishesModulesWithIdenticalArtifactNames() throws Exception {
        var fixture = fixture("identity");
        writeModule(fixture.root(), "provided", "same", "jar", "");
        writeModule(fixture.root(), "bundled", "same", "jar", "");
        writeModule(fixture.root(), "example", "plugin", "hpi", dependency("provided", "same"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:plugin:1");
        fixture.project().getDependencies().add(fixture.runtime().getName(), "bundled:same:1");

        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .containsExactly(
                        fixture.root().resolve("repo/bundled/same/1/same-1.jar").toFile());
    }

    @Test
    void retainsNonCoreDescendantsReachedThroughBundledLibrary() throws Exception {
        var fixture = fixture("core-exclusion");
        writeModule(fixture.root(), "example", "extra", "jar", "");
        writeModule(fixture.root(), "example", "core-library", "jar", dependency("example", "extra"));
        writeModule(fixture.root(), "example", "library", "jar", dependency("example", "core-library"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:library:1");
        fixture.project().getDependencies().add(fixture.core().getName(), "example:core-library:1");
        fixture.core().exclude(Map.of("group", "example", "module", "extra"));

        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .extracting(File::getName)
                .containsExactlyInAnyOrder("library-1.jar", "extra-1.jar");
    }

    @Test
    void retainsNonCoreDescendantsOfDirectCoreRoots() throws Exception {
        var fixture = fixture("direct-core-root");
        writeModule(fixture.root(), "example", "extra", "jar", "");
        writeModule(fixture.root(), "example", "core-library", "jar", dependency("example", "extra"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:core-library:1");
        fixture.project().getDependencies().add(fixture.core().getName(), "example:core-library:1");
        fixture.core().exclude(Map.of("group", "example", "module", "extra"));

        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .extracting(File::getName)
                .containsExactly("extra-1.jar");
        assertThat(fixture.artifacts().getBundledModules())
                .extracting(ModuleComponentIdentifier::getModule)
                .containsExactly("extra");
    }

    @Test
    void usesTheConsumersSelectedNestedPluginVersion() throws Exception {
        var fixture = fixture("nested-plugin-upgrade");
        writeModule(fixture.root(), "example", "extra", "jar", "");
        writeModule(fixture.root(), "example", "nested-plugin", "hpi", dependency("example", "extra"));
        writeModule(fixture.root(), "example", "nested-plugin", "2", "hpi", "");
        writeModule(fixture.root(), "example", "plugin", "hpi", dependency("example", "nested-plugin"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:plugin:1");
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:nested-plugin:2");
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:extra:1");

        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .extracting(File::getName)
                .containsExactly("extra-1.jar");
        assertThat(fixture.artifacts().getBundledModules())
                .extracting(ModuleComponentIdentifier::getModule)
                .containsExactly("extra");
    }

    @Test
    void ignoresUnavailableUpstreamArtifactsExcludedByTheConsumer() throws Exception {
        var fixture = fixture("unavailable-provided-artifact");
        writeModule(fixture.root(), "example", "unavailable", "jar", "");
        Files.delete(fixture.root().resolve("repo/example/unavailable/1/unavailable-1.jar"));
        writeModule(fixture.root(), "example", "plugin", "hpi", dependency("example", "unavailable"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:plugin:1");
        fixture.runtime().exclude(Map.of("group", "example", "module", "unavailable"));
        var local = Files.createFile(fixture.root().resolve("bundled.jar"));
        fixture.project()
                .getDependencies()
                .add(fixture.runtime().getName(), fixture.project().files(local.toFile()));

        assertThat(fixture.artifacts().getBundledLibraries().getFiles()).containsExactly(local.toFile());
    }

    @TestFactory
    Stream<DynamicTest> failsForUnavailableConsumerLibraries() {
        return Stream.of("metadata", "artifact")
                .map(kind -> dynamicTest(kind, () -> {
                    var fixture = fixture("unavailable-consumer-" + kind);
                    if (kind.equals("artifact")) {
                        writeModule(fixture.root(), "example", "unavailable", "jar", "");
                        Files.delete(fixture.root().resolve("repo/example/unavailable/1/unavailable-1.jar"));
                    }
                    fixture.project().getDependencies().add(fixture.runtime().getName(), "example:unavailable:1");

                    assertThatThrownBy(() ->
                                    fixture.artifacts().getBundledLibraries().getFiles())
                            .hasStackTraceContaining("unavailable");
                }));
    }

    @TestFactory
    Stream<DynamicTest> retainsExternalClassifiersNotSuppliedByProviders() {
        return Stream.of("plugin", "core")
                .map(provider -> dynamicTest(provider, () -> {
                    var fixture = fixture("provided-main-artifact-" + provider);
                    writeModule(fixture.root(), "example", "shared", "jar", "");
                    Files.copy(
                            fixture.root().resolve("repo/example/shared/1/shared-1.jar"),
                            fixture.root().resolve("repo/example/shared/1/shared-1-extra.jar"));
                    if (provider.equals("plugin")) {
                        writeModule(fixture.root(), "example", "plugin", "hpi", dependency("example", "shared"));
                        fixture.project()
                                .getDependencies()
                                .add(fixture.runtime().getName(), "example:plugin:1");
                    } else {
                        fixture.project().getDependencies().add(fixture.core().getName(), "example:shared:1");
                    }
                    fixture.project().getDependencies().add(fixture.runtime().getName(), "example:shared:1:extra");

                    assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                            .extracting(File::getName)
                            .containsExactly("shared-1-extra.jar");
                    assertThat(fixture.artifacts().getBundledModules())
                            .extracting(ModuleComponentIdentifier::getModule)
                            .containsExactly("shared");
                }));
    }

    @Test
    void retainsDirectLibraryNoLongerSuppliedBySelectedNestedPlugin() throws Exception {
        var fixture = fixture("nested-plugin-direct-library");
        writeModule(fixture.root(), "example", "extra", "jar", "");
        writeModule(fixture.root(), "example", "nested-plugin", "hpi", dependency("example", "extra"));
        writeModule(fixture.root(), "example", "nested-plugin", "2", "hpi", "");
        writeModule(
                fixture.root(),
                "example",
                "plugin",
                "hpi",
                dependency("example", "nested-plugin") + dependency("example", "extra"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:plugin:1");
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:nested-plugin:2");
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:extra:1");

        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .extracting(File::getName)
                .containsExactly("extra-1.jar");
        assertThat(fixture.artifacts().getBundledModules())
                .extracting(ModuleComponentIdentifier::getModule)
                .containsExactly("extra");
    }

    @Test
    void retainsLibrariesExcludedByPublishedPlugins() throws Exception {
        var fixture = fixture("published-plugin-exclusion");
        writeModule(fixture.root(), "example", "extra", "jar", "");
        writeModule(fixture.root(), "example", "shared", "jar", dependency("example", "extra"));
        writeModule(fixture.root(), "example", "plugin", "hpi", """
                <dependency><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                <exclusions><exclusion><groupId>example</groupId><artifactId>extra</artifactId></exclusion></exclusions>
                </dependency>
                """);
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:plugin:1");
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:shared:1");

        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .extracting(File::getName)
                .containsExactly("extra-1.jar");
        assertThat(fixture.artifacts().getBundledModules())
                .extracting(ModuleComponentIdentifier::getModule)
                .containsExactly("extra");
    }

    @Test
    void declaresPluginsReachedThroughBundledLibraryRoots() throws Exception {
        var fixture = fixture("transitive-plugin");
        fixture.runtime()
                .getAttributes()
                .attribute(
                        ArtifactType.ARTIFACT_TYPE_ATTRIBUTE,
                        fixture.project().getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));
        writeModule(fixture.root(), "example", "provided", "jar", "");
        writeModule(fixture.root(), "example", "plugin", "hpi", dependency("example", "provided"));
        writeModule(fixture.root(), "example", "library", "jar", dependency("example", "plugin"));
        fixture.project().getDependencies().add(fixture.runtime().getName(), "example:library:1");

        assertThat(V2JpiPlugin.resolvePluginDependencies(fixture.runtime())).isEqualTo("plugin:1");
        assertThat(fixture.artifacts().getBundledLibraries().getFiles())
                .extracting(File::getName)
                .containsExactly("library-1.jar");
    }

    @TestFactory
    Stream<DynamicTest> declaresPluginsWhenJarAndArchiveVariantsAreSelectedInEitherOrder() {
        return Stream.of(true, false)
                .map(jarFirst -> dynamicTest("jarFirst=" + jarFirst, () -> {
                    var fixture = fixture("variant-order-" + jarFirst);
                    fixture.runtime()
                            .getAttributes()
                            .attribute(
                                    ArtifactType.ARTIFACT_TYPE_ATTRIBUTE,
                                    fixture.project().getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));
                    writeModule(fixture.root(), "example", "plugin", "hpi", "");
                    var jar = (ExternalModuleDependency)
                            fixture.project().getDependencies().create("example:plugin:1");
                    jar.attributes(attributes -> attributes.attribute(
                            ArtifactType.ARTIFACT_TYPE_ATTRIBUTE,
                            fixture.project().getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR)));
                    var archive = fixture.project().getDependencies().create("example:plugin:1");
                    fixture.runtime().getDependencies().add(jarFirst ? jar : archive);
                    fixture.runtime().getDependencies().add(jarFirst ? archive : jar);

                    assertThat(V2JpiPlugin.resolvePluginDependencies(fixture.runtime()))
                            .isEqualTo("plugin:1");
                }));
    }

    private Fixture fixture(String name) throws IOException {
        var root = Files.createDirectory(tempDir.resolve(name));
        var project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        projects.add(project);
        project.getRepositories()
                .maven(repository -> repository.setUrl(root.resolve("repo").toUri()));
        project.getDependencies().getComponents().all(HpiMetadataRule.class);
        var runtime = project.getConfigurations().create("reviewedRuntime");
        runtime.getAttributes()
                .attribute(
                        ArtifactType.ARTIFACT_TYPE_ATTRIBUTE,
                        project.getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));
        var core = project.getConfigurations().create("reviewedCore");
        return new Fixture(root, project, runtime, core, new RuntimeClasspathArtifacts(project, runtime, core));
    }

    private record Fixture(
            Path root,
            Project project,
            Configuration runtime,
            Configuration core,
            RuntimeClasspathArtifacts artifacts) {}
}
