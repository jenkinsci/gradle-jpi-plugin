package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
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
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                apply(plugin = "groovy")
                dependencies {
                    implementation(localGroovy())
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example");
        ith.mkDirInProjectDir("src/main/groovy/com/example");
        Files.write((/* language=java */ """
                package com.example;
                public class JavaPlugin extends hudson.Plugin {
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/JavaPlugin.java"));
        Files.write((/* language=groovy */ """
                package com.example
                class GroovyPlugin extends hudson.Plugin {
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/groovy/com/example/GroovyPlugin.groovy"));

        var result = ith.gradleRunner().withArguments("checkOverlappingSources").buildAndFail();

        assertThat(result.getOutput())
                .contains(":checkOverlappingSources FAILED")
                .contains("Found multiple directories containing Jenkins plugin implementations");
    }

    @Test
    void checkOverlappingSourcesFailsForOverlappingSezpozFiles() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                apply(plugin = "groovy")
                dependencies {
                    implementation(localGroovy())
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example");
        ith.mkDirInProjectDir("src/main/groovy/com/example");
        Files.write((/* language=java */ """
                package com.example;
                @hudson.Extension
                public class JavaExtension {
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/JavaExtension.java"));
        Files.write((/* language=groovy */ """
                package com.example
                @hudson.Extension
                class GroovyExtension {
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/groovy/com/example/GroovyExtension.groovy"));

        var result = ith.gradleRunner().withArguments("checkOverlappingSources").buildAndFail();

        assertThat(result.getOutput())
                .contains(":checkOverlappingSources FAILED")
                .contains("Found overlapping Sezpoz file:");
    }
}
