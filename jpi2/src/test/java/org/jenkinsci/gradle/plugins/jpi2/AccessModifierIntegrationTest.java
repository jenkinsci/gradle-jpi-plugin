package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class AccessModifierIntegrationTest extends V2IntegrationTestBase {

    @Test
    void checkLifecycleRunsCheckAccessModifier() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write((/* language=java */ """
                package com.example.plugin;
                public class Example {
                    public String value() {
                        return "ok";
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/Example.java"));

        // when
        var result = ith.gradleRunner().withArguments("check").build();

        // then
        assertThat(result.getOutput()).contains(":checkAccessModifier").contains("BUILD SUCCESSFUL");
        assertThat(ith.inProjectDir("build/access-modifier/main-java.txt")).exists();
    }

    @Test
    void checkAccessModifierFailsWhenIgnoreFailuresDisabled() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                tasks.named<org.jenkinsci.gradle.plugins.jpi2.accmod.CheckAccessModifierTask>("checkAccessModifier") {
                    ignoreFailures.set(false)
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));
        ith.mkDirInProjectDir("src/main/java/org/example/restricted");
        ith.mkDirInProjectDir("src/main/java/org/example/blessed");
        Files.write((/* language=java */ """
                package org.example.restricted;
                import org.kohsuke.accmod.Restricted;
                import org.kohsuke.accmod.restrictions.DoNotUse;
                @Restricted(DoNotUse.class)
                public class OhNo {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/org/example/restricted/OhNo.java"));
        Files.write((/* language=java */ """
                package org.example.blessed;
                import org.example.restricted.OhNo;
                public class Consumer {
                    public int consume() {
                        return new OhNo().add(1, 2);
                    }
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/org/example/blessed/Consumer.java"));

        // when
        var result = ith.gradleRunner().withArguments("checkAccessModifier").buildAndFail();

        // then
        assertThat(result.getOutput())
                .contains(":checkAccessModifier FAILED")
                .contains("org/example/restricted/OhNo")
                .contains("must not be used");
    }
}
