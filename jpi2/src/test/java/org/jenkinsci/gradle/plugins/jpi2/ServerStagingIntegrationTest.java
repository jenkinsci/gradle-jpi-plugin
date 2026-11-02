package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ServerStagingIntegrationTest extends V2IntegrationTestBase {
    @Test
    void nestedProjectDependenciesKeepTheirNames() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureModuleWithNestedDependencies(ith);

        ith.gradleRunner()
                .withArguments(":plugin-four:prepareServer", "--max-workers=2")
                .build();

        assertThat(ith.inProjectDir("plugin-four/work/plugins/plugin-three.jpi"))
                .exists();
        assertThat(ith.inProjectDir("plugin-four/work/plugins/git.jpi")).exists();
        assertThat(ith.inProjectDir("plugin-four/work/plugins/null.jpi")).doesNotExist();
    }

    @Test
    void prepareServerStagesPluginsAndReusesConfigurationCache() throws Exception {
        assertStaging("prepareServer");
    }

    @Test
    void prepareRunStagesPluginsAndReusesConfigurationCache() throws Exception {
        assertStaging("prepareRun");
    }

    private void assertStaging(String task) throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssPluginDependency(ith);
        var runner = ith.gradleRunner().withArguments(task, "--configuration-cache", "--max-workers=2");

        runner.build();
        assertThat(ith.inProjectDir("work/plugins/git.jpi")).exists();
        assertThat(ith.inProjectDir("work/plugins/workflow-step-api.jpi")).exists();
        assertThat(ith.inProjectDir("work/plugins/test-plugin." + (task.equals("prepareRun") ? "hpl" : "jpi")))
                .exists();

        var second = runner.build();
        assertThat(second.getOutput()).contains("Reusing configuration cache.");
        assertThat(second.task(":" + task).getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        var original =
                Files.readAllBytes(ith.inProjectDir("work/plugins/git.jpi").toPath());
        FileUtils.deleteDirectory(ith.inProjectDir("work/plugins"));
        var restored = runner.build();
        assertThat(restored.getOutput()).contains("Reusing configuration cache.");
        assertThat(restored.task(":" + task).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readAllBytes(ith.inProjectDir("work/plugins/git.jpi").toPath()))
                .isEqualTo(original);
        assertThat(ith.inProjectDir("work/plugins/test-plugin." + (task.equals("prepareRun") ? "hpl" : "jpi")))
                .exists();
    }

    @TestFactory
    Stream<DynamicTest> stagesArchiveExtensionsAndClassifiers() {
        return Stream.of("prepareServer", "prepareRun")
                .flatMap(task -> Stream.of("jpi", "hpi", "custom")
                        .map(extension ->
                                dynamicTest(task + " with " + extension, () -> assertArchiveNames(task, extension))));
    }

    private void assertArchiveNames(String task, String extension) throws Exception {
        var directory = Files.createDirectory(tempDir.toPath().resolve(task + "-" + extension));
        var ith = new IntegrationTestHelper(directory.toFile(), "8.14");
        configureSimpleBuild(ith);
        for (var packaging : new String[] {"hpi", "jpi"}) {
            writeArtifact(directory, "plugin-" + packaging, "1.0", "", packaging);
        }
        writeArtifact(directory, "classified", "1.0", "-linux", "hpi");
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + """
                repositories { maven { url = uri("repo") } }
                jenkinsPlugin { archiveExtension.set("%s") }
                dependencies {
                    implementation("test.staging:plugin-hpi:1.0@hpi")
                    implementation("test.staging:plugin-jpi:1.0@jpi")
                    implementation("test.staging:classified:1.0:linux@hpi")
                }
                """.formatted(extension));

        var runner = ith.gradleRunner().withArguments(task, "--configuration-cache", "--max-workers=2");
        runner.build();
        FileUtils.deleteDirectory(ith.inProjectDir("work/plugins"));
        var restored = runner.build();
        assertThat(restored.getOutput()).contains("Reusing configuration cache.");
        var stagedExtension = task.equals("prepareRun") ? "jpi" : extension;
        assertThat(ith.inProjectDir("work/plugins").list())
                .containsExactlyInAnyOrder(
                        "test-plugin." + (task.equals("prepareRun") ? "hpl" : extension),
                        "plugin-hpi." + stagedExtension,
                        "plugin-jpi." + stagedExtension,
                        "classified-linux." + stagedExtension);
        assertThat(Files.readAllBytes(ith.inProjectDir("work/plugins/classified-linux." + stagedExtension)
                        .toPath()))
                .isEqualTo(Files.readAllBytes(
                        directory.resolve("repo/test/staging/classified/1.0/classified-1.0-linux.hpi")));
    }

    private static void writeArtifact(Path directory, String name, String version, String classifier, String extension)
            throws IOException {
        var module = Files.createDirectories(directory.resolve("repo/test/staging/" + name + "/" + version));
        Files.writeString(module.resolve(name + "-" + version + ".pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>test.staging</groupId>
                <artifactId>%s</artifactId><version>%s</version><packaging>%s</packaging></project>
                """.formatted(name, version, extension));
        try (var archive = new JarOutputStream(
                Files.newOutputStream(module.resolve(name + "-" + version + classifier + "." + extension)))) {
            archive.putNextEntry(new JarEntry("payload.txt"));
            archive.write((name + version + classifier).getBytes(StandardCharsets.UTF_8));
        }
    }

    @TestFactory
    Stream<DynamicTest> rebuildsAndRemovesPluginProjectDependencies() {
        return Stream.of("prepareServer", "prepareRun")
                .flatMap(task -> Stream.of(false, true)
                        .map(included -> dynamicTest(
                                task + (included ? " included build" : " nested project"),
                                () -> assertProjectDependencyChanges(task, included))));
    }

    private void assertProjectDependencyChanges(String task, boolean included) throws Exception {
        var root = Files.createDirectory(tempDir.toPath().resolve(task + (included ? "-included" : "-nested")));
        var ith = new IntegrationTestHelper(root.toFile(), "8.14");
        var producer = Files.createDirectories(root.resolve(included ? "producer-build/producer" : "producer"));
        Files.writeString(root.resolve("settings.gradle.kts"), included ? """
                rootProject.name = "test-plugin"
                includeBuild("producer-build")
                """ : """
                rootProject.name = "test-plugin"
                include("producer")
                """);
        if (included) {
            Files.writeString(root.resolve("producer-build/settings.gradle.kts"), """
                    rootProject.name = "producer-build"
                    include("producer")
                    """);
            Files.writeString(root.resolve("producer-build/build.gradle.kts"), "");
        }
        var producerScript = getBasePluginConfig() + (included ? """
                val pluginType = org.jenkinsci.gradle.plugins.jpi2.ArtifactType.ARTIFACT_TYPE_ATTRIBUTE
                configurations.configureEach {
                    attributes.attribute(pluginType, objects.named("pluginJar"))
                }
                configurations.create("jenkinsPluginElements") {
                    isCanBeResolved = false
                    isCanBeConsumed = true
                    attributes.attribute(pluginType, objects.named("default"))
                    outgoing.artifact(tasks.named("jpi"))
                }
                """ : "");
        Files.writeString(producer.resolve("build.gradle.kts"), producerScript);
        var resources = Files.createDirectories(producer.resolve("src/main/resources"));
        Files.writeString(resources.resolve("payload.txt"), "first");
        var dependency = included ? "\"com.example:producer:1.0.0\"" : "project(\":producer\")";
        var script = getBasePluginConfig() + "\ndependencies { implementation(" + dependency + ") }\n";
        Files.writeString(root.resolve("build.gradle.kts"), script);
        var runner = ith.gradleRunner().withArguments(task, "--configuration-cache", "--max-workers=2");
        var linked = task.equals("prepareRun") && !included;
        var staged = root.resolve("work/plugins/producer." + (linked ? "hpl" : "jpi"));
        var first = runner.build();
        var producerTask =
                (included ? ":producer-build" : "") + ":producer:" + (linked ? GenerateHplTask.TASK_NAME : "jpi");
        assertThat(first.task(producerTask).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertProducerPayload(staged, linked, "first");

        Files.writeString(resources.resolve("payload.txt"), "second");
        FileUtils.deleteDirectory(root.resolve("work/plugins").toFile());
        var changed = runner.build();
        assertThat(changed.getOutput()).contains("Reusing configuration cache.");
        assertProducerPayload(staged, linked, "second");

        Files.writeString(producer.resolve("build.gradle.kts"), producerScript + "\nversion = \"2.0.0\"\n");
        runner.build();
        assertProducerPayload(staged, linked, "second");
        assertThat(root.resolve("work/plugins/producer-2.0.0.jpi")).doesNotExist();

        Files.writeString(root.resolve("build.gradle.kts"), getBasePluginConfig());
        runner.build();
        assertThat(staged).doesNotExist();
        assertThat(root.resolve("work/plugins/test-plugin." + (task.equals("prepareRun") ? "hpl" : "jpi")))
                .exists();
    }

    private static void assertProducerPayload(Path staged, boolean linked, String expected) throws Exception {
        if (!linked) {
            assertPluginPayload(staged, expected);
            return;
        }
        try (var input = Files.newInputStream(staged)) {
            var libraries = new Manifest(input).getMainAttributes().getValue("Libraries");
            var payload = Stream.of(libraries.split(","))
                    .map(Path::of)
                    .map(path -> path.resolve("payload.txt"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElseThrow();
            assertThat(Files.readString(payload)).isEqualTo(expected);
        }
    }

    private static void assertPluginPayload(Path archive, String expected) throws Exception {
        try (var plugin = new JarFile(archive.toFile())) {
            var library = plugin.stream()
                    .filter(entry -> entry.getName().startsWith("WEB-INF/lib/producer-"))
                    .findFirst()
                    .orElseThrow();
            try (var nested = new JarInputStream(plugin.getInputStream(library))) {
                for (var entry = nested.getNextJarEntry(); entry != null; entry = nested.getNextJarEntry()) {
                    if (entry.getName().equals("payload.txt")) {
                        assertThat(new String(nested.readAllBytes(), StandardCharsets.UTF_8))
                                .isEqualTo(expected);
                        return;
                    }
                }
                throw new AssertionError("Plugin JAR did not contain payload.txt");
            }
        }
    }
}
