package org.jenkinsci.gradle.plugins.jpi2;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.GradleRunner;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "TempDir doesn't appear to work correctly on Windows")
class MultiModuleIntegrationTest extends V2IntegrationTestBase {

    @Test
    void multiModuleWithNestedDependenciesShouldBuild() throws IOException, XmlPullParserException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureModuleWithNestedDependencies(ith);

        // when
        ith.gradleRunner()
                .withArguments("build", "publish")
                .build();

        // then
        var jpi = ith.inProjectDir("plugin-four/build/libs/plugin-four-1.0.0.jpi");
        var jar = ith.inProjectDir("plugin-four/build/libs/plugin-four-1.0.0.jar");
        var explodedWar = ith.inProjectDir("plugin-four/build/jpi");

        assertThat(jpi).exists();
        assertThat(jar).exists();
        assertThat(explodedWar).exists();

        var manifest = new File(explodedWar, "META-INF/MANIFEST.MF");
        assertThat(manifest).exists();
        var manifestData = new Manifest(manifest.toURI().toURL().openStream()).getMainAttributes();
        assertThat(manifest).isNotNull().isNotEmpty();

        assertThat(manifestData.getValue("Jenkins-Version")).isEqualTo("2.492.3");
        assertThat(manifestData.getValue("Plugin-Dependencies")).isEqualTo("plugin-three:1.0.0");

        var jpiLibsDir = new File(explodedWar, "WEB-INF/lib");
        assertThat(jpiLibsDir).exists();

        var jpiLibs = jpiLibsDir.list();
        assertThat(jpiLibs).isNotNull()
                .containsExactlyInAnyOrder("plugin-four-1.0.0.jar");

        var pom = ith.inProjectDir("build/repo/com/example/plugin-four/1.0.0/plugin-four-1.0.0.pom");

        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pom));

        assertThat(model).isNotNull();

        assertThat(model.getGroupId()).isEqualTo("com.example");
        assertThat(model.getArtifactId()).isEqualTo("plugin-four");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getPackaging()).isEqualTo("jpi");

        var dependencies = model.getDependencies();
        assertThat(dependencies)
                .extracting(Dependency::getGroupId, Dependency::getArtifactId, Dependency::getVersion, Dependency::getScope)
                .containsExactlyInAnyOrder(
                        new Tuple("com.example", "plugin-three", "1.0.0", "runtime")
                );

    }

    @Test
    void multiModuleWithNestedDependenciesShouldLaunchServer() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureModuleWithNestedDependencies(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, ":plugin-four:server");

        // then
        var pluginThreeJpi = ith.inProjectDir("plugin-four/work/plugins/plugin-three.jpi");
        assertThat(pluginThreeJpi).exists();
    }
}
