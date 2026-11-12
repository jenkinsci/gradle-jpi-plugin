package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class BundledLicenseMetadataIntegrationTest extends V2IntegrationTestBase {
    @Test
    void reportsOnlyLibrariesThatAreActuallyBundled() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                dependencies {
                    implementation("org.jenkins-ci.plugins:jackson2-api:2.19.0-404.vb_b_0fd2fea_e10")
                    implementation("com.auth0:java-jwt:4.5.0")
                }
                """);

        ith.gradleRunner()
                .withArguments("generateLicenseInfo", "--configuration-cache")
                .build();

        var report =
                Files.readString(ith.inProjectDir("build/licenses/licenses.xml").toPath());
        assertThat(report)
                .contains("artifactId=\"java-jwt\"")
                .doesNotContain(
                        "artifactId=\"jackson-databind\"",
                        "artifactId=\"jackson-core\"",
                        "artifactId=\"jackson-annotations\"");
    }

    @Test
    void doesNotDownloadPluginArchivesToGenerateLicenses() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        var module = Files.createDirectories(tempDir.toPath().resolve("repo/test/license/plugin/1"));
        Files.writeString(module.resolve("plugin-1.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>test.license</groupId>
                <artifactId>plugin</artifactId><version>1</version><packaging>hpi</packaging></project>
                """);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                repositories {
                    exclusiveContent {
                        forRepository { maven { url = uri("repo") } }
                        filter { includeGroup("test.license") }
                    }
                }
                dependencies { implementation("test.license:plugin:1") }
                """);

        ith.gradleRunner().withArguments("generateLicenseInfo").build();

        assertThat(ith.inProjectDir("build/licenses/licenses.xml")).exists();
    }

    @Test
    void doesNotDownloadDirectLibrariesProvidedByCore() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        var module = Files.createDirectories(tempDir.toPath().resolve("repo/test/license/provided/1"));
        Files.writeString(module.resolve("provided-1.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>test.license</groupId>
                <artifactId>provided</artifactId><version>1</version></project>
                """);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                repositories {
                    exclusiveContent {
                        forRepository { maven { url = uri("repo") } }
                        filter { includeGroup("test.license") }
                    }
                }
                dependencies {
                    implementation("test.license:provided:1")
                    add("jenkinsCore", "test.license:provided:1")
                }
                """);

        ith.gradleRunner().withArguments("generateLicenseInfo").build();

        assertThat(Files.readString(
                        ith.inProjectDir("build/licenses/licenses.xml").toPath()))
                .doesNotContain("artifactId=\"provided\"");
    }

    @Test
    void matchesArchiveSelectionForTransitiveCoreLibraries() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        MavenDependencyFixture.writeModule(tempDir.toPath(), "test.license", "provided", "jar", "");
        MavenDependencyFixture.writeModule(
                tempDir.toPath(),
                "test.license",
                "library",
                "jar",
                MavenDependencyFixture.dependency("test.license", "provided"));
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                repositories {
                    exclusiveContent {
                        forRepository { maven { url = uri("repo") } }
                        filter { includeGroup("test.license") }
                    }
                }
                dependencies {
                    implementation("test.license:library:1")
                    add("jenkinsCore", "test.license:provided:1")
                }
                """);
        ith.gradleRunner().withArguments("jpi").build();
        try (var archive = new JarFile(ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi"))) {
            var bundled = archive.getEntry("WEB-INF/lib/provided-1.jar") != null;
            assertThat(Files.readString(ith.inProjectDir("build/licenses/licenses.xml")
                                    .toPath())
                            .contains("artifactId=\"provided\""))
                    .isEqualTo(bundled);
        }
    }

    @Test
    void warnsWhenALibraryCannotBeResolved() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                repositories {
                    exclusiveContent {
                        forRepository { maven { url = uri("repo") } }
                        filter { includeGroup("test.license") }
                    }
                }
                dependencies { runtimeOnly("test.license:missing:1") }
                """);
        var result = ith.gradleRunner().withArguments("generateLicenseInfo").build();
        assertThat(result.getOutput()).contains("License metadata is incomplete", "test.license:missing:1");
    }

    @Test
    void doesNotSnapshotGeneratedLocalLibraries() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                dependencies {
                    implementation(fileTree(layout.buildDirectory.dir("generated-libs")))
                }
                """);
        var runner = ith.gradleRunner().withArguments("generateLicenseInfo", "--configuration-cache");
        runner.build();
        var directory = Files.createDirectories(tempDir.toPath().resolve("build/generated-libs"));
        try (var ignored = new JarOutputStream(Files.newOutputStream(directory.resolve("generated.jar")))) {}

        assertThat(runner.build().getOutput()).contains("Reusing configuration cache");
    }

    @Test
    void warnsWhenABundledLibraryHasNoPom() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        var module = Files.createDirectories(tempDir.toPath().resolve("repo/test/license/library/1"));
        try (var ignored = new JarOutputStream(Files.newOutputStream(module.resolve("library-1.jar")))) {}
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                repositories {
                    exclusiveContent {
                        forRepository {
                            maven {
                                url = uri("repo")
                                metadataSources { artifact() }
                            }
                        }
                        filter { includeGroup("test.license") }
                    }
                }
                dependencies { implementation("test.license:library:1") }
                """);

        var result = ith.gradleRunner().withArguments("generateLicenseInfo").build();

        assertThat(result.getOutput()).contains("License metadata is incomplete", "test.license:library:1");
        assertThat(ith.inProjectDir("build/licenses/licenses.xml")).exists();
    }
}
