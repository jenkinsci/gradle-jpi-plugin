package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.awaitility.Awaitility;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 2, unit = TimeUnit.MINUTES)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class V2IntegrationTest {
    private static String getBasePluginConfig() {
        return String.format(/* language=kotlin */ """
                plugins {
                    id("org.jenkins-ci.jpi2")
                }
                repositories {
                    mavenCentral()
                    maven {
                        url = uri("https://repo.jenkins-ci.org/releases/")
                    }
                }
                tasks.named<JavaExec>("server") {
                    args("--httpPort=%d")
                }
                """, RandomPortProvider.findFreePort());
    }

    private static String getBaseLibraryConfig() {
        return /* language=kotlin */ """
                plugins {
                    id("java-library")
                }
                repositories {
                    mavenCentral()
                }
                """;
    }

    private static void initBuild(IntegrationTestHelper ith) throws IOException {
        Files.write(/* language=kotlin */ """
                rootProject.name = "test-plugin"
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("settings.gradle.kts"));
        Files.write(/* language=properties */ """
                jenkins.version=2.500
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("gradle.properties"));
    }

    static class TapWriter extends Writer {
        private final Writer writer1;
        private final Writer writer2;

        public TapWriter(Writer writer1, Writer writer2) {
            this.writer1 = writer1;
            this.writer2 = writer2;
        }

        @Override
        public void write(@NotNull char[] cbuf, int off, int len) throws IOException {
            writer1.write(cbuf, off, len);
            writer2.write(cbuf, off, len);
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
                .atMost(2, TimeUnit.MINUTES)
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
        serverThread.awaitTermination(1, TimeUnit.MINUTES);
        assertThat(buildResult.get()).isNull();
        assertThat(stderr2.toString()).contains("Jenkins is fully up and running");
    }

    private static void configureSimpleBuild(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    @Test
    void simpleGradleBuildShouldBuild(@TempDir File tempDir) throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureSimpleBuild(ith);

        // when
        ith.gradleRunner().withArguments("build").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = Files.readLines(manifest, StandardCharsets.UTF_8);

        assertThat(manifestData)
                .contains("Jenkins-Version: 2.500");

        var jarFileInJpi = new File(explodedWar, "WEB-INF/lib/test-plugin.jar");
        assertThat(jarFileInJpi).exists();
    }

    @Test
    void simpleGradleBuildShouldLaunchServer(@TempDir File tempDir) throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureSimpleBuild(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");
    }

    private static void configureBuildWithOssPluginDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    jenkinsPlugin("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    @Test
    void gradleBuildWithOssPluginDependencyShouldBuild(@TempDir File tempDir) throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureBuildWithOssPluginDependency(ith);

        // when
        ith.gradleRunner().withArguments("build").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = Files.readLines(manifest, StandardCharsets.UTF_8);

        assertThat(manifestData)
                .contains("Jenkins-Version: 2.500")
                .contains("Plugin-Dependencies: git:5.7.0");
    }

    @Test
    void gradleBuildWithOssPluginDependencyShouldLaunchServer(@TempDir File tempDir) throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureBuildWithOssPluginDependency(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");

        // the selected plugin
        var gitPlugin = ith.inProjectDir("work/plugins/git-5.7.0.hpi");
        // a transitive dependency
        var gitClientPlugin = ith.inProjectDir("work/plugins/git-client-6.1.0.hpi");

        assertThat(gitPlugin).exists();
        assertThat(gitClientPlugin).exists();
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
    void gradleBuildWithOssLibraryDependencyShouldBuild(@TempDir File tempDir) throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureBuildWithOssLibraryDependency(ith);

        // when
        ith.gradleRunner().withArguments("build").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin.jpi");
        var jar = ith.inProjectDir("build/libs/test-plugin.jar");
        var explodedWar = ith.inProjectDir("build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = Files.readLines(manifest, StandardCharsets.UTF_8);

        assertThat(manifestData)
                .contains("Jenkins-Version: 2.500");

        var jarFileInJpi = new File(explodedWar, "WEB-INF/lib/test-plugin.jar");
        assertThat(jarFileInJpi).exists();

        var dependencyJar = new File(explodedWar, "WEB-INF/lib/nothing-java-0.2.0.jar");
        assertThat(dependencyJar).exists();

    }

    @Test
    void gradleBuildWithOssLibraryDependencyShouldLaunchServer(@TempDir File tempDir) throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureBuildWithOssLibraryDependency(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, "server");
    }

    @Test
    void generatesExtensionsWithSezpoz(@TempDir File tempDir) throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        Files.write((getBasePluginConfig()).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(("""
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
        assertThat(lines).contains("com.example.plugin.SomeExtension");
    }

    private static void configureModuleWithNestedDependencies(IntegrationTestHelper ith) throws IOException {
        Files.write(/* language=kotlin */ """
                rootProject.name = "test-plugin"
                
                include("library-one", "library-two", "plugin-three", "plugin-four")
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("settings.gradle.kts"));
        Files.write(/* language=properties */ """
                jenkins.version=2.500
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
                    jenkinsPlugin("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-three/build.gradle.kts"));
        ith.mkDirInProjectDir("plugin-three/src/main/java/com/example/plugin3");
        Files.write((/* language=java */ """
                package com.example.plugin3;
                import com.example.lib2.ExampleTwo;
                public class ExampleThree {
                    public String hello() {
                        return new ExampleTwo().hello();
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-three/src/main/java/com/example/plugin3/ExampleThree.java"));
        ith.mkDirInProjectDir("plugin-four");
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                                dependencies {
                                    jenkinsPlugin(project(":plugin-three"))
                                }
                                tasks.named("compileJava") {
                                    dependsOn(":plugin-three:war") // TODO: This should not be required
                                    /*
                                        This is what we see when running the same from outside the test with the task-graph plugin
                                       \s
                                        :plugin-four:classes
                                        +--- :plugin-four:compileJava
                                        |    \\--- :plugin-three:jar
                                        |         +--- :plugin-three:classes
                                        |         |    +--- :plugin-three:compileJava
                                        |         |    |    \\--- :library-two:compileJava
                                        |         |    |         \\--- :library-one:compileJava
                                        |         |    \\--- :plugin-three:processResources
                                        |         \\--- :plugin-three:compileJava *
                                        \\--- :plugin-four:processResources
                                       \s
                                        This iw what we see when running the same from inside the test
                                       \s
                                        :plugin-four:classes
                                        +--- :plugin-four:compileJava
                                        \\--- :plugin-four:processResources
                                     */
                                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-four/build.gradle.kts"));
        ith.mkDirInProjectDir("plugin-four/src/main/java/com/example/plugin4");
        Files.write((/* language=java */ """
                package com.example.plugin4;
                import com.example.plugin3.ExampleThree;
                public class ExampleFour {
                    public String hello() {
                        return new ExampleThree().hello();
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("plugin-four/src/main/java/com/example/plugin4/ExampleFour.java"));
    }

    @Test
    void multiModuleWithNestedDependenciesShouldBuild(@TempDir File tempDir) throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureModuleWithNestedDependencies(ith);

        // when
        ith.gradleRunner()
                .withArguments("build")
                .build();

        // then
        var jpi = ith.inProjectDir("plugin-four/build/libs/plugin-four.jpi");
        var jar = ith.inProjectDir("plugin-four/build/libs/plugin-four.jar");
        var explodedWar = ith.inProjectDir("plugin-four/build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = Files.readLines(manifest, StandardCharsets.UTF_8);

        assertThat(manifestData)
                .contains("Jenkins-Version: 2.500")
                .contains("Plugin-Dependencies: plugin-three:unspecified");

        var jarFileInJpi = new File(explodedWar, "WEB-INF/lib/plugin-four.jar");
        assertThat(jarFileInJpi).exists();

    }

    @Test
    void multiModuleWithNestedDependenciesShouldLaunchServer(@TempDir File tempDir) throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir);
        configureModuleWithNestedDependencies(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, ":plugin-four:server");
    }

}
