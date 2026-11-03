package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.jar.Manifest;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class HplInputsIntegrationTest extends V2IntegrationTestBase {
    @Test
    void tracksLibraryPathsExistenceAndOrderWithoutHashingContents() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                import org.jenkinsci.gradle.plugins.jpi2.GenerateHplTask
                plugins { id("org.jenkins-ci.jpi2") apply false }
                val library = tasks.register("library") {
                    val output = layout.buildDirectory.file("library.jar")
                    outputs.file(output)
                    doLast { output.get().asFile.writeText("library") }
                }
                tasks.register<GenerateHplTask>("hpl") {
                    hpl.set(layout.buildDirectory.file("plugin.hpl"))
                    resourcePath.set(file("webapp"))
                    upstreamManifest.set(file("upstream.mf"))
                    libraries.from(library)
                    libraries.from(providers.gradleProperty("paths").orElse("one,two").map { it.split(",") })
                }
                """);
        Files.writeString(ith.inProjectDir("upstream.mf").toPath(), "Manifest-Version: 1.0\n\n");
        var one = ith.mkDirInProjectDir("one").toPath();
        var runner = ith.gradleRunner();
        var first = runner.withArguments("hpl", "--configuration-cache").build();
        assertThat(first.task(":library").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.task(":hpl").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        var original = Files.readString(ith.inProjectDir("build/plugin.hpl").toPath());

        Files.writeString(one.resolve("Example.class"), "changed bytes");
        var contents = runner.build();
        assertThat(contents.getOutput()).contains("Reusing configuration cache.");
        assertThat(contents.task(":hpl").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(Files.readString(ith.inProjectDir("build/plugin.hpl").toPath()))
                .isEqualTo(original);

        ith.mkDirInProjectDir("two");
        assertThat(runner.build().task(":hpl").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        var reordered = runner.withArguments("hpl", "--configuration-cache", "-Ppaths=two,one")
                .build();
        assertThat(reordered.task(":hpl").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(libraries(ith))
                .contains(ith.inProjectDir("two").getCanonicalPath() + ","
                        + one.toFile().getCanonicalPath());

        deleteDirectory(one.toFile());
        assertThat(runner.build().task(":hpl").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(libraries(ith)).doesNotContain(one.toFile().getCanonicalPath());
    }

    private static String libraries(IntegrationTestHelper ith) throws Exception {
        try (var input =
                Files.newInputStream(ith.inProjectDir("build/plugin.hpl").toPath())) {
            return new Manifest(input).getMainAttributes().getValue("Libraries");
        }
    }
}
