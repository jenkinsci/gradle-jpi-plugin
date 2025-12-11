package org.jenkinsci.gradle.plugins.jpi2;

import com.google.common.io.Files;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class PublishingIntegrationTest extends V2IntegrationTestBase {

    @Test
    void publishesJarAndJpi() throws IOException, XmlPullParserException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    api("org.jenkins-ci.plugins:jackson2-api:2.18.3-402.v74c4eb_f122b_2")
                    implementation("com.github.rahulsom:nothing-java:0.2.0")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        // when
        var gradleRunner = ith.gradleRunner();
        gradleRunner.withArguments("publish").build();

        // then
        var jpi = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jpi");
        var jar = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.jar");
        var sourcesJar = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0-sources.jar");
        var javadocJar = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0-javadoc.jar");
        var pom = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.pom");
        var module = ith.inProjectDir("build/repo/com/example/test-plugin/1.0.0/test-plugin-1.0.0.module");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(sourcesJar).exists();
        assertThat(javadocJar).exists();
        assertThat(pom).exists();
        assertThat(module).exists();

        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom));

        assertThat(model).isNotNull();

        assertThat(model.getGroupId()).isEqualTo("com.example");
        assertThat(model.getArtifactId()).isEqualTo("test-plugin");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getPackaging()).isEqualTo("jpi");

        var dependencies = model.getDependencies();
        assertThat(dependencies)
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion, Dependency::getScope)
                .containsExactlyInAnyOrder(
                        new Tuple("org.jenkins-ci.plugins", "jackson2-api", "2.18.3-402.v74c4eb_f122b_2", "compile"),
                        new Tuple("com.github.rahulsom", "nothing-java", "0.2.0", "runtime")
                );

        var repositories = model.getRepositories();
        assertThat(repositories)
                .extracting(Repository::getId, Repository::getUrl)
                .containsExactlyInAnyOrder(
                        new Tuple("MavenRepo", "https://repo.maven.apache.org/maven2/"),
                        new Tuple("jenkins-releases", "https://repo.jenkins-ci.org/releases/")
                );
        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("nothing-java-0.2.0.jar",
                        "test-plugin-1.0.0.jar",
                        "commons-math3-3.6.1.jar",
                        "commons-lang3-3.12.0.jar");
    }

    @Test
    void consumesJpisWithJars() throws IOException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.write((getBasePluginConfig() + /* language=kotlin */ """
                dependencies {
                    implementation("org.jenkins-ci.plugins:jackson2-api:2.18.3-402.v74c4eb_f122b_2")
                }
                """).getBytes(StandardCharsets.UTF_8), ith.inProjectDir("build.gradle.kts"));

        ith.mkDirInProjectDir("src/main/java/com/example/plugin");
        Files.write(/* language=java */ """
                package com.example.plugin;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class Plugin {
                    public static void main(String[] args) {
                        var o = new ObjectMapper();
                    }
                }
                """.getBytes(StandardCharsets.UTF_8), ith.inProjectDir("src/main/java/com/example/plugin/Plugin.java"));

        // when
        var gradleRunner = ith.gradleRunner();
        var result = gradleRunner.withArguments("build").build();

        // then
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        var explodedWar = ith.inProjectDir("build/jpi");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("test-plugin-1.0.0.jar");

    }
}
