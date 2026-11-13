package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.gradle.plugins.jpi2.MavenDependencyFixture.dependency;
import static org.jenkinsci.gradle.plugins.jpi2.MavenDependencyFixture.writeModule;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PluginProjectLibrariesIntegrationTest extends V2IntegrationTestBase {
    @Test
    void reusesIncludedProvidersAcrossAncestors() throws Exception {
        var root = Files.createDirectory(tempDir.toPath().resolve("included-chain"));
        var count = 4;
        var settings = new StringBuilder();
        writeModule(root, "example", "shared", "jar", "");
        for (var index = 0; index < count; index++) {
            var name = "plugin" + index;
            settings.append("includeBuild(\"").append(name).append("\")\n");
            var producer = Files.createDirectory(root.resolve(name));
            Files.writeString(producer.resolve("settings.gradle.kts"), "rootProject.name = \"" + name + "\"\n");
            Files.writeString(
                    producer.resolve("build.gradle.kts"),
                    getBasePluginConfig()
                            + """
                repositories { maven { url = uri("../repo") } }
                configurations.named("defaultRuntime") {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                }
                dependencies { implementation("%s") }
                """.formatted(
                                            index + 1 < count
                                                    ? "com.example:plugin" + (index + 1) + ":1.0.0"
                                                    : "example:shared:1"));
        }
        Files.writeString(root.resolve("settings.gradle.kts"), settings);
        Files.writeString(root.resolve("build.gradle.kts"), getBasePluginConfig() + """
            repositories { maven { url = uri("repo") } }
            dependencies { implementation("com.example:plugin0:1.0.0") }
            """);
        var tracker = root.resolve("track.gradle");
        Files.writeString(tracker, """
            gradle.addListener(new DependencyResolutionListener() {
                void beforeResolve(ResolvableDependencies dependencies) {
                    if (dependencies.name.startsWith('detachedConfiguration') &&
                            dependencies.dependencies.any { it.group == 'com.example' && it.name.startsWith('plugin') }) {
                        println('INCLUDED_PROVIDER_RESOLUTION')
                    }
                }
                void afterResolve(ResolvableDependencies dependencies) {}
            })
            """);
        var result = new IntegrationTestHelper(root.toFile(), "8.14")
                .gradleRunner()
                .withArguments("jpi", "--init-script", tracker.toString())
                .build();
        assertThat(result.getOutput()
                        .lines()
                        .filter("INCLUDED_PROVIDER_RESOLUTION"::equals)
                        .count())
                .isEqualTo(2L * count);
    }

    @TestFactory
    Stream<DynamicTest> selectsActualUpstreamArtifactsAcrossBuilds() {
        return Stream.of(
                        "included",
                        "classifier",
                        "nested-plugin",
                        "included-classifier",
                        "included-nested-plugin",
                        "published-classifier",
                        "published-plugin-classifier",
                        "published-nested-plugin",
                        "core-classifier",
                        "included-core-exclusion",
                        "included-alias")
                .map(mode -> dynamicTest(mode, () -> {
                    var root = Files.createDirectories(tempDir.toPath().resolve(mode));
                    var upstream = Files.createDirectory(root.resolve("upstream"));
                    var included = mode.startsWith("included");
                    var published = mode.startsWith("published");
                    Files.writeString(
                            root.resolve("settings.gradle.kts"),
                            published ? "" : included ? "includeBuild(\"upstream\")\n" : "include(\"upstream\")\n");
                    if (included) {
                        Files.writeString(upstream.resolve("settings.gradle.kts"), "rootProject.name = \"upstream\"\n");
                    }
                    if (mode.equals("included-alias")) {
                        Files.writeString(root.resolve("settings.gradle.kts"), """
                            includeBuild("upstream") {
                                dependencySubstitution {
                                    substitute(module("com.example:upstream")).using(project(":"))
                                }
                            }
                            """);
                    }
                    writeModule(root, "example", "shared", "jar", "");
                    try (var ignored = new JarOutputStream(
                            Files.newOutputStream(root.resolve("repo/example/shared/1/shared-1-extra.jar")))) {}
                    writeModule(root, "example", "extra", "jar", "");
                    writeModule(root, "example", "nested-plugin", "hpi", dependency("example", "extra"));
                    writeModule(root, "example", "nested-plugin", "2", "hpi", "");
                    for (var version : new String[] {"1", "2"}) {
                        var module = root.resolve("repo/example/nested-plugin/" + version);
                        Files.copy(
                                module.resolve("nested-plugin-" + version + ".hpi"),
                                module.resolve("nested-plugin-" + version + ".jar"));
                    }
                    var repositories = """
                            repositories { maven { url = uri("%s") } }
                            """;
                    var nested = mode.endsWith("nested-plugin");
                    var classifier = mode.endsWith("classifier");
                    var pluginClassifier = mode.equals("published-plugin-classifier");
                    var coreExclusion = mode.endsWith("core-exclusion");
                    if (published) {
                        writeModule(
                                root,
                                "com.example",
                                "upstream",
                                "1.0.0",
                                "hpi",
                                nested
                                        ? dependency("example", "nested-plugin") + dependency("example", "extra")
                                        : dependency("example", "shared"));
                        var module = root.resolve("repo/com/example/upstream/1.0.0");
                        Files.copy(module.resolve("upstream-1.0.0.hpi"), module.resolve("upstream-1.0.0.jar"));
                        if (pluginClassifier) {
                            Files.copy(
                                    module.resolve("upstream-1.0.0.jar"), module.resolve("upstream-1.0.0-extra.jar"));
                        }
                    }
                    Files.writeString(
                            upstream.resolve("build.gradle.kts"),
                            getBasePluginConfig()
                                    + repositories.formatted("../repo")
                                    + (nested ? """
                            dependencies {
                                implementation("example:nested-plugin:1")
                                implementation("example:extra:1")
                            }
                            """ : "dependencies { implementation(\"example:shared:1\") }\n")
                                    + (coreExclusion
                                            ? "dependencies { add(\"jenkinsCore\", \"example:shared:1\") }\n"
                                            : "")
                                    + (mode.equals("included-alias") ? "group = \"different.group\"\n" : ""));
                    Files.writeString(
                            root.resolve("build.gradle.kts"),
                            getBasePluginConfig()
                                    + repositories.formatted("repo")
                                    + """
                            dependencies {
                                implementation(%s)
                                %s
                            }
                            """.formatted(
                                                    included || published
                                                            ? "\"com.example:upstream:1.0.0\""
                                                            : "project(\":upstream\")",
                                                    nested
                                                            ? """
                                            implementation("example:nested-plugin:2")
                                            implementation("example:extra:1")
                                            """
                                                            : pluginClassifier
                                                                    ? "implementation(\"com.example:upstream:1.0.0:extra@jar\")"
                                                                    : classifier
                                                                            ? "implementation(\"example:shared:1:extra\")"
                                                                            : "")
                                    + (mode.equals("core-classifier") || coreExclusion ? """
                            dependencies { add("jenkinsCore", "example:shared:1") }
                            """ : "")
                                    + (coreExclusion ? """
                            dependencies { implementation("example:shared:1") }
                            configurations.named("jenkinsCore") { exclude(group = "example", module = "shared") }
                            """ : ""));
                    var runner = new IntegrationTestHelper(root.toFile(), "8.14")
                            .gradleRunner()
                            .withArguments("jpi", "--configuration-cache");
                    runner.build();
                    assertThat(runner.build().getOutput()).contains("Reusing configuration cache");
                    try (var archive = new JarFile(
                            root.resolve("build/libs/" + mode + "-1.0.0.jpi").toFile())) {
                        var entries =
                                archive.stream().map(entry -> entry.getName()).toList();
                        if (coreExclusion) {
                            assertThat(entries).contains("WEB-INF/lib/shared-1.jar");
                            assertThat(Files.readString(root.resolve("build/licenses/licenses.xml")))
                                    .contains("artifactId=\"shared\"");
                        } else {
                            assertThat(entries).doesNotContain("WEB-INF/lib/shared-1.jar");
                        }
                        if (nested || classifier) {
                            var module = pluginClassifier ? "upstream" : nested ? "extra" : "shared";
                            assertThat(entries)
                                    .contains("WEB-INF/lib/"
                                            + (pluginClassifier
                                                    ? "upstream-1.0.0-extra.jar"
                                                    : nested ? "extra-1.jar" : "shared-1-extra.jar"));
                            assertThat(Files.readString(root.resolve("build/licenses/licenses.xml")))
                                    .contains("artifactId=\"" + module + "\"");
                        }
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> retainsFeaturesOwnedByPluginProjects() {
        return Stream.of("sibling", "included", "feature-only-main", "feature-only-separate")
                .map(mode -> dynamicTest(mode, () -> {
                    var root = Files.createDirectory(tempDir.toPath().resolve("plugin-feature-" + mode));
                    var upstream = Files.createDirectory(root.resolve("upstream"));
                    var included = mode.equals("included");
                    var featureOnly = mode.startsWith("feature-only");
                    var mainFeature = mode.equals("feature-only-main");
                    Files.writeString(
                            root.resolve("settings.gradle.kts"),
                            included ? "includeBuild(\"upstream\")\n" : "include(\"upstream\")\n");
                    if (included) {
                        Files.writeString(upstream.resolve("settings.gradle.kts"), "rootProject.name = \"upstream\"\n");
                    }
                    writeModule(root, "example", "feature-only", "jar", "");
                    writeModule(root, "example", "shared", "jar", "");
                    Files.writeString(
                            upstream.resolve("build.gradle.kts"),
                            getBasePluginConfig()
                                    + """
                        java.registerFeature("extra") { usingSourceSet(%s) }
                        repositories { maven { url = uri("../repo") } }
                        dependencies {
                            implementation("example:shared:1")
                            add("extraImplementation", "example:feature-only:1")
                            add("extraImplementation", "example:shared:1")
                        }
                        """.formatted(
                                            mainFeature ? "sourceSets.main.get()" : "sourceSets.create(\"extra\")"));
                    var source = Files.createDirectories(upstream.resolve("src/extra/java/example"));
                    Files.writeString(source.resolve("Feature.java"), "package example; public class Feature {}\n");
                    var dependency = included ? "\"com.example:upstream:1.0.0\"" : "project(\":upstream\")";
                    Files.writeString(
                            root.resolve("build.gradle.kts"),
                            getBasePluginConfig()
                                    + """
                        repositories { maven { url = uri("repo") } }
                        dependencies {
                            %s
                            implementation(%s) { capabilities { requireCapability("com.example:upstream-extra") } }
                        }
                        """.formatted(
                                            featureOnly ? "" : "implementation(" + dependency + ")", dependency));
                    var runner = new IntegrationTestHelper(root.toFile(), "8.14")
                            .gradleRunner()
                            .withArguments("jpi", "--configuration-cache");
                    runner.build();
                    assertThat(runner.build().getOutput()).contains("Reusing configuration cache");
                    try (var archive = new JarFile(root.resolve("build/libs/plugin-feature-" + mode + "-1.0.0.jpi")
                            .toFile())) {
                        var entries =
                                archive.stream().map(entry -> entry.getName()).toList();
                        assertThat(entries)
                                .contains(
                                        "WEB-INF/lib/feature-only-1.jar",
                                        "WEB-INF/lib/upstream-1.0.0" + (mainFeature ? "" : "-extra") + ".jar");
                        if (!mainFeature) {
                            assertThat(entries).doesNotContain("WEB-INF/lib/upstream-1.0.0.jar");
                        }
                        if (featureOnly) {
                            assertThat(entries).contains("WEB-INF/lib/shared-1.jar");
                            assertThat(archive.getManifest().getMainAttributes().getValue("Plugin-Dependencies"))
                                    .isNull();
                        }
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> excludesUpstreamPluginAndItsLocalLibraries() {
        return Stream.of("standard", "included", "generated")
                .map(mode -> dynamicTest(mode, () -> {
                    var included = mode.equals("included");
                    var root = Files.createDirectories(tempDir.toPath().resolve("build-" + mode));
                    var plugins = included ? Files.createDirectory(root.resolve("plugins")) : root;
                    Files.writeString(plugins.resolve("settings.gradle.kts"), """
                    rootProject.name = "plugins"
                    include("upstream", "downstream")
                    """);
                    if (included) {
                        Files.writeString(root.resolve("settings.gradle.kts"), "includeBuild(\"plugins\")\n");
                    }
                    Files.writeString(plugins.resolve("build.gradle.kts"), "");
                    var upstream = Files.createDirectory(plugins.resolve("upstream"));
                    var downstream = Files.createDirectory(plugins.resolve("downstream"));
                    if (!mode.equals("generated")) {
                        try (var ignored =
                                new JarOutputStream(Files.newOutputStream(upstream.resolve("provided.jar")))) {}
                    }
                    Files.writeString(upstream.resolve("payload.txt"), "payload");
                    try (var ignored = new JarOutputStream(Files.newOutputStream(downstream.resolve("bundled.jar")))) {}
                    var providedLibrary = mode.equals("generated") ? """
                    val producer = tasks.register<Jar>("produceLocal") {
                        archiveFileName.set("provided.jar")
                        destinationDirectory.set(layout.buildDirectory.dir("generated-libs"))
                        from("payload.txt")
                    }
                    dependencies {
                        implementation(fileTree(layout.buildDirectory.dir("generated-libs")).builtBy(producer))
                    }
                    """ : """
                    dependencies { implementation(files("provided.jar")) }
                    """;
                    Files.writeString(upstream.resolve("build.gradle.kts"), getBasePluginConfig() + providedLibrary);
                    Files.writeString(downstream.resolve("build.gradle.kts"), getBasePluginConfig() + """
                    dependencies {
                        implementation(project(":upstream"))
                        implementation(files("bundled.jar"))
                    }
                    """);
                    var ith = new IntegrationTestHelper(root.toFile(), "8.14");
                    var runner = ith.gradleRunner()
                            .withArguments(
                                    included ? ":plugins:downstream:jpi" : ":downstream:jpi", "--configuration-cache");
                    runner.build();
                    assertThat(runner.build().getOutput()).contains("Reusing configuration cache");

                    try (var archive = new JarFile(downstream
                            .resolve("build/libs/downstream-1.0.0.jpi")
                            .toFile())) {
                        assertThat(archive.stream()
                                        .map(entry -> entry.getName())
                                        .toList())
                                .contains("WEB-INF/lib/bundled.jar")
                                .doesNotContain("WEB-INF/lib/provided.jar", "WEB-INF/lib/upstream-1.0.0.jar");
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> respectsSelectedProjectFeatures() {
        return Stream.of(
                        "transitive-feature",
                        "direct-feature",
                        "provided-feature-file",
                        "non-transitive-project",
                        "separate-feature")
                .map(mode -> dynamicTest(mode, () -> {
                    var root = Files.createDirectory(tempDir.toPath().resolve(mode));
                    Files.writeString(
                            root.resolve("settings.gradle.kts"), "include(\"upstream\", \"library\", \"shared\")\n");
                    for (var name : new String[] {"upstream", "library", "shared"}) {
                        Files.createDirectory(root.resolve(name));
                    }
                    writeModule(root, "example", "feature-only", "jar", "");
                    var providedFeature = mode.equals("provided-feature-file");
                    var nonTransitive = mode.equals("non-transitive-project");
                    var featureSourceSet =
                            mode.equals("separate-feature") ? "sourceSets.create(\"extra\")" : "sourceSets.main.get()";
                    try (var ignored =
                            new JarOutputStream(Files.newOutputStream(root.resolve("shared/provided.jar")))) {}
                    Files.writeString(root.resolve("shared/build.gradle.kts"), """
                            plugins { id("java-library") }
                            group = "example"
                            version = "1"
                            java.registerFeature("extra") { usingSourceSet(%s) }
                            repositories { maven { url = uri(rootProject.file("repo")) } }
                            dependencies {
                                add("extraImplementation", "example:feature-only:1")
                                add("extraImplementation", files("provided.jar"))
                            }
                            """.formatted(featureSourceSet));
                    if (nonTransitive) {
                        Files.writeString(root.resolve("shared/build.gradle.kts"), """
                            plugins { id("java-library") }
                            group = "example"
                            version = "1"
                            dependencies { implementation(files("provided.jar")) }
                            """);
                    }
                    var featureSelection = "capabilities { requireCapability(\"example:shared-extra\") }";
                    Files.writeString(root.resolve("library/build.gradle.kts"), """
                            plugins { id("java-library") }
                            version = "1"
                            dependencies {
                                implementation(project(":shared")) { %s }
                            }
                            """.formatted(
                                    providedFeature || nonTransitive ? "" : featureSelection));
                    Files.writeString(
                            root.resolve("upstream/build.gradle.kts"),
                            getBasePluginConfig()
                                    + """
                            repositories { maven { url = uri(rootProject.file("repo")) } }
                            dependencies { implementation(project(":shared")) { %s } }
                            """.formatted(
                                                    nonTransitive
                                                            ? "isTransitive = false"
                                                            : providedFeature ? featureSelection : ""));
                    var consumer = mode.equals("direct-feature")
                            ? "implementation(project(\":shared\")) { " + featureSelection + " }"
                            : "implementation(project(\":library\"))";
                    Files.writeString(
                            root.resolve("build.gradle.kts"), getBasePluginConfig() + """
                            repositories { maven { url = uri("repo") } }
                            dependencies {
                                implementation(project(":upstream"))
                                %s
                            }
                            val libraries = tasks.named<War>("jpi").get().classpath!!.files.map { it.name }.sorted()
                            tasks.register("writeLibraries") {
                                val names = libraries
                                val output = layout.projectDirectory.file("libraries.txt")
                                doLast { output.asFile.writeText(names.joinToString("\\n")) }
                            }
                            """.formatted(consumer));
                    var ith = new IntegrationTestHelper(root.toFile(), "8.14");
                    ith.gradleRunner()
                            .withArguments("writeLibraries")
                            .forwardOutput()
                            .build();
                    var libraries = Files.readAllLines(root.resolve("libraries.txt"));
                    assertThat(libraries).doesNotContain("shared-1.jar", "upstream-1.0.0.jar");
                    if (providedFeature) {
                        assertThat(libraries).containsExactly("library-1.jar");
                    } else if (nonTransitive) {
                        assertThat(libraries).containsExactly("library-1.jar", "provided.jar");
                    } else {
                        assertThat(libraries).contains("feature-only-1.jar", "provided.jar");
                        if (mode.equals("separate-feature")) {
                            assertThat(libraries).contains("shared-1-extra.jar");
                        }
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> respectsTheConsumingProjectsResolvedGraph() {
        return Stream.of(
                        "core-exclusion",
                        "unavailable-excluded-module",
                        "upstream-exclusion",
                        "upstream-configuration-exclusion",
                        "upstream-default-dependency",
                        "upstream-core-exclusion",
                        "upstream-selected-plugin-version",
                        "upstream-two-exclusions",
                        "upstream-two-exclusions-reversed")
                .map(mode -> dynamicTest(mode, () -> {
                    var root = Files.createDirectories(tempDir.toPath().resolve(mode));
                    var upstream = Files.createDirectory(root.resolve("upstream"));
                    Files.writeString(root.resolve("settings.gradle.kts"), "include(\"upstream\")\n");
                    var repositories = """
                    repositories {
                        exclusiveContent {
                            forRepository { maven { url = uri(rootProject.file("repo")) } }
                            filter { includeGroup("example") }
                        }
                    }
                    """;
                    var upstreamDependencies = mode.equals("core-exclusion") ? """
                    dependencies {
                        implementation("example:core-library:1")
                        add("jenkinsCore", "example:core-library:1")
                    }
                    configurations.named("jenkinsCore") { exclude(group = "example", module = "extra") }
                    """ : """
                    dependencies { runtimeOnly("example:unavailable:1") }
                    """;
                    var consumerDependencies = mode.equals("core-exclusion") ? """
                    dependencies {
                        implementation(project(":upstream"))
                        implementation("example:library:1")
                        add("jenkinsCore", "example:core-library:1")
                    }
                    configurations.named("jenkinsCore") { exclude(group = "example", module = "extra") }
                    """ : """
                    dependencies {
                        implementation(project(":upstream")) { exclude(group = "example", module = "unavailable") }
                    }
                    """;
                    if (mode.startsWith("upstream-")) {
                        writeModule(root, "example", "extra", "jar", "");
                        writeModule(root, "example", "shared", "jar", dependency("example", "extra"));
                        upstreamDependencies = """
                        dependencies {
                            implementation("example:shared:1") { exclude(group = "example", module = "extra") }
                        }
                        """;
                        consumerDependencies = """
                        dependencies {
                            implementation(project(":upstream"))
                            implementation("example:shared:1")
                        }
                        """;
                    }
                    if (mode.equals("upstream-configuration-exclusion")) {
                        upstreamDependencies = """
                        dependencies { implementation("example:shared:1") }
                        configurations.named("defaultRuntime") { exclude(group = "example", module = "extra") }
                        """;
                    } else if (mode.equals("upstream-default-dependency")) {
                        upstreamDependencies = """
                        dependencies { add("defaultRuntime", "example:shared:1") }
                        """;
                    }
                    if (mode.startsWith("upstream-two-exclusions")) {
                        var second = Files.createDirectory(root.resolve("second"));
                        Files.writeString(root.resolve("settings.gradle.kts"), "include(\"upstream\", \"second\")\n");
                        var withoutExclusion = "dependencies { implementation(\"example:shared:1\") }\n";
                        Files.writeString(
                                second.resolve("build.gradle.kts"),
                                getBasePluginConfig()
                                        + repositories
                                        + (mode.endsWith("reversed") ? upstreamDependencies : withoutExclusion));
                        if (mode.endsWith("reversed")) {
                            upstreamDependencies = withoutExclusion;
                        }
                        consumerDependencies = """
                        dependencies {
                            implementation(project(":upstream"))
                            implementation(project(":second"))
                        }
                        """;
                    }
                    if (mode.equals("upstream-core-exclusion")) {
                        writeModule(root, "example", "extra", "jar", "");
                        writeModule(root, "example", "core-library", "jar", dependency("example", "extra"));
                        writeModule(root, "example", "library", "jar", dependency("example", "core-library"));
                        upstreamDependencies = """
                        dependencies {
                            implementation("example:library:1")
                            add("jenkinsCore", "example:core-library:1")
                        }
                        """;
                        consumerDependencies = """
                        dependencies {
                            implementation(project(":upstream"))
                            add("jenkinsCore", "example:core-library:1")
                        }
                        configurations.named("jenkinsCore") { exclude(group = "example", module = "core-library") }
                        """;
                    }
                    if (mode.equals("core-exclusion")) {
                        writeModule(root, "example", "extra", "jar", "");
                        writeModule(root, "example", "core-library", "jar", dependency("example", "extra"));
                        writeModule(root, "example", "library", "jar", dependency("example", "core-library"));
                    }
                    if (mode.equals("upstream-selected-plugin-version")) {
                        writeModule(root, "example", "nested-plugin", "hpi", dependency("example", "extra"));
                        writeModule(root, "example", "nested-plugin", "2", "hpi", "");
                        upstreamDependencies = "dependencies { implementation(\"example:nested-plugin:1\") }\n";
                        consumerDependencies = """
                        dependencies {
                            implementation(project(":upstream"))
                            implementation("example:nested-plugin:2")
                            implementation("example:extra:1")
                        }
                        """;
                    }
                    Files.writeString(
                            upstream.resolve("build.gradle.kts"),
                            getBasePluginConfig() + repositories + upstreamDependencies);
                    Files.writeString(
                            root.resolve("build.gradle.kts"),
                            getBasePluginConfig() + repositories + consumerDependencies + """
                    val libraries = tasks.named<War>("jpi").get().classpath!!.files.map { it.name }.sorted()
                    tasks.register("writeLibraries") {
                        val output = layout.projectDirectory.file("libraries.txt")
                        val names = libraries
                        doLast { output.asFile.writeText(names.joinToString("\\n")) }
                    }
                    """);
                    var ith = new IntegrationTestHelper(root.toFile(), "8.14");
                    ith.gradleRunner()
                            .withArguments("writeLibraries", "generateLicenseInfo", "--configuration-cache")
                            .build();
                    var libraries = Files.readAllLines(root.resolve("libraries.txt"));
                    if (mode.equals("core-exclusion")) {
                        assertThat(libraries).containsExactly("library-1.jar");
                    } else if (mode.equals("upstream-core-exclusion")) {
                        assertThat(libraries).containsExactly("core-library-1.jar", "extra-1.jar");
                    } else if (mode.equals("upstream-exclusion")
                            || mode.equals("upstream-configuration-exclusion")
                            || mode.equals("upstream-selected-plugin-version")) {
                        assertThat(libraries).containsExactly("extra-1.jar");
                        assertThat(Files.readString(root.resolve("build/licenses/licenses.xml")))
                                .contains("artifactId=\"extra\"");
                    } else {
                        assertThat(libraries).isEmpty();
                    }
                }));
    }
}
