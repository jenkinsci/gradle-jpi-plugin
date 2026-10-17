package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 5, unit = TimeUnit.MINUTES)
abstract class V2IntegrationTestBase {

    @TempDir(cleanup = CleanupMode.NEVER)
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

    /*
     * Generated build scripts must be identical across tests: Gradle caches compiled Kotlin DSL
     * scripts by content and classpath, so anything per-test (ports, temp-dir paths) forces a
     * recompile of every script in every test. Ports go on the command line via -Pserver.port.
     */
    @NotNull
    static String getBasePluginConfig() {
        return /* language=kotlin */ """
                plugins {
                    id("org.jenkins-ci.jpi2")
                }
                repositories {
                    mavenCentral()
                    jenkinsPublic()
                }
                tasks.named<JavaExec>("server") {
                    maxHeapSize = "512m"
                }
                tasks.named<JavaExec>("hplRun") {
                    maxHeapSize = "512m"
                }
                tasks.withType(Test::class) {
                    useJUnitPlatform()
                }
                """ + getPublishingConfig();
    }

    @NotNull
    static String getBasePluginConfigWithBuildscriptClasspath(String pluginJarPath) {
        return String.format(/* language=kotlin */ """
                buildscript {
                    dependencies {
                        classpath(files("%s"))
                    }
                }
                apply(plugin = "org.jenkins-ci.jpi2")
                repositories {
                    mavenCentral()
                    jenkinsPublic()
                }
                tasks.named<JavaExec>("server") {
                    maxHeapSize = "512m"
                }
                tasks.named<JavaExec>("hplRun") {
                    maxHeapSize = "512m"
                }
                tasks.withType(Test::class) {
                    useJUnitPlatform()
                }
                group = "com.example"
                version = "1.0.0"
                """, pluginJarPath.replace("\\", "\\\\"));
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
        Files.writeString(ith.inProjectDir("settings.gradle.kts").toPath(), /* language=kotlin */ """
                rootProject.name = "test-plugin"
                """);
    }

    static class TapWriter extends Writer {
        private final Writer writer1;
        private final Writer writer2;

        /** Creates a writer that copies output to both destinations. */
        public TapWriter(Writer writer1, Writer writer2) {
            this.writer1 = writer1;
            this.writer2 = writer2;
        }

        @Override
        public void write(char @NotNull [] cbuf, int off, int len) throws IOException {
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

    static void testServerStarts(GradleRunner gradleRunner, String... task) throws InterruptedException {
        var stdout1 = new StringWriter();
        var stdout2 = new StringWriter();
        var stdout = new TapWriter(stdout1, stdout2);
        var stderr1 = new StringWriter();
        var stderr2 = new StringWriter();
        var stderr = new TapWriter(stderr1, stderr2);
        var serverThread = Executors.newSingleThreadExecutor();
        final AtomicReference<BuildResult> buildResult = new AtomicReference<>();
        serverThread.submit(() -> buildResult.set(gradleRunner
                .withArguments(withServerPort(task))
                .forwardStdError(stderr)
                .forwardStdOutput(stdout)
                .build()));
        Awaitility.await()
                .atMost(3, TimeUnit.MINUTES)
                .pollInterval(200, TimeUnit.MILLISECONDS)
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

    private static String[] withServerPort(String... args) {
        var withPort = Arrays.copyOf(args, args.length + 1);
        withPort[args.length] = "-Pserver.port=" + RandomPortProvider.findFreePort();
        return withPort;
    }

    static void configureSimpleBuild(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig());
    }

    static void configureSimpleBuildForVerification(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        var pluginJar = materializePluginJar();
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(),
                getBasePluginConfigWithBuildscriptClasspath(pluginJar.getAbsolutePath()));
    }

    static void configureTwoPluginsForVerification(IntegrationTestHelper ith) throws IOException {
        var pluginJar = materializePluginJar();
        Files.writeString(
                ith.inProjectDir("settings.gradle.kts").toPath(), /* language=kotlin */ """
                rootProject.name = "test-plugin"
                include("upstream", "downstream")
                """, StandardCharsets.UTF_8);
        Files.writeString(
                ith.inProjectDir("gradle.properties").toPath(), /* language=properties */ """
                jenkins.version=2.492.3
                org.gradle.warning.mode=all
                """, StandardCharsets.UTF_8);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), "", StandardCharsets.UTF_8);

        ith.mkDirInProjectDir("upstream/src/main/java/com/example/upstream");
        Files.writeString(
                ith.inProjectDir("upstream/build.gradle.kts").toPath(),
                getBasePluginConfigWithBuildscriptClasspath(pluginJar.getAbsolutePath()),
                StandardCharsets.UTF_8);
        Files.writeString(
                ith.inProjectDir("upstream/src/main/java/com/example/upstream/Example.java")
                        .toPath(),
                /* language=java */ """
                        package com.example.upstream;
                        public class Example { public String hello() { return "v1"; } }
                        """,
                StandardCharsets.UTF_8);

        ith.mkDirInProjectDir("downstream/src/main/java/com/example/downstream");
        Files.writeString(
                ith.inProjectDir("downstream/build.gradle.kts").toPath(),
                getBasePluginConfigWithBuildscriptClasspath(pluginJar.getAbsolutePath()) + /* language=kotlin */ """
                        dependencies {
                            "implementation"(project(":upstream"))
                        }
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                ith.inProjectDir("downstream/src/main/java/com/example/downstream/Example.java")
                        .toPath(),
                /* language=java */ """
                        package com.example.downstream;
                        public class Example { public String hello() { return "v1"; } }
                        """,
                StandardCharsets.UTF_8);
    }

    static void configureBuildWithOssPluginDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """);
    }

    static void configureBuildWithOssLibraryDependency(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """);
    }

    static void configureBuildWithApplicationPlugin(IntegrationTestHelper ith) throws IOException {
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), /* language=kotlin */ """
                plugins {
                    application
                    id("org.jenkins-ci.jpi2")
                }
                repositories {
                    mavenCentral()
                    jenkinsPublic()
                }
                application {
                    mainClass.set("com.example.Main")
                }
                """ + getPublishingConfig());
    }

    static void configureModuleWithNestedDependencies(IntegrationTestHelper ith) throws IOException {
        Files.writeString(ith.inProjectDir("settings.gradle.kts").toPath(), /* language=kotlin */ """
                rootProject.name = "test-plugin"
                include("library-one", "library-two", "plugin-three", "plugin-four")
                """);
        Files.writeString(ith.inProjectDir("gradle.properties").toPath(), /* language=properties */ """
                jenkins.version=2.492.3
                """);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), "");
        ith.mkDirInProjectDir("library-one");
        Files.writeString(
                ith.inProjectDir("library-one/build.gradle.kts").toPath(),
                getBaseLibraryConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """);
        ith.mkDirInProjectDir("library-one/src/main/java/com/example/lib1");
        Files.writeString(
                ith.inProjectDir("library-one/src/main/java/com/example/lib1/Example.java")
                        .toPath(), /* language=java */
                """
                package com.example.lib1;
                import com.github.rahulsom.nothing.java.Foo;
                public class Example {
                    public String hello() {
                        return "Hello";
                    }
                }
                """);
        ith.mkDirInProjectDir("library-two");
        Files.writeString(
                ith.inProjectDir("library-two/build.gradle.kts").toPath(),
                getBaseLibraryConfig() + /* language=kotlin */ """
                dependencies {
                    implementation(project(":library-one"))
                }
                """);
        ith.mkDirInProjectDir("library-two/src/main/java/com/example/lib2");
        Files.writeString(
                ith.inProjectDir("library-two/src/main/java/com/example/lib2/ExampleTwo.java")
                        .toPath(), /* language=java */
                """
                package com.example.lib2;
                import com.example.lib1.Example;
                public class ExampleTwo {
                    public String hello() {
                        return new Example().hello();
                    }
                }
                """);
        ith.mkDirInProjectDir("plugin-three");
        Files.writeString(
                ith.inProjectDir("plugin-three/build.gradle.kts").toPath(),
                getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation(project(":library-two"))
                    implementation("org.jenkins-ci.plugins:git:5.7.0")
                }
                """);
        ith.mkDirInProjectDir("plugin-three/src/main/java/com/example/plugin3");
        Files.writeString(
                ith.inProjectDir("plugin-three/src/main/java/com/example/plugin3/ExampleThree.java")
                        .toPath(), /* language=java */
                """
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
                """);
        ith.mkDirInProjectDir("plugin-four");
        Files.writeString(
                ith.inProjectDir("plugin-four/build.gradle.kts").toPath(),
                getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation(project(":plugin-three"))
                }
                """);
        ith.mkDirInProjectDir("plugin-four/src/main/java/com/example/plugin4");
        Files.writeString(
                ith.inProjectDir("plugin-four/src/main/java/com/example/plugin4/ExampleFour.java")
                        .toPath(), /* language=java */
                """
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
                """);
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

    private static File cachedPluginJar;

    /**
     * The jar's path ends up in the generated build script, so it lives at a content-addressed
     * location shared by all tests instead of in each test's temp dir; that keeps the script text
     * stable and lets Gradle reuse the compiled script. Entries are written in a fixed order with a
     * fixed timestamp so unchanged plugin classes always produce the same jar.
     */
    @NotNull
    private static synchronized File materializePluginJar() throws IOException {
        if (cachedPluginJar != null) {
            return cachedPluginJar;
        }
        var roots = List.of(
                getCodeSourceRoot(V2JpiPlugin.class),
                getCodeSourceRoot(JenkinsPluginExtension.class),
                getResourceRoot("META-INF/gradle-plugins/org.jenkins-ci.jpi2.properties"));
        var entries = new HashSet<String>();
        var bytes = new ByteArrayOutputStream();
        try (var jarOutputStream = new JarOutputStream(bytes)) {
            for (var root : roots) {
                addDirectoryToJar(root.toPath(), jarOutputStream, entries);
            }
        }
        var jarBytes = bytes.toByteArray();
        var dir = Path.of(System.getProperty("java.io.tmpdir"), "jpi2-plugin-under-test");
        Files.createDirectories(dir);
        var jar = dir.resolve("jpi2-under-test-" + sha256(jarBytes) + ".jar");
        if (!Files.exists(jar)) {
            // Another test JVM may be writing the same jar; move atomically so readers never see a partial file.
            var tmp = Files.createTempFile(dir, "jpi2-under-test-", ".tmp");
            Files.write(tmp, jarBytes);
            try {
                Files.move(tmp, jar, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                Files.deleteIfExists(tmp);
            }
        }
        cachedPluginJar = jar.toFile();
        return cachedPluginJar;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static File getCodeSourceRoot(Class<?> type) {
        try {
            return new File(
                    type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to locate code source for " + type.getName(), e);
        }
    }

    private static File getResourceRoot(String resourcePath) {
        var resource = V2IntegrationTestBase.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new RuntimeException("Unable to locate resource " + resourcePath);
        }
        try {
            File current = new File(resource.toURI());
            for (var ignored : resourcePath.split("/")) {
                current = current.getParentFile();
            }
            return current;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to locate resource root for " + resourcePath, e);
        }
    }

    private static void addDirectoryToJar(Path root, JarOutputStream jarOutputStream, Set<String> entries)
            throws IOException {
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        for (var path : files) {
            var entryName = root.relativize(path).toString().replace(File.separatorChar, '/');
            if (!entries.add(entryName)) {
                continue;
            }
            var entry = new JarEntry(entryName);
            entry.setTime(0);
            jarOutputStream.putNextEntry(entry);
            Files.copy(path, jarOutputStream);
            jarOutputStream.closeEntry();
        }
    }

    static void deleteDirectory(File dir) throws IOException {
        if (!dir.exists()) return;
        try (var paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
