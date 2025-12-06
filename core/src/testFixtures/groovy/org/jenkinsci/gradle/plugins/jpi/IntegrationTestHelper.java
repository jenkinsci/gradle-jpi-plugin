package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

public class IntegrationTestHelper {

    private final File projectDir;

    private final String minimumGradleVersion;

    public IntegrationTestHelper(File projectDir, String minimumGradleVersion) {
        this.projectDir = projectDir;
        this.minimumGradleVersion = minimumGradleVersion;
    }

    public IntegrationTestHelper(File projectDir) {
        this(projectDir, null);
    }

    public GradleRunner gradleRunner() throws IOException {
        return gradleRunner(WarningMode.ALL);
    }

    public GradleRunner gradleRunner(WarningMode warningMode) throws IOException {
        var gradleProperties = inProjectDir("gradle.properties");
        if (!existsRelativeToProjectDir("gradle.properties")) {
            var props = new Properties();
            props.setProperty("org.gradle.warning.mode", warningMode.name().toLowerCase(Locale.US));
            try (var outputStream = new FileOutputStream(gradleProperties)) {
                props.store(outputStream, "IntegrationSpec default generated values");
            }
        }
        var runner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir);
        var gradleVersion = getGradleVersionForTest(minimumGradleVersion);
        if (gradleVersion != GradleVersion.current()) {
            return runner.withGradleVersion(gradleVersion.getVersion());
        }
        return runner.withArguments("-Dorg.gradle.deprecation.trace=true");
    }


    public static GradleVersion getGradleVersionForTest(String minimumGradleVersion) {
        String gradleUnderTest = System.getProperty("gradle.under.test");
        var targetVersion = gradleUnderTest == null ? GradleVersion.current() : GradleVersion.version(gradleUnderTest);
        if (minimumGradleVersion == null) {
            return targetVersion;
        }
        var minimumVersion = GradleVersion.version(minimumGradleVersion);
        return targetVersion.compareTo(minimumVersion) < 0 ? minimumVersion : targetVersion;
    }

    public static boolean isBeforeJavaConventionDeprecation() {
        return getGradleVersionForTest(null).compareTo(GradleVersion.version("8.2")) < 0;
    }

    public static boolean isAfterJavaConventionDeprecation() {
        return !isBeforeJavaConventionDeprecation();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public boolean existsRelativeToProjectDir(String path) {
        return inProjectDir(path).exists();
    }

    public File inProjectDir(String path) {
        return new File(projectDir, path);
    }

    public File mkDirInProjectDir(String path) throws IOException {
        return Files.createDirectories(projectDir.toPath().resolve(path)).toFile();
    }

    public File touchInProjectDir(String path) throws IOException {
        return Files.createFile(projectDir.toPath().resolve(path)).toFile();
    }

}
