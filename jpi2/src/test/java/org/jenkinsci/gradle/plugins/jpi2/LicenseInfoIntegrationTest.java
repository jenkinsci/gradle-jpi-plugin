package org.jenkinsci.gradle.plugins.jpi2;

import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class LicenseInfoIntegrationTest extends V2IntegrationTestBase {

    @Test
    void generateLicenseInfoShouldRunAndPackageLicenseReport() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        // when
        var result = ith.gradleRunner().withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("generateLicenseInfo");

        var generatedLicenseInfo = ith.inProjectDir("build/licenses/licenses.xml");
        assertThat(generatedLicenseInfo).exists();
        assertThat(Files.readString(generatedLicenseInfo.toPath()))
                .contains("<l:dependencies")
                .contains("artifactId=\"test-plugin\"");

        var packagedLicenseInfo = ith.inProjectDir("build/jpi/WEB-INF/licenses.xml");
        assertThat(packagedLicenseInfo).exists();
    }
}
