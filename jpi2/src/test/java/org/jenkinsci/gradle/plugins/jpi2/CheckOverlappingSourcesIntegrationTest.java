package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class CheckOverlappingSourcesIntegrationTest extends V2IntegrationTestBase {

    @Test
    void checkLifecycleRunsCheckOverlappingSources() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        var firstRun = ith.gradleRunner().withArguments("check").build();
        var secondRun = ith.gradleRunner().withArguments("check").build();

        assertThat(firstRun.task(":checkOverlappingSources").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondRun.task(":checkOverlappingSources").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(ith.inProjectDir("build/check-overlap/discovered.txt")).exists();
    }

    @Test
    void checkOverlappingSourcesFailsForMultiplePluginImplementations() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                apply(plugin = "groovy")
                dependencies {
                    implementation(localGroovy())
                }
                """);
        ith.mkDirInProjectDir("src/main/java/com/example");
        ith.mkDirInProjectDir("src/main/groovy/com/example");
        Files.writeString(
                ith.inProjectDir("src/main/java/com/example/JavaPlugin.java").toPath(), /* language=java */ """
                package com.example;
                public class JavaPlugin extends hudson.Plugin {
                }
                """);
        Files.writeString(
                ith.inProjectDir("src/main/groovy/com/example/GroovyPlugin.groovy")
                        .toPath(), /* language=groovy */
                """
                package com.example
                class GroovyPlugin extends hudson.Plugin {
                }
                """);

        var result = ith.gradleRunner().withArguments("checkOverlappingSources").buildAndFail();

        assertThat(result.getOutput())
                .contains(":checkOverlappingSources FAILED")
                .contains("Found multiple directories containing Jenkins plugin implementations");
    }

    @Test
    void checkOverlappingSourcesFailsForOverlappingSezpozFiles() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(
                ith.inProjectDir("build.gradle.kts").toPath(), getBasePluginConfig() + /* language=kotlin */ """
                apply(plugin = "groovy")
                dependencies {
                    implementation(localGroovy())
                }
                """);
        ith.mkDirInProjectDir("src/main/java/com/example");
        ith.mkDirInProjectDir("src/main/groovy/com/example");
        Files.writeString(
                ith.inProjectDir("src/main/java/com/example/JavaExtension.java").toPath(), /* language=java */ """
                package com.example;
                @hudson.Extension
                public class JavaExtension {
                }
                """);
        Files.writeString(
                ith.inProjectDir("src/main/groovy/com/example/GroovyExtension.groovy")
                        .toPath(), /* language=groovy */
                """
                package com.example
                @hudson.Extension
                class GroovyExtension {
                }
                """);

        var result = ith.gradleRunner().withArguments("checkOverlappingSources").buildAndFail();

        assertThat(result.getOutput())
                .contains(":checkOverlappingSources FAILED")
                .contains("Found overlapping Sezpoz file:");
    }
}
