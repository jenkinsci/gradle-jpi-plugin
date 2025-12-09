package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 2, unit = TimeUnit.MINUTES)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
abstract class V2IntegrationTestBase {

    @TempDir
    File tempDir;

    @NotNull
    static String getPublishingConfig() {
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
    static String getBasePluginConfig() {
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
                tasks.withType(Test::class) {
                    useJUnitPlatform()
                }
                """, RandomPortProvider.findFreePort()) + getPublishingConfig();
    }

    @NotNull
    static String getBaseLibraryConfig() {
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

    static void initBuild(IntegrationTestHelper ith) throws IOException {
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

    static void testServerStarts(GradleRunner gradleRunner, String... task) throws InterruptedException {
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

    static void configureSimpleBuild(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    static void configureBuildWithOssPluginDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    static void configureBuildWithOssLibraryDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
    }

    static void configureModuleWithNestedDependencies(IntegrationTestHelper ith) throws IOException {
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

    static void assertDependencyTreesMatch(List<String> actualList, List<String> expectedList) {
        var actualDeps = actualList.stream()
                .filter(line -> line.contains("---") && !line.startsWith("---"))
                .map(String::trim)
                .toList();
        var expectedDeps = expectedList.stream()
                .filter(line -> line.contains("---") && !line.startsWith("---"))
                .map(String::trim)
                .toList();
        assertThat(actualDeps).containsExactlyElementsOf(expectedDeps);
    }

    @SuppressWarnings("unused")
    static File repro() {
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
