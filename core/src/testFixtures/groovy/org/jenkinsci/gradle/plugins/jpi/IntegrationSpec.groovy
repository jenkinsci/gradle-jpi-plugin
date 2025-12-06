package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

@CompileStatic
class IntegrationSpec extends Specification {
    @TempDir
    protected File projectDir

    protected GradleRunner gradleRunner(WarningMode warningMode = WarningMode.FAIL) {
        new IntegrationTestHelper(projectDir).gradleRunner(warningMode)
    }

    static GradleVersion getGradleVersionForTest() {
        IntegrationTestHelper.getGradleVersionForTest(null)
    }

    static boolean isBeforeJavaConventionDeprecation() {
        IntegrationTestHelper.isBeforeJavaConventionDeprecation()
    }

    static boolean isAfterJavaConventionDeprecation() {
        IntegrationTestHelper.isAfterJavaConventionDeprecation()
    }

    static boolean isWindows() {
        IntegrationTestHelper.isWindows()
    }

    boolean existsRelativeToProjectDir(String path) {
        new IntegrationTestHelper(projectDir).existsRelativeToProjectDir(path)
    }

    File inProjectDir(String path) {
        new IntegrationTestHelper(projectDir).inProjectDir(path)
    }

    File mkDirInProjectDir(String path) {
        new IntegrationTestHelper(projectDir).mkDirInProjectDir(path)
    }

    File touchInProjectDir(String path) {
        new IntegrationTestHelper(projectDir).touchInProjectDir(path)
    }
}
