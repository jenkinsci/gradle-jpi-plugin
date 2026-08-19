package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.nio.file.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces <a href="https://github.com/jenkinsci/gradle-jpi-plugin/issues/429">#429</a>: a plugin
 * that upgraded JUnit past the version jenkins-core drags in transitively could not resolve its
 * test classpath at all, and applying {@code com.jfrog.artifactory} replaced Gradle's conflict
 * report with an opaque "dependency has no dependents and is not root" from its own resolution
 * listener.
 */
class ArtifactoryInteropIntegrationTest extends V2IntegrationTestBase {

    /**
     * jenkins-core 2.541.1 pulls org.junit:junit-bom:5.14.0 in transitively via spotbugs-annotations.
     * Since a BOM carries no artifacts, aligning it with core buys nothing and only blocks upgrades.
     */
    private static final String NEWER_THAN_JENKINS_CORE = "5.14.4";

    @Test
    void allowsUpgradingJunitPastTheVersionJenkinsCoreBringsIn() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("gradle.properties").toPath(), /* language=properties */ """
                jenkins.version=2.541.1
                """);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), /* language=kotlin */ """
                plugins {
                    id("org.jenkins-ci.jpi2")
                    id("com.jfrog.artifactory") version "5.2.5"
                }
                repositories {
                    mavenCentral()
                    jenkinsPublic()
                }
                group = "com.example"
                version = "1.0.0"
                dependencies {
                    testImplementation(platform("org.junit:junit-bom:%s"))
                }
                """.formatted(NEWER_THAN_JENKINS_CORE));

        var result = ith.gradleRunner()
                .withArguments("dependencyInsight", "--configuration", "testDefaultRuntime",
                        "--dependency", "org.junit.jupiter:junit-jupiter-api")
                .build();

        assertThat(result.getOutput())
                .contains("BUILD SUCCESSFUL")
                .contains("org.junit.jupiter:junit-jupiter-api:" + NEWER_THAN_JENKINS_CORE);
    }

}
