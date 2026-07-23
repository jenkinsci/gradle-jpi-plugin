package org.jenkinsci.gradle.plugins.jpi2;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testHplRunInvalidatesOnUpstreamModuleSourceChange() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureTwoPluginsForVerification(ith);

        GradleRunner runner = ith.gradleRunner();
        var taskPath = ":downstream:testHplRun";

        var first = runner.withArguments(":downstream:testHplRun", "--build-cache").build();
        assertThat(first.task(taskPath).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.getOutput()).contains("Jenkins is fully up and running");

        var noChange = runner.withArguments(":downstream:testHplRun", "--build-cache").build();
        assertThat(noChange.task(taskPath).getOutcome())
                .as("unchanged inputs should hit the cache and skip launching Jenkins")
                .isEqualTo(TaskOutcome.UP_TO_DATE);

        deleteDirectory(ith.inProjectDir("downstream/build"));
        var fromCache = runner.withArguments(":downstream:testHplRun", "--build-cache").build();
        assertThat(fromCache.task(taskPath).getOutcome())
                .as("after build dir is deleted, the task must be restored FROM_CACHE rather than re-executing")
                .isEqualTo(TaskOutcome.FROM_CACHE);

        // Edit a source file in the *upstream* module — Jenkins on the next hplRun would
        // load the new class via upstream's HPL (which only references paths, not content).
        // The downstream testHplRun must therefore re-execute, not silently reuse the
        // cached pass that no longer reflects current behavior.
        var upstreamSrc = ith.inProjectDir("upstream/src/main/java/com/example/upstream/Example.java").toPath();
        Files.writeString(upstreamSrc, /* language=java */ """
                package com.example.upstream;
                public class Example { public String hello() { return "v2"; } }
                """, StandardCharsets.UTF_8);
        var afterUpstreamEdit = runner.withArguments(":downstream:testHplRun", "--build-cache").build();
        assertThat(afterUpstreamEdit.task(taskPath).getOutcome())
                .as("editing an upstream module's source must invalidate the downstream testHplRun cache")
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(afterUpstreamEdit.getOutput()).contains("Jenkins is fully up and running");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void concurrentTestServersAcrossModulesDoNotTripValidation() throws IOException {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureTwoPluginsForVerification(ith);

        // downstream depends on upstream, so downstream's prepareServer source (which testServer
        // fingerprints) includes upstream's produced artifacts. Running both testServers together
        // must not trip Gradle's implicit-dependency validation on the upstream producing task.
        //
        // The validation fires at graph/configuration time, before Jenkins boots, so a short startup
        // timeout is enough to exercise it — this avoids booting two full Jenkins servers at once,
        // which would spike memory/CPU on a constrained CI runner. Both launches therefore time out
        // (buildAndFail); the fix is proven by the ABSENCE of the validation error.
        var result = ith.gradleRunner()
                .withArguments(":upstream:testServer", ":downstream:testServer",
                        "--continue", "-DtestServer.timeoutSeconds=5")
                .buildAndFail();

        assertThat(result.getOutput())
                .doesNotContain("without declaring an explicit or implicit dependency");
        // Downstream's task must actually reach its launch (it prints the nested :downstream:server
        // command), proving it got past validation — without the fix it fails at configuration first.
        assertThat(result.getOutput()).contains(":downstream:server");
    }

    @Test
    void multiModuleWithNestedDependenciesShouldLaunchRun() throws IOException, InterruptedException {
        // given
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        configureModuleWithNestedDependencies(ith);

        GradleRunner gradleRunner = ith.gradleRunner();

        // when
        testServerStarts(gradleRunner, ":plugin-four:hplRun");

        // then
        var pluginThreeHpl = ith.inProjectDir("plugin-four/work/plugins/plugin-three.hpl");
        assertThat(pluginThreeHpl).exists();
        assertThat(ith.inProjectDir("plugin-four/work/plugins/plugin-three.jpi")).doesNotExist();
        assertThat(ith.inProjectDir("plugin-four/work/plugins/plugin-four.hpl")).exists();
    }
}
