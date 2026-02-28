package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class ArchiveExtensionIntegrationTest extends V2IntegrationTestBase {

    @Test
    void defaultExtensionProducesJpiArtifact() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        // when
        ith.gradleRunner().withArguments("jpi").build();

        // then
        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        var hpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.hpi");
        assertThat(jpi).exists();
        assertThat(hpi).doesNotExist();
    }

    @Test
    void archiveExtensionSetToHpiProducesHpiArtifact() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    archiveExtension.set("hpi")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        // when
        ith.gradleRunner().withArguments("jpi").build();

        // then
        var hpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.hpi");
        var jpi = ith.inProjectDir("build/libs/test-plugin-1.0.0.jpi");
        assertThat(hpi).exists();
        assertThat(jpi).doesNotExist();
    }

    @Test
    void archiveExtensionSetToHpiPomPackagingIsHpi() throws IOException, XmlPullParserException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    archiveExtension.set("hpi")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        // when
        ith.gradleRunner().withArguments("publish").build();

        // then
        var hpi = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.hpi");
        var jpi = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jpi");
        assertThat(hpi).exists();
        assertThat(jpi).doesNotExist();

        var pom = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.pom");
        var reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom));
        assertThat(model.getPackaging()).isEqualTo("hpi");
    }

    @Test
    void prepareServerWithHpiExtensionProducesHpiInWorkPlugins() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                jenkinsPlugin {
                    archiveExtension.set("hpi")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        // when
        ith.gradleRunner().withArguments("prepareServer").build();

        // then
        var hpi = ith.inProjectDir("work/plugins/test-plugin.hpi");
        var jpi = ith.inProjectDir("work/plugins/test-plugin.jpi");
        assertThat(hpi).exists();
        assertThat(jpi).doesNotExist();
    }

    @Test
    void prepareServerWithDefaultExtensionProducesJpiInWorkPlugins() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureSimpleBuild(ith);

        // when
        ith.gradleRunner().withArguments("prepareServer").build();

        // then
        var jpi = ith.inProjectDir("work/plugins/test-plugin.jpi");
        var hpi = ith.inProjectDir("work/plugins/test-plugin.hpi");
        assertThat(jpi).exists();
        assertThat(hpi).doesNotExist();
    }
}
