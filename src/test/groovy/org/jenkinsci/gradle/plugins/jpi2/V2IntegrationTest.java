package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 2, unit = TimeUnit.MINUTES)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class V2IntegrationTest {

    @TempDir File tempDir;

    @NotNull
    private static String getPublishingConfig() {
        return /* language=kotlin */ """
                group = "com.example"
                version = "1.0.0"
                publishing {
                    repositories {
                        maven {
                            name = "local"
                            url = uri("${rootDir}/build/repo")
                        }
                    }
                }
                """;
    }

    @NotNull
    private static String getBasePluginConfig() {
        return String.format(/* language=kotlin */ """
                plugins {
                    id("org.jenkins-ci.jpi2")
                }
                repositories {
                    mavenCentral()
                    maven {
                        name = "jenkins-releases"
                        url = uri("https://repo.jenkins-ci.org/releases/")
                    }
                }
                tasks.named<JavaExec>("server") {
                    args("--httpPort=%d")
                }
                """, RandomPortProvider.findFreePort()) + getPublishingConfig();
    }

    @NotNull
    private static String getBaseLibraryConfig() {
        return /* language=kotlin */ """
                plugins {
                    id("java-library")
                    id("maven-publish")
                }
                repositories {
                    mavenCentral()
                }
                publishing {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                }
                """ + getPublishingConfig();
    }

    private static void initBuild(IntegrationTestHelper ith) throws IOException {
        Files.write(/* language=kotlin */ """
                rootProject.name = "test-plugin"
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("settings.gradle.kts"));
    }

    static class TapWriter extends Writer {
        private final Writer writer1;
        private final Writer writer2;

        public TapWriter(Writer writer1, Writer writer2) {
            this.writer1 = writer1;
            this.writer2 = writer2;
        }

        @Override
        public void write(@NotNull char[] bytes, int off, int len) throws IOException {
            writer1.write(bytes, off, len);
            writer2.write(bytes, off, len);
        }

        @Override
        public void flush() throws IOException {
            writer1.flush();
            writer2.flush();
        }

        @Override
        public void close() throws IOException {
            writer1.close();
            writer2.close();
        }
    }

    private static void testServerStarts(GradleRunner gradleRunner, String... task) throws InterruptedException {
        var stdout1 = new StringWriter();
        var stdout2 = new StringWriter();
        var stdout = new TapWriter(stdout1, stdout2);
        var stderr1 = new StringWriter();
        var stderr2 = new StringWriter();
        var stderr = new TapWriter(stderr1, stderr2);
        var serverThread = Executors.newSingleThreadExecutor();
        final AtomicReference<BuildResult> buildResult = new AtomicReference<>();
        serverThread.submit(() -> buildResult.set(gradleRunner.withArguments(task)
                .forwardStdError(stderr)
                .forwardStdOutput(stdout)
                .build()));
        Awaitility.await()
                .atMost(3, TimeUnit.MINUTES)
                .pollInterval(5, TimeUnit.SECONDS)
                .conditionEvaluationListener(condition -> {
                    if (condition.getRemainingTimeInMS() <= 0 || condition.isSatisfied()) {
                        serverThread.shutdownNow();
                    }
                })
                .until(() -> {
                    System.err.print(stderr1);
                    stderr1.getBuffer().setLength(0);
                    System.err.print(stdout1);
                    stdout1.getBuffer().setLength(0);
                    return stderr2.toString().contains("Jenkins is fully up and running")
                           || stderr2.toString().contains("BUILD FAILED")
                           || stderr2.toString().contains("BUILD SUCCESSFUL");
                });

        serverThread.shutdown();
        boolean terminatedSafely = serverThread.awaitTermination(1, TimeUnit.MINUTES);
        assertThat(terminatedSafely).isTrue();
        assertThat(buildResult.get()).isNull();
        assertThat(stderr2.toString()).contains("Jenkins is fully up and running");
    }

    private static void configureSimpleBuild(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    @Test
    void simpleGradleBuildShouldBuild() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        // when
        ith.gradleRunner().withArguments("build").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin-1.0.0.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifest).isNotNull().isNotEmpty();

        assertThat(manifestData.getValue("Jenkins-Version"))
                .isEqualTo("2.492.3");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void simpleGradleBuildShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");
    }

    private static void configureBuildWithOssPluginDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
    Files.write((getBasePluginConfig() + /* language=kotlin */ """
            dependencies {
                implementation("org.jenkins-ci.plugins:git:5.7.0")
            }
            """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    @Test
    void gradleBuildWithOssPluginDependencyShouldBuild() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssPluginDependency(ith);

        // when
        ith.gradleRunner().withArguments("build").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin-1.0.0.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifest).isNotNull().isNotEmpty();

        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");
        assertThat(manifestData.getValue("Plugin-Dependencies")).isEqualTo("git:5.7.0");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void gradleBuildWithOssPluginDependencyShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssPluginDependency(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");

        // the selected plugin
        var pluginsDir = ith.inProjectDir("work/plugins");
        assertThat(pluginsDir).exists();

        var files = Arrays.stream(Objects.requireNonNull(pluginsDir.list())).sorted().toList();
        assertThat(files).isNotNull()
                .containsExactly(
                        "apache-httpcomponents-client-4-api.jpi",
                        "asm-api.jpi",
                        "bouncycastle-api.jpi",
                        "caffeine-api.jpi",
                        "credentials-binding.jpi",
                        "credentials.jpi",
                        "display-url-api.jpi",
                        "git-client.jpi",
                        "git.jpi",
                        "gson-api.jpi",
                        "instance-identity.jpi",
                        "jakarta-activation-api.jpi",
                        "jakarta-mail-api.jpi",
                        "mailer.jpi",
                        "mina-sshd-api-common.jpi",
                        "mina-sshd-api-core.jpi",
                        "plain-credentials.jpi",
                        "scm-api.jpi",
                        "script-security.jpi",
                        "ssh-credentials.jpi",
                        "structs.jpi",
                        "test-plugin-1.0.0.jpi",
                        "variant.jpi",
                        "workflow-scm-step.jpi",
                        "workflow-step-api.jpi"
                );
    }

    private static void configureBuildWithOssLibraryDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    @Test
    void gradleBuildWithOssLibraryDependencyShouldBuild() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssLibraryDependency(ith);

        // when
        ith.gradleRunner().withArguments("build", "publish").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin-1.0.0.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifest).isNotNull().isNotEmpty();

        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("nothing-java-0.2.0.jar",
                        "test-plugin-1.0.0.jar",
                        "commons-math3-3.6.1.jar",
                        "commons-lang3-3.12.0.jar");
    }

    @Test
    void gradleBuildWithOssLibraryDependencyShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureBuildWithOssLibraryDependency(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");
    }

    @Test
    void generatesExtensionsWithSezpoz() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig()).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write((/* language=java */ """
                package com.example.plugin;
                @hudson.Extension
                public class SomeExtension {
                    public static void init() {
                        System.out.println("Hello from SomeExtension");
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/SomeExtension.java"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        gradleRunner.withArguments("build").build();

        // then
        var extensionsList = ith.inProjectDir("build/classes/java/main/META-INF/annotations/hudson.Extension.txt");
        assertThat(extensionsList).exists();

        var lines = Files.readLines(extensionsList, StandardCharsets.UTF_8);
        assertThat(lines.subList(1, lines.size())).containsExactlyInAnyOrder("com.example.plugin.SomeExtension");
    }

    private static void configureModuleWithNestedDependencies(IntegrationTestHelper ith) throws IOException {
        Files.write(/* language=kotlin */ """
                rootProject.name = "test-plugin"
                include("library-one", "library-two", "plugin-three", "plugin-four")
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("settings.gradle.kts"));
        Files.write(/* language=properties */ """
                jenkins.version=2.492.3
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("gradle.properties"));
        Files.write(("").getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("library-one");
        Files.write((getBaseLibraryConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("library-one/build.gradle.kts"));
        ith.mkDirInProjectDir("library-one/src/main/java/com/example/lib1");
        Files.write((/* language=java */ """
                package com.example.lib1;
                import com.github.rahulsom.nothing.java.Foo;
                public class Example {
                    public String hello() {
                        return "Hello";
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("library-one/src/main/java/com/example/lib1/Example.java"));
        ith.mkDirInProjectDir("library-two");
        Files.write((getBaseLibraryConfig() + /* language=kotlin */ """
                dependencies {
                    implementation(project(":library-one"))
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("library-two/build.gradle.kts"));
        ith.mkDirInProjectDir("library-two/src/main/java/com/example/lib2");
        Files.write((/* language=java */ """
                package com.example.lib2;
                import com.example.lib1.Example;
                public class ExampleTwo {
                    public String hello() {
                        return new Example().hello();
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("library-two/src/main/java/com/example/lib2/ExampleTwo.java"));
        ith.mkDirInProjectDir("plugin-three");
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation(project(":library-two"))
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-three/build.gradle.kts"));
        ith.mkDirInProjectDir("plugin-three/src/main/java/com/example/plugin3");
        Files.write((/* language=java */ """
                package com.example.plugin3;
                import com.example.lib2.ExampleTwo;
                /** Example simple class. */
                public class ExampleThree {
                    /** Example simple constructor. */
                    public ExampleThree() {
                        System.out.println("Hello from ExampleThree");
                    }
                    /**
                     * Example simple method.
                     * @return a hello string
                     */
                    public String hello() {
                        return new ExampleTwo().hello();
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-three/src/main/java/com/example/plugin3/ExampleThree.java"));
        ith.mkDirInProjectDir("plugin-four");
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation(project(":plugin-three"))
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-four/build.gradle.kts"));
        ith.mkDirInProjectDir("plugin-four/src/main/java/com/example/plugin4");
        Files.write((/* language=java */ """
                package com.example.plugin4;
                import com.example.plugin3.ExampleThree;
                /** Example simple class. */
                public class ExampleFour {
                    /** Example simple constructor. */
                    public ExampleFour() {
                        System.out.println("Hello from ExampleFour");
                    }
                    /**
                     * Example simple method.
                     * @return a hello string
                     */
                    public String hello() {
                        return new ExampleThree().hello();
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-four/src/main/java/com/example/plugin4/ExampleFour.java"));
    }

    @Test
    void multiModuleWithNestedDependenciesShouldBuild() throws IOException, XmlPullParserException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureModuleWithNestedDependencies(ith);

        // when
        ith.gradleRunner()
                .withArguments("build", "publish")
                .build();

        // then
        var jpi = ith.inProjectDir("plugin-four/build/libs/plugin-four-1.0.0.jpi");
        var jar = ith.inProjectDir("plugin-four/build/libs/plugin-four-1.0.0.jar");
        var explodedWar = ith.inProjectDir("plugin-four/build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifest).isNotNull().isNotEmpty();

        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");
        assertThat(manifestData.getValue("Plugin-Dependencies")).isEqualTo("plugin-three:1.0.0");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("plugin-four-1.0.0.jar");

        var pom = ith.inProjectDir("build/repo/com/example/plugin-four/1.0.0/plugin-four-1.0.0.pom");

        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom));

        assertThat(model).isNotNull();

        assertThat(model.getGroupId()).isEqualTo("com.example");
        assertThat(model.getArtifactId()).isEqualTo("plugin-four");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getPackaging()).isEqualTo("jpi");

        var dependencies = model.getDependencies();
        assertThat(dependencies)
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion, Dependency::getScope)
                .containsExactlyInAnyOrder(
                        new Tuple("com.example", "plugin-three", "1.0.0", "runtime")
                );

    }

    @Test
    void multiModuleWithNestedDependenciesShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureModuleWithNestedDependencies(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, ":plugin-four:server");

        // then
        var pluginThreeJpi = ith.inProjectDir("plugin-four/work/plugins/plugin-three-1.0.0.jpi"); // TODO Remove version from here
        assertThat(pluginThreeJpi).exists();
    }

    @Test
    void manifestContainsVersionWhenUsingForce() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                configurations.configureEach {
                    resolutionStrategy {
                        force("org.jenkins-ci.plugins:git:5.7.0")
                    }
                }
                dependencies {
                    implementation("org.jenkins-ci.plugins:git")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        gradleRunner.withArguments("build").build();

        // then
        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();

        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData).isNotNull().isNotEmpty();
        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");
        assertThat(manifestData.getValue("Plugin-Dependencies")).isEqualTo("git:5.7.0");
    }

    @Test
    void manifestContainsVersionWhenUsingBom() throws IOException, XmlPullParserException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        ith.mkDirInProjectDir("src-repo/com/example/bom/bom/1.0.0");
        Files.write(/* language=xml */ """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example.bom</groupId>
                    <artifactId>bom</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.jenkins-ci.plugins</groupId>
                                <artifactId>git</artifactId>
                                <version>5.7.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src-repo/com/example/bom/bom/1.0.0/bom-1.0.0.pom"));
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                repositories {
                    maven {
                        url = uri("${rootDir}/src-repo")
                    }
                }
                dependencies {
                    implementation(platform("com.example.bom:bom:1.0.0"))
                    api("org.jenkins-ci.plugins:git")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("build", "publish").build();

        // then
        assertThat(result.getOutput()).doesNotContain("Dependency resolution rules will not be applied to configuration");
        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();

        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData).isNotNull().isNotEmpty();
        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");
        assertThat(manifestData.getValue("Plugin-Dependencies")).isEqualTo("git:5.7.0");

        var pom = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.pom");
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom));
        assertThat(model).isNotNull();

        assertThat(model.getGroupId()).isEqualTo("com.example");
        assertThat(model.getArtifactId()).isEqualTo("test-plugin");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getPackaging()).isEqualTo("jpi");
        var dependencies = model.getDependencies();
        assertThat(dependencies)
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion, Dependency::getScope)
                .containsExactlyInAnyOrder(
                        new Tuple("org.jenkins-ci.plugins", "git", "5.7.0", "compile")
                );
    }

    @Test
    void shouldHaveTestDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig()).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.write((/* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import org.jvnet.hudson.test.JenkinsRule;
                import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
                import static org.junit.jupiter.api.Assertions.*;
                @WithJenkins
                public class PluginTest {
                    /** @noinspection JUnitMalformedDeclaration*/
                    @Test
                    void test(JenkinsRule j) {
                        var injector = j.jenkins.getInjector();
                        //noinspection DataFlowIssue
                        assertNotNull(injector);
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2414.v185474555e66")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.3");
    }

    @Test
    void testCanAccessJpiDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.write((/* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                import hudson.plugins.git.Branch;
                public class PluginTest {
                    @Test
                    void test() {
                        Branch branch = null;
                        assertNull(branch);
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2414.v185474555e66")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.3");
    }

    @Test
    void shouldCustomizeJenkinsVersionAndTestHarnessVersion() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(/* language=properties */ """
                jenkins.version=2.492.1
                jenkins.testharness.version=2411.v1e79b_0dc94b_7
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("gradle.properties"));
        Files.write((getBasePluginConfig()).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/test/java/com/example/plugin");
        Files.write((/* language=java */ """
                package com.example.plugin;
                import org.junit.jupiter.api.Test;
                import org.jvnet.hudson.test.JenkinsRule;
                import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
                import static org.junit.jupiter.api.Assertions.*;
                @WithJenkins
                public class PluginTest {
                    /** @noinspection JUnitMalformedDeclaration*/
                    @Test
                    void test(JenkinsRule j) {
                        var injector = j.jenkins.getInjector();
                        //noinspection DataFlowIssue,ObviousNullCheck
                        assertNotNull(injector);
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/test/java/com/example/plugin/PluginTest.java"));

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("test").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=testCompileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("org.jenkins-ci.main:jenkins-test-harness:2411.v1e79b_0dc94b_7")
                .contains("org.jenkins-ci.main:jenkins-core:2.492.1");
    }

    @Test
    void publishesJarAndJpi() throws IOException, XmlPullParserException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    api("org.jenkins-ci.plugins:jackson2-api:2.18.3-402.v74c4eb_f122b_2")
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        // when
        var gradleRunner = ith.gradleRunner();
        gradleRunner.withArguments("publish").build();

        // then
        var jpi = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jpi");
        var jar = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jar");
        var sourcesJar = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0-sources.jar");
        var javadocJar = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0-javadoc.jar");
        var pom = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.pom");
        var module = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.module");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(sourcesJar).exists();
        assertThat(javadocJar).exists();
        assertThat(pom).exists();
        assertThat(module).exists();

        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom));

        assertThat(model).isNotNull();

        assertThat(model.getGroupId()).isEqualTo("com.example");
        assertThat(model.getArtifactId()).isEqualTo("test-plugin");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getPackaging()).isEqualTo("jpi");

        var dependencies = model.getDependencies();
        assertThat(dependencies)
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion, Dependency::getScope)
                .containsExactlyInAnyOrder(
                        new Tuple("org.jenkins-ci.plugins", "jackson2-api", "2.18.3-402.v74c4eb_f122b_2", "compile"),
                        new Tuple("com.github.rahulsom", "nothing-java", "0.2.0", "runtime")
                );

        var repositories = model.getRepositories();
        assertThat(repositories)
                .extracting(Repository::getId, Repository::getUrl)
                .containsExactlyInAnyOrder(
                        new Tuple("MavenRepo", "https://repo.maven.apache.org/maven2/"),
                        new Tuple("jenkins-releases", "https://repo.jenkins-ci.org/releases/")
                );
        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("nothing-java-0.2.0.jar",
                        "test-plugin-1.0.0.jar",
                        "commons-math3-3.6.1.jar",
                        "commons-lang3-3.12.0.jar");
    }

    @Test
    void consumesJpisWithJars() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:jackson2-api:2.18.3-402.v74c4eb_f122b_2")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class Plugin {
                    public static void main(String[] args) {
                        var o = new ObjectMapper();
                    }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/Plugin.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");

    }

    @Test
    void playsWellWithLombok() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import lombok.*;
                import hudson.Extension;
                import jakarta.inject.Inject;
                import hudson.model.RootAction;
                @Extension
                @NoArgsConstructor
                public class PluginAction implements RootAction {
                    private String name;
                    @Inject
                    public PluginAction(String name) {
                        this.name = name;
                    }
                    public String getUrlName() { return "example"; }
                    public String getDisplayName() { return "Example plugin"; }
                    public String getIconFileName() { return null; }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void getsCorrectGuiceVersion() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                    runtimeOnly("com.google.inject:guice:5.1.0") // This is an older version of Guice that should get upgraded
                }
                configurations.getByName("compileClasspath").shouldResolveConsistentlyWith(configurations.getByName("runtimeClasspath"))
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import lombok.*;
                import hudson.Extension;
                import jakarta.inject.Inject;
                import hudson.model.RootAction;
                @Extension
                @NoArgsConstructor
                public class PluginAction implements RootAction {
                    private String name;
                    @Inject
                    public PluginAction(String name) {
                        this.name = name;
                    }
                    public String getUrlName() { return "example"; }
                    public String getDisplayName() { return "Example plugin"; }
                    public String getIconFileName() { return null; }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("dependencies", "--configuration=defaultRuntime").build();

        // then
        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("com.google.inject:guice:5.1.0 -> 6.0.0");

        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=compileClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("com.google.inject:guice:6.0.0");


        // when
        result = gradleRunner.withArguments("dependencies", "--configuration=runtimeClasspath").build();

        // then
        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("com.google.inject:guice:5.1.0 -> 6.0.0");

    }

    @Test
    void skipsJenkinsCoreDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                    runtimeOnly("com.google.inject:guice:5.1.0") // This is an older version of Guice that should get upgraded
                }
                configurations.getByName("compileClasspath").shouldResolveConsistentlyWith(configurations.getByName("runtimeClasspath"))
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import lombok.*;
                import hudson.Extension;
                import jakarta.inject.Inject;
                import hudson.model.RootAction;
                @Extension
                @NoArgsConstructor
                public class PluginAction implements RootAction {
                    private String name;
                    @Inject
                    public PluginAction(String name) {
                        this.name = name;
                    }
                    public String getUrlName() { return "example"; }
                    public String getDisplayName() { return "Example plugin"; }
                    public String getIconFileName() { return null; }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void skipsJarDependenciesOfPluginDependencies() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() +/* language=kotlin */ """
                dependencies {
                    annotationProcessor("org.projectlombok:lombok:1.18.38")
                    compileOnly("org.projectlombok:lombok:1.18.38")
                    implementation("org.jenkins-ci.plugins:jackson2-api:2.19.0-404.vb_b_0fd2fea_e10")
                    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0") // This is a jar dependency that should not be included in the JPI
                }
                configurations.getByName("compileClasspath").shouldResolveConsistentlyWith(configurations.getByName("runtimeClasspath"))
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import lombok.*;
                import hudson.Extension;
                import jakarta.inject.Inject;
                import hudson.model.RootAction;
                @Extension
                @NoArgsConstructor
                public class PluginAction implements RootAction {
                    private String name;
                    @Inject
                    public PluginAction(String name) {
                        this.name = name;
                    }
                    public String getUrlName() { return "example"; }
                    public String getDisplayName() { return "Example plugin"; }
                    public String getIconFileName() { return null; }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void playsWellWithGitPlugin() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import hudson.plugins.git.Branch;
                public class PluginAction {
                    private String name;
                    public PluginAction(String name) {
                        Branch branch;
                        this.name = name;
                    }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/PluginAction.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");
    }

    @Test
    void dependencyTreeIsCorrectForRuntimeClasspath() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        var gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("dependencies", "--configuration=runtimeClasspath").build();

        // then
        var expected = getClass().getClassLoader().getResourceAsStream("org/jenkinsci/gradle/plugins/jpi2/runtimeClasspath.txt");

        List<String> actualList = Arrays.stream(result.getOutput().split("\n")).toList();
        Assertions.assertNotNull(expected);
        List<String> expectedList = IOUtils.readLines(expected, StandardCharsets.UTF_8);
        assertThat(String.join("\n", actualList.subList(0, actualList.size() - 2)))
                .isEqualTo(String.join("\n", expectedList.subList(0, expectedList.size() - 2)));
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void dependencyTreeIsCorrectForCompileClasspath() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        var gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("dependencies", "--configuration=compileClasspath").build();

        // then
        var expected = getClass().getClassLoader().getResourceAsStream("org/jenkinsci/gradle/plugins/jpi2/compileClasspath.txt");

        List<String> actualList = Arrays.stream(result.getOutput().split("\n")).toList();
        Assertions.assertNotNull(expected);
        List<String> expectedList = IOUtils.readLines(expected, StandardCharsets.UTF_8);
        assertThat(String.join("\n", actualList.subList(0, actualList.size() - 2)))
                .isEqualTo(String.join("\n", expectedList.subList(0, expectedList.size() - 2)));
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void respectsExclusions() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0") {
                        exclude(group = "org.apache.commons", module = "commons-lang3")
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        var gradleRunner = ith.gradleRunner();

        // when
        var result = gradleRunner.withArguments("jpi").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
        var libs = ith.inProjectDir("build/jpi/WEB-INF/lib");

        assertThat(libs).exists();

        var jpiLibs = libs.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("nothing-java-0.2.0.jar",
                        "test-plugin-1.0.0.jar",
                        "commons-math3-3.6.1.jar");
    }

    @SuppressWarnings("unused") // Can be used to reproduce issues
    private static File repro() {
        var file = new File("/tmp/repro");
        if (file.exists()) {
            try {
                FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        boolean successful = file.mkdirs();
        assertThat(successful).isTrue();
        return file;
    }

}
