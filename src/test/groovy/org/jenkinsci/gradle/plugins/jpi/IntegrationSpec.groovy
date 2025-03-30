package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

@CompileStatic
class IntegrationSpec extends Specification {
    @TempDir
    protected File projectDir

    protected GradleRunner gradleRunner(WarningMode warningMode = WarningMode.FAIL) {
        def gradleProperties = inProjectDir('gradle.properties')
        if (!existsRelativeToProjectDir('gradle.properties')) {
            def props = new Properties()
            props.setProperty('org.gradle.warning.mode', warningMode.name().toLowerCase(Locale.US))
            gradleProperties.withOutputStream {
                props.store(it, 'IntegrationSpec default generated values')
            }
        }
        def runner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir)
        def gradleVersion = gradleVersionForTest
        if (gradleVersion != GradleVersion.current()) {
            return runner.withGradleVersion(gradleVersion.version)
        }
        runner.withArguments('-Dorg.gradle.deprecation.trace=true')
    }

    static GradleVersion getGradleVersionForTest() {
        System.getProperty('gradle.under.test')?.with { GradleVersion.version(delegate) } ?: GradleVersion.current()
    }

    static boolean isBeforeJavaConventionDeprecation() {
        gradleVersionForTest < GradleVersion.version('8.2')
    }

    static boolean isAfterJavaConventionDeprecation() {
        !isBeforeJavaConventionDeprecation()
    }

    static boolean isWindows() {
        System.getProperty('os.name').toLowerCase().contains('windows')
    }

    boolean existsRelativeToProjectDir(String path) {
        inProjectDir(path).exists()
    }

    File inProjectDir(String path) {
        new File(projectDir, path)
    }

    File mkDirInProjectDir(String path) {
        Files.createDirectories(projectDir.toPath().resolve(path)).toFile()
    }

    File touchInProjectDir(String path) {
        Files.createFile(projectDir.toPath().resolve(path)).toFile()
    }
}
