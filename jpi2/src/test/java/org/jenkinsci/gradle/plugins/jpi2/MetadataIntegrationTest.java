package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class MetadataIntegrationTest extends V2IntegrationTestBase {

    private static final String FULL_METADATA_CONFIG = /* language=kotlin */ """
            jenkinsPlugin {
                homePage.set(uri("https://example.com/my-plugin"))
                compatibleSinceVersion.set("1.2")
                pluginFirstClassLoader.set(true)
                maskClasses.add("com.example.shaded")
                developers {
                    developer {
                        id.set("alice")
                        name.set("Alice Dev")
                        email.set("alice@example.com")
                    }
                    developer {
                        id.set("bob")
                        name.set("Bob Dev")
                        email.set("bob@example.com")
                    }
                }
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
            }
            """;

    @Test
    void manifestContainsHomePageAndCompatibleSinceVersion() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + FULL_METADATA_CONFIG)
                .getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("build").build();

        var manifest = new Manifest(ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF").toURI().toURL().openStream());
        var attrs = manifest.getMainAttributes();

        assertThat(attrs.getValue("Url")).isEqualTo("https://example.com/my-plugin");
        assertThat(attrs.getValue("Compatible-Since-Version")).isEqualTo("1.2");
        assertThat(attrs.getValue("PluginFirstClassLoader")).isEqualTo("true");
        assertThat(attrs.getValue("Mask-Classes")).isEqualTo("com.example.shaded");
        assertThat(attrs.getValue("Plugin-Developers"))
                .isEqualTo("Alice Dev:alice:alice@example.com,Bob Dev:bob:bob@example.com");
    }

    @Test
    void pomContainsDevelopersAndLicenses() throws IOException, XmlPullParserException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + FULL_METADATA_CONFIG)
                .getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("publish").build();

        var pom = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.pom");
        var model = new MavenXpp3Reader().read(new FileReader(pom));

        assertThat(model.getUrl()).isEqualTo("https://example.com/my-plugin");

        assertThat(model.getDevelopers())
                .extracting(Developer::getId, Developer::getName, Developer::getEmail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alice", "Alice Dev", "alice@example.com"),
                        org.assertj.core.groups.Tuple.tuple("bob", "Bob Dev", "bob@example.com")
                );

        assertThat(model.getLicenses())
                .extracting(License::getName, License::getUrl, License::getDistribution)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "Apache License, Version 2.0",
                                "https://www.apache.org/licenses/LICENSE-2.0",
                                "repo"
                        )
                );
    }

    @Test
    void defaultsApplyWhenMetadataNotConfigured() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write(getBasePluginConfig().getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.gradleRunner().withArguments("build").build();

        var manifest = new Manifest(ith.inProjectDir("build/jpi/META-INF/MANIFEST.MF").toURI().toURL().openStream());
        var attrs = manifest.getMainAttributes();

        assertThat(attrs.getValue("Url")).isNull();
        assertThat(attrs.getValue("Compatible-Since-Version")).isNull();
        assertThat(attrs.getValue("PluginFirstClassLoader")).isNull();
        assertThat(attrs.getValue("Mask-Classes")).isNull();
        assertThat(attrs.getValue("Plugin-Developers")).isNull();
    }
}
