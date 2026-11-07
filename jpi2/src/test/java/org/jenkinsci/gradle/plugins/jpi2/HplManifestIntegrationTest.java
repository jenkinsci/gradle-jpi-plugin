package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class HplManifestIntegrationTest extends V2IntegrationTestBase {
    @Test
    void hplGenerationAvoidsPackagingAndReusesConfigurationCache() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());
        var runner = ith.gradleRunner().withArguments("prepareRun", "--configuration-cache");

        var first = runner.build();

        for (var task : new String[] {"jar", "jpi", "explodedJpi", "generateLicenseInfo"}) {
            assertThat(first.task(":" + task)).as(task).isNull();
        }
        assertThat(ith.inProjectDir("work/plugins/test-plugin.hpl")).exists();
        assertThat(ith.inProjectDir("build/libs")).doesNotExist();
        var second = runner.build();
        assertThat(second.getOutput()).contains("Reusing configuration cache");
        assertThat(second.task(":" + GenerateJenkinsManifestTask.NAME).getOutcome())
                .isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void hplJarAndJpiSharePluginMetadata() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                dependencies { implementation("org.jenkins-ci.plugins:git:5.7.0") }
                tasks.named<War>("jpi") {
                    manifest.attributes("Provider-Header" to providers.provider { "provider-value" })
                    manifest.from("first.mf")
                    manifest.from("custom.mf") {
                        eachEntry {
                            if (key == "Long-Name") value = "$baseValue/$mergeValue"
                            if (key == "Url") exclude()
                        }
                    }
                }
                jenkinsPlugin {
                    pluginId.set("custom-id")
                    displayName.set("Custom plugin")
                    homePage.set(uri("https://example.com/plugin"))
                }
                """);
        Files.writeString(
                ith.inProjectDir("first.mf").toPath(), "Manifest-Version: 1.0\nPlugin-Class: example.FirstPlugin\n\n");
        Files.writeString(
                ith.inProjectDir("custom.mf").toPath(),
                "Manifest-Version: 1.0\nPlugin-Class: example.CustomPlugin\nLong-Name: imported\nUrl: https://example.com/imported\n\nName: example/\nSection-Header: section-value\n\n");
        var runner = ith.gradleRunner();
        runner.withArguments("prepareRun", "--configuration-cache").build();
        assertThat(runner.build().getOutput()).contains("Reusing configuration cache");
        var hplFile = ith.inProjectDir("build/hpl/custom-id.hpl");
        Manifest hpl;
        try (var input = Files.newInputStream(hplFile.toPath())) {
            hpl = new Manifest(input);
        }
        assertThat(hpl.getMainAttributes().getValue("Provider-Header")).isEqualTo("provider-value");
        assertThat(hpl.getMainAttributes().getValue("Long-Name")).isEqualTo("Custom plugin/imported");
        assertThat(hpl.getMainAttributes().getValue("Url")).isNull();
        assertThat(hpl.getMainAttributes().getValue("Plugin-Class")).isEqualTo("example.CustomPlugin");
        assertThat(hpl.getMainAttributes().getValue("Plugin-Dependencies")).isEqualTo("git:5.7.0");

        Files.writeString(
                ith.inProjectDir("custom.mf").toPath(),
                "Manifest-Version: 1.0\nPlugin-Class: example.UpdatedPlugin\nLong-Name: imported\nUrl: https://example.com/imported\n\nName: example/\nSection-Header: updated-section\n\n");
        runner.build();
        try (var input = Files.newInputStream(hplFile.toPath())) {
            hpl = new Manifest(input);
        }
        assertThat(hpl.getMainAttributes().getValue("Plugin-Class")).isEqualTo("example.UpdatedPlugin");
        runner.withArguments("jpi", "--configuration-cache").build();
        assertThat(runner.build().getOutput()).contains("Reusing configuration cache");
        try (var jar = new JarFile(ith.inProjectDir("build/libs/test-plugin-1.0.0.jar"));
                var jpi = new JarFile(ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi"))) {
            assertThat(hpl.getEntries().get("example/").getValue("Section-Header"))
                    .isEqualTo("updated-section");
            assertThat(jpi.getManifest().getEntries()).isEqualTo(hpl.getEntries());
            assertThat(jar.getManifest().getEntries()).isEqualTo(hpl.getEntries());
            var attributes = jar.getManifest().getMainAttributes();
            assertThat(jpi.getManifest().getMainAttributes()).isEqualTo(attributes);
            hpl.getMainAttributes().remove(new Attributes.Name("Libraries"));
            hpl.getMainAttributes().remove(new Attributes.Name("Resource-Path"));
            assertThat(hpl.getMainAttributes()).isEqualTo(attributes);
        }
    }

    @Test
    void resolvesProviderHeadersWithoutUserManifestImports() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                tasks.named<War>("jpi") {
                    manifest.attributes(
                        "Plugin-Class" to providers.provider { "example.ProviderPlugin" },
                        "Absent-Header" to providers.provider<String> { null })
                    manifest.attributes(mapOf("Section-Header" to providers.provider { "section-value" }), "example/")
                }
                """);
        ith.gradleRunner().withArguments("prepareRun", "--configuration-cache").build();
        try (var input = Files.newInputStream(
                ith.inProjectDir("build/hpl/test-plugin.hpl").toPath())) {
            var manifest = new Manifest(input);
            assertThat(manifest.getMainAttributes().getValue("Plugin-Class")).isEqualTo("example.ProviderPlugin");
            assertThat(manifest.getMainAttributes().getValue("Absent-Header")).isNull();
            assertThat(manifest.getEntries().get("example/").getValue("Section-Header"))
                    .isEqualTo("section-value");
        }
    }

    @TestFactory
    Stream<DynamicTest> explodedOutputRetainsMetadataWhenTheArchiveActionDoesNotRun() {
        return Stream.of("direct", "up-to-date", "from-cache")
                .map(mode -> dynamicTest(mode, () -> {
                    var root = Files.createDirectory(tempDir.toPath().resolve(mode));
                    var ith = new IntegrationTestHelper(root.toFile(), "8.14");
                    initBuild(ith);
                    Files.writeString(root.resolve("build.gradle.kts"), getBasePluginConfig() + """
                    tasks.named("jar") { outputs.cacheIf { true } }
                    tasks.named("jpi") { outputs.cacheIf { true } }
                    """);
                    var runner = ith.gradleRunner();
                    if (!mode.equals("direct")) {
                        runner.withArguments("jpi", "--build-cache").build();
                        var path = root.resolve("build/jpi");
                        try (var paths = Files.walk(path)) {
                            for (var entry :
                                    paths.sorted(Comparator.reverseOrder()).toList()) {
                                Files.delete(entry);
                            }
                        }
                    }
                    var result = mode.equals("from-cache")
                            ? runner.withArguments("clean", "explodedJpi", "--build-cache")
                                    .build()
                            : runner.withArguments("explodedJpi", "--build-cache")
                                    .build();
                    if (!mode.equals("direct")) {
                        assertThat(result.task(":jpi").getOutcome())
                                .isEqualTo(mode.equals("from-cache") ? TaskOutcome.FROM_CACHE : TaskOutcome.UP_TO_DATE);
                    }
                    try (var input = Files.newInputStream(root.resolve("build/jpi/META-INF/MANIFEST.MF"))) {
                        assertThat(new Manifest(input).getMainAttributes().getValue("Short-Name"))
                                .isEqualTo("test-plugin");
                    }
                }));
    }

    @Test
    void runsImportedManifestProducersAndTracksTheirChanges() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(tempDir.toPath().resolve("class-name.txt"), "example.First");
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                val customManifest = layout.buildDirectory.file("custom.mf")
                val className = layout.projectDirectory.file("class-name.txt")
                abstract class GenerateCustomManifest : DefaultTask() {
                    @get:OutputFile abstract val outputFile: RegularFileProperty
                    @get:InputFile abstract val sourceFile: RegularFileProperty
                    @TaskAction fun generate() {
                        outputFile.get().asFile.apply {
                            parentFile.mkdirs()
                            writeText("Manifest-Version: 1.0\\nPlugin-Class: " + sourceFile.get().asFile.readText().trim() + "\\n\\n")
                        }
                    }
                }
                val producer = tasks.register<GenerateCustomManifest>("generateCustomManifest") {
                    outputFile.set(customManifest)
                    sourceFile.set(className)
                }
                tasks.named<War>("jpi") {
                    inputs.file(producer.flatMap { it.outputFile })
                    dependsOn(providers.provider { listOf(tasks.named("jar")) })
                    manifest.from(customManifest)
                }
                """);
        var runner = ith.gradleRunner().withArguments("prepareRun", "--configuration-cache");
        var first = runner.build();
        assertThat(first.task(":generateCustomManifest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        Files.writeString(tempDir.toPath().resolve("class-name.txt"), "example.Updated");
        var second = runner.build();
        assertThat(second.getOutput()).contains("Reusing configuration cache");
        try (var input = Files.newInputStream(
                ith.inProjectDir("build/hpl/test-plugin.hpl").toPath())) {
            assertThat(new Manifest(input).getMainAttributes().getValue("Plugin-Class"))
                    .isEqualTo("example.Updated");
        }
    }

    @Test
    void retainsExplicitJarHeadersAndLateExtensionConfiguration() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                tasks.named<War>("jpi").get()
                version = "2.0.0"
                jenkinsPlugin {
                    jenkinsVersion.set("2.504.3")
                    pluginId.set("late-id")
                    displayName.set("Late name")
                    homePage.set(uri("https://example.com/late"))
                }
                tasks.named<Jar>("jar") {
                    manifest.attributes("Long-Name" to "Custom JAR name")
                }
                """);
        ith.gradleRunner()
                .withArguments("jpi", "prepareRun", "--configuration-cache")
                .build();
        try (var jar = new JarFile(ith.inProjectDir("build/libs/test-plugin-2.0.0.jar"));
                var input = Files.newInputStream(
                        ith.inProjectDir("build/hpl/late-id.hpl").toPath())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Long-Name"))
                    .isEqualTo("Custom JAR name");
            assertThat(jar.getManifest().getMainAttributes().getValue("Jenkins-Version"))
                    .isEqualTo("2.504.3");
            assertThat(jar.getManifest().getMainAttributes().getValue("Short-Name"))
                    .isEqualTo("late-id");
            assertThat(jar.getManifest().getMainAttributes().getValue("Url")).isEqualTo("https://example.com/late");
            assertThat(jar.getManifest().getMainAttributes().getValue("Plugin-Version"))
                    .isEqualTo("2.0.0");
            var hpl = new Manifest(input).getMainAttributes();
            assertThat(hpl.getValue("Jenkins-Version")).isEqualTo("2.504.3");
            assertThat(hpl.getValue("Short-Name")).isEqualTo("late-id");
            assertThat(hpl.getValue("Plugin-Version")).isEqualTo("2.0.0");
        }
    }
}
