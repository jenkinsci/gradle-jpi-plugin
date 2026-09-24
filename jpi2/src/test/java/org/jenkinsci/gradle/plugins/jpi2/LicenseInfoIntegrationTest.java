package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

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
