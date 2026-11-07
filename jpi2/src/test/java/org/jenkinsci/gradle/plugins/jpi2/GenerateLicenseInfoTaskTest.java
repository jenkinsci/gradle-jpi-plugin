package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

class GenerateLicenseInfoTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void writesProjectAndBundledDependencyLicenses() throws Exception {
        var second = writePom("b-pom.xml", "second", "Apache-2.0");
        var first = writePom("a-pom.xml", "first", "MIT");
        var task = task();
        task.getProjectDescription().set("Plugin description");
        task.getProjectUrl().set("https://example.com/plugin");
        task.getPomFiles().from(second.toFile(), first.toFile());

        task.generateLicenseInfo();

        var document = readReport();
        var root = document.getDocumentElement();
        assertThat(root.getAttribute("groupId")).isEqualTo("com.example");
        assertThat(root.getAttribute("artifactId")).isEqualTo("plugin");
        assertThat(root.getAttribute("version")).isEqualTo("1.0");

        var dependencies = root.getElementsByTagNameNS("licenses", "dependency");
        assertThat(dependencies.getLength()).isEqualTo(3);
        var plugin = (Element) dependencies.item(0);
        assertThat(plugin.getAttribute("name")).isEqualTo("Plugin description");
        assertThat(plugin.getAttribute("url")).isEqualTo("https://example.com/plugin");
        assertThat(description(plugin)).isEqualTo("Plugin description");

        var firstDependency = (Element) dependencies.item(1);
        assertThat(firstDependency.getAttribute("artifactId")).isEqualTo("first");
        assertThat(firstDependency.getAttribute("groupId")).isEqualTo("com.example.libs");
        assertThat(firstDependency.getAttribute("version")).isEqualTo("2.0");
        assertThat(description(firstDependency)).isEqualTo("Description of first");
        var firstLicense = (Element)
                firstDependency.getElementsByTagNameNS("licenses", "license").item(0);
        assertThat(firstLicense.getAttribute("name")).isEqualTo("MIT");
        assertThat(firstLicense.getAttribute("url")).isEqualTo("https://example.com/MIT");

        var secondDependency = (Element) dependencies.item(2);
        assertThat(secondDependency.getAttribute("artifactId")).isEqualTo("second");
        assertThat(((Element) secondDependency
                                .getElementsByTagNameNS("licenses", "license")
                                .item(0))
                        .getAttribute("name"))
                .isEqualTo("Apache-2.0");
    }

    @Test
    void omitsOptionalMetadataAndMissingPom() throws Exception {
        var task = task();
        task.getPomFiles().from(tempDir.resolve("missing-pom.xml").toFile());

        task.generateLicenseInfo();

        var dependencies = readReport().getDocumentElement().getElementsByTagNameNS("licenses", "dependency");
        assertThat(dependencies.getLength()).isEqualTo(1);
        var plugin = (Element) dependencies.item(0);
        assertThat(plugin.getAttribute("name")).isEqualTo("plugin");
        assertThat(plugin.hasAttribute("url")).isFalse();
        assertThat(description(plugin)).isEmpty();
    }

    @Test
    void ordersCollidingPomNamesByCoordinates() throws Exception {
        var first = writePom("first.xml", "same", "MIT");
        var second = writePom("second.xml", "same", "Apache-2.0");
        var a = Files.createDirectory(tempDir.resolve("a")).resolve("same.pom");
        var b = Files.createDirectory(tempDir.resolve("b")).resolve("same.pom");
        Files.writeString(a, Files.readString(first).replace("com.example.libs", "a.example"));
        Files.writeString(b, Files.readString(second).replace("com.example.libs", "b.example"));
        var task = task();
        task.getPomFiles().setFrom(b.toFile(), a.toFile());
        task.generateLicenseInfo();
        var report = Files.readString(tempDir.resolve("licenses/licenses.xml"));

        task.getPomFiles().setFrom(a.toFile(), b.toFile());
        task.generateLicenseInfo();

        assertThat(Files.readString(tempDir.resolve("licenses/licenses.xml"))).isEqualTo(report);
        assertThat(report.indexOf("a.example")).isLessThan(report.indexOf("b.example"));
    }

    private GenerateLicenseInfoTask task() {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks()
                .register("generateLicenseInfoTest", GenerateLicenseInfoTask.class)
                .get();
        task.getOutputDirectory().set(tempDir.resolve("licenses").toFile());
        task.getProjectGroup().set("com.example");
        task.getProjectName().set("plugin");
        task.getProjectVersion().set("1.0");
        return task;
    }

    private Path writePom(String fileName, String artifactId, String licenseName) throws IOException {
        return Files.writeString(tempDir.resolve(fileName), /* language=xml */ """
                <project>
                  <groupId>com.example.libs</groupId>
                  <artifactId>%s</artifactId>
                  <version>2.0</version>
                  <name>Library %s</name>
                  <description>Description of %s</description>
                  <licenses>
                    <license><name>%s</name><url>https://example.com/%s</url></license>
                  </licenses>
                </project>
                """.formatted(
                        artifactId, artifactId, artifactId, licenseName, licenseName));
    }

    private org.w3c.dom.Document readReport() throws IOException, ParserConfigurationException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(tempDir.resolve("licenses/licenses.xml").toFile());
    }

    private static String description(Element dependency) {
        return dependency
                .getElementsByTagNameNS("licenses", "description")
                .item(0)
                .getTextContent();
    }
}
