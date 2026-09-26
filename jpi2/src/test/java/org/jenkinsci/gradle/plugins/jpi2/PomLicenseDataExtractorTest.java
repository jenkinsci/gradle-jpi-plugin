package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jenkinsci.gradle.plugins.jpi2.GenerateLicenseInfoTask.LicenseInfo;
import org.jenkinsci.gradle.plugins.jpi2.GenerateLicenseInfoTask.PomLicenseDataExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomLicenseDataExtractorTest {

    @TempDir
    Path tempDir;

    private PomLicenseDataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PomLicenseDataExtractor();
    }

    @Test
    void extractsDirectGavAndLicenses() throws IOException {
        var pom = writePom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>library</artifactId>
                  <version>1.2.3</version>
                  <name>Example Library</name>
                  <description>A library</description>
                  <url>https://example.com</url>
                  <licenses>
                    <license>
                      <name>MIT</name>
                      <url>https://opensource.org/licenses/MIT</url>
                    </license>
                    <license>
                      <name>Apache-2.0</name>
                      <url>https://www.apache.org/licenses/LICENSE-2.0</url>
                    </license>
                  </licenses>
                </project>
                """);

        var data = extractor.extractFrom(pom.toFile());

        assertThat(data.groupId()).isEqualTo("com.example");
        assertThat(data.artifactId()).isEqualTo("library");
        assertThat(data.version()).isEqualTo("1.2.3");
        assertThat(data.name()).isEqualTo("Example Library");
        assertThat(data.description()).isEqualTo("A library");
        assertThat(data.url()).isEqualTo("https://example.com");
        assertThat(data.licenses())
                .extracting(LicenseInfo::name, LicenseInfo::url)
                .containsExactly(
                        tuple("MIT", "https://opensource.org/licenses/MIT"),
                        tuple("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"));
    }

    @Test
    void fallsBackToParentGroupIdAndVersionWhenMissing() throws IOException {
        var pom = writePom("""
                <project>
                  <parent>
                    <groupId>com.parent</groupId>
                    <artifactId>parent</artifactId>
                    <version>9.9.9</version>
                  </parent>
                  <artifactId>child</artifactId>
                  <name>Child</name>
                </project>
                """);

        var data = extractor.extractFrom(pom.toFile());

        assertThat(data.groupId()).isEqualTo("com.parent");
        assertThat(data.artifactId()).isEqualTo("child");
        assertThat(data.version()).isEqualTo("9.9.9");
        assertThat(data.licenses()).isEmpty();
    }

    @Test
    void prefersDirectGroupIdAndVersionOverParent() throws IOException {
        var pom = writePom("""
                <project>
                  <parent>
                    <groupId>com.parent</groupId>
                    <artifactId>parent</artifactId>
                    <version>9.9.9</version>
                  </parent>
                  <groupId>com.child</groupId>
                  <artifactId>child</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        var data = extractor.extractFrom(pom.toFile());

        assertThat(data.groupId()).isEqualTo("com.child");
        assertThat(data.version()).isEqualTo("1.0.0");
    }

    @Test
    void includesBlankLicenseFieldsRatherThanSkippingThem() throws IOException {
        var pom = writePom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>library</artifactId>
                  <version>1.0.0</version>
                  <licenses>
                    <license>
                      <name></name>
                      <url></url>
                    </license>
                    <license>
                      <name>MIT</name>
                    </license>
                  </licenses>
                </project>
                """);

        var data = extractor.extractFrom(pom.toFile());

        assertThat(data.licenses())
                .extracting(LicenseInfo::name, LicenseInfo::url)
                .containsExactly(tuple("", ""), tuple("MIT", ""));
    }

    @Test
    void failsWhenPomCannotBeParsed() throws IOException {
        var pom = writePom("not-xml");

        assertThatThrownBy(() -> extractor.extractFrom(pom.toFile()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse POM");
    }

    private Path writePom(String contents) throws IOException {
        return Files.writeString(tempDir.resolve("pom.xml"), contents);
    }
}
