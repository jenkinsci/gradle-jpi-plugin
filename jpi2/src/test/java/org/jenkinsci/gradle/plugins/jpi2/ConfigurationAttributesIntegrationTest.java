package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class ConfigurationAttributesIntegrationTest extends V2IntegrationTestBase {
    @Test
    void runtimeResolversSelectRuntimeVariantsFromGradleMetadata() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        MavenDependencyFixture.writeModule(tempDir.toPath(), "org.jenkins-ci.main", "jenkins-core", "jar", "");
        MavenDependencyFixture.writeModule(tempDir.toPath(), "org.jenkins-ci.main", "jenkins-war", "war", "");
        var metadata = """
                {
                  "formatVersion": "1.1",
                  "component": {"group": "test.attributes", "module": "library", "version": "1"},
                  "variants": [
                    {
                      "name": "apiElements",
                      "attributes": {"org.gradle.category": "library", "org.gradle.usage": "java-api"},
                      "files": [{"name": "api.jar", "url": "missing-api.jar"}]
                    },
                    {
                      "name": "runtimeElements",
                      "attributes": {"org.gradle.category": "library", "org.gradle.usage": "java-runtime"},
                      "files": [{"name": "library-1.jar", "url": "library-1.jar"}]
                    }
                  ]
                }
                """;
        for (var name : new String[] {"library", "bundled"}) {
            MavenDependencyFixture.writeModule(tempDir.toPath(), "test.attributes", name, "jar", "");
            Files.writeString(
                    tempDir.toPath().resolve("repo/test/attributes/" + name + "/1/" + name + "-1.module"),
                    metadata.replace("\"module\": \"library\"", "\"module\": \"" + name + "\"")
                            .replace("library-1.jar", name + "-1.jar"));
        }
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                plugins { id("org.jenkins-ci.jpi2") }
                repositories {
                    maven {
                        url = uri("repo")
                        metadataSources { gradleMetadata(); mavenPom() }
                    }
                }
                jenkinsPlugin { jenkinsVersion = "1" }
                dependencies {
                    add("jenkinsCore", "test.attributes:library:1")
                    implementation("test.attributes:library:1")
                    implementation("test.attributes:bundled:1")
                    add("serverTaskClasspath", "test.attributes:library:1")
                }
                // Isolate the resolvers from the unrelated test harness dependencies.
                configurations.named("testImplementation") { dependencies.clear() }
                configurations.named("testRuntimeOnly") { dependencies.clear() }
                tasks.register("resolveRuntimeVariants") {
                    doLast {
                        for (name in listOf("jenkinsCore", "defaultRuntime", "testDefaultRuntime", "serverTaskClasspath")) {
                            val configuration = configurations.getByName(name)
                            check(configuration.files.any { it.name == "library-1.jar" })
                            val library = configuration.incoming.resolutionResult.allDependencies
                                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                                .first { it.selected.moduleVersion?.group == "test.attributes" }
                            check(library.resolvedVariant.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name == Usage.JAVA_RUNTIME)
                        }
                        check(configurations.getByName("serverTaskClasspath").files.any { it.extension == "war" })
                        // HPL library selection uses a separate, detached configuration.
                        val hpl = tasks.named<org.jenkinsci.gradle.plugins.jpi2.GenerateHplTask>("generateJenkinsServerHpl").get()
                        check(hpl.libraries.files.any { it.name == "bundled-1.jar" })
                    }
                }
                """);

        var result = ith.gradleRunner().withArguments("resolveRuntimeVariants").build();

        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }
}
