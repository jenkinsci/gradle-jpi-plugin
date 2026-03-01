package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.BuildResult;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class VersionSourceIntegrationTest extends V2IntegrationTestBase {

    private static final Pattern GIT_HASH = Pattern.compile("[a-f0-9]{40}");

    @Test
    void projectVersionIsUsedByDefault() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("build").build();

        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new java.util.jar.Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData.getValue("Plugin-Version")).isEqualTo("1.0.0");
        assertThat(manifestData.getValue("Implementation-Version")).isEqualTo("1.0.0");

        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        assertThat(jpi).exists();
    }

    @Test
    void projectVersionAppearsInPublishedPomAndJpi() throws IOException, XmlPullParserException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("publish").build();

        var expectedVersion = "1.0.0";
        assertPomAndJpiContainVersion(ith, expectedVersion);
    }

    @Test
    void fixedVersionIsUsedWhenSet() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.FIXED)
                    fixedVersion.set("2.0.0-beta")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("build").build();

        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new java.util.jar.Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData.getValue("Plugin-Version")).isEqualTo("2.0.0-beta");
        assertThat(manifestData.getValue("Implementation-Version")).isEqualTo("2.0.0-beta");

        var jpi = ith.inProjectDir("build/libs/test-plugin-2.0.0-beta.jpi");
        assertThat(jpi).exists();
    }

    @Test
    void fixedVersionFromProviderIsUsedWhenSet() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.FIXED)
                    fixedVersion.set(providers.provider { "2.0.0-beta" })
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("build").build();

        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new java.util.jar.Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData.getValue("Plugin-Version")).isEqualTo("2.0.0-beta");
        assertThat(manifestData.getValue("Implementation-Version")).isEqualTo("2.0.0-beta");

        var jpi = ith.inProjectDir("build/libs/test-plugin-2.0.0-beta.jpi");
        assertThat(jpi).exists();
    }

    @Test
    void fixedVersionAppearsInPublishedPomAndJpi() throws IOException, XmlPullParserException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.FIXED)
                    fixedVersion.set("2.0.0-beta")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("publish").build();

        assertPomAndJpiContainVersion(ith, "2.0.0-beta");
    }

    @Test
    void generateGitVersionTaskProducesVersionFile() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    gitVersion {
                        allowDirty.set(true)
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        initGitRepo(ith.inProjectDir("."));

        ith.gradleRunner().withArguments("generateGitVersion").build();

        var versionFile = ith.inProjectDir("build/generated/version/version.txt");
        assertThat(versionFile).exists();
        var lines = Files.readLines(versionFile, StandardCharsets.UTF_8);
        assertThat(lines).hasSizeGreaterThanOrEqualTo(2);
        // First line: depth.abbrevHash (e.g. 1.abc123def456)
        assertThat(lines.get(0)).matches("\\d+\\.[a-f0-9]{12}");
        assertThat(GIT_HASH.matcher(lines.get(1).trim()).matches()).isTrue();
    }

    @Test
    void gitVersionSourceUsesGeneratedVersionInBuild() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.GIT)
                    gitVersion {
                        allowDirty.set(true)
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        initGitRepo(ith.inProjectDir("."));

        ith.gradleRunner().withArguments("build").build();

        var versionFile = ith.inProjectDir("build/generated/version/version.txt");
        assertThat(versionFile).exists();
        var expectedVersion = Files.readLines(versionFile, StandardCharsets.UTF_8).get(0).trim();

        var manifest = ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new java.util.jar.Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifestData.getValue("Plugin-Version")).isEqualTo(expectedVersion);
        assertThat(manifestData.getValue("Implementation-Version")).isEqualTo(expectedVersion);

        var jpi = ith.inProjectDir("build/libs/test-plugin-" + expectedVersion + ".jpi");
        assertThat(jpi).exists();
    }

    @Test
    void gitVersionAppearsInPublishedPomAndJpi() throws IOException, InterruptedException, XmlPullParserException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        initGitRepo(ith.inProjectDir("."));
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.GIT)
                    gitVersion {
                        allowDirty.set(true)
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("publish").build();

        var versionFile = ith.inProjectDir("build/generated/version/version.txt");
        assertThat(versionFile).exists();
        var expectedVersion = Files.readLines(versionFile, StandardCharsets.UTF_8).get(0).trim();
        assertPomAndJpiContainVersion(ith, expectedVersion);
    }

    @Test
    void generateGitVersionFailsWhenDirtyAndAllowDirtyFalse() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        initGitRepo(ith.inProjectDir("."));
        Files.write("dirty".getBytes(StandardCharsets.UTF_8), ith.inProjectDir("uncommitted.txt"));

        BuildResult result = ith.gradleRunner().withArguments("generateGitVersion").buildAndFail();

        assertThat(result.getOutput()).contains("uncommitted changes");
    }

    @Test
    void generateGitVersionSucceedsWhenAllowDirtyTrue() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    gitVersion {
                        allowDirty.set(true)
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        initGitRepo(ith.inProjectDir("."));
        Files.write("dirty".getBytes(StandardCharsets.UTF_8), ith.inProjectDir("uncommitted.txt"));

        ith.gradleRunner().withArguments("generateGitVersion").build();

        var versionFile = ith.inProjectDir("build/generated/version/version.txt");
        assertThat(versionFile).exists();
    }

    @Test
    void generateGitVersionWithCustomFormat() throws IOException, InterruptedException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    gitVersion {
                        versionFormat.set("rc-%d.%s")
                        abbrevLength.set(10)
                        allowDirty.set(true)
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        initGitRepo(ith.inProjectDir("."));

        ith.gradleRunner().withArguments("generateGitVersion").build();

        var versionFile = ith.inProjectDir("build/generated/version/version.txt");
        assertThat(versionFile).exists();
        var firstLine = Files.readLines(versionFile, StandardCharsets.UTF_8).get(0);
        assertThat(firstLine).startsWith("rc-");
        assertThat(firstLine).matches("rc-\\d+\\.[a-f0-9]{10}");
    }

    @Test
    void generateGitVersionFailsWhenNotAGitRepository() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        BuildResult result = ith.gradleRunner().withArguments("generateGitVersion").buildAndFail();

        assertThat(result.getOutput()).contains("Not a Git repository");
    }

    private static void assertPomAndJpiContainVersion(IntegrationTestHelper ith, String expectedVersion) throws IOException, XmlPullParserException {
        var pom = ith.inProjectDir("build/repo/com/example/test-plugin/" + expectedVersion + "/test-plugin-" + expectedVersion + ".pom");
        assertThat(pom).exists();
        var reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom, StandardCharsets.UTF_8));
        assertThat(model.getVersion()).isEqualTo(expectedVersion);

        var jpi = ith.inProjectDir("build/repo/com/example/test-plugin/" + expectedVersion + "/test-plugin-" + expectedVersion + ".jpi");
        assertThat(jpi).exists();
        try (var jar = new JarFile(jpi)) {
            var manifest = jar.getManifest();
            assertThat(manifest).isNotNull();
            assertThat(manifest.getMainAttributes().getValue("Plugin-Version")).isEqualTo(expectedVersion);
        }
    }

    private static void initGitRepo(File projectDir) throws IOException, InterruptedException {
        runGit(projectDir, "init");
        Files.write(/* language=gitignore */ """
                .gradle
                build
                """.getBytes(StandardCharsets.UTF_8), new File(projectDir, ".gitignore"));
        runGit(projectDir, "add", ".");
        runGit(projectDir, "config", "user.email", "test@test");
        runGit(projectDir, "config", "user.name", "Test");
        runGit(projectDir, "commit", "-m", "Initial commit");
    }

    private static void runGit(File workDir, String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        var process = pb.start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git " + String.join(" ", args) + " timed out: " + output);
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " failed: " + output);
        }
    }
}
