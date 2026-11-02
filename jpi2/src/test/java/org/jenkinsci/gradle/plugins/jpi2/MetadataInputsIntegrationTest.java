package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.gradle.testkit.runner.TaskOutcome;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.Test;

class MetadataInputsIntegrationTest extends V2IntegrationTestBase {
    @Test
    void ignoresClassChangesButTracksMetadataAcrossOutputRoots() throws Exception {
        var ith = new IntegrationTestHelper(tempDir, "8.14");
        initBuild(ith);
        Files.writeString(ith.inProjectDir("build.gradle.kts").toPath(), """
                import org.jenkinsci.gradle.plugins.jpi2.CheckOverlappingSourcesTask
                import org.jenkinsci.gradle.plugins.jpi2.GenerateOptionalJenkinsManifestTask
                plugins { id("org.jenkins-ci.jpi2") apply false }
                tasks.register<CheckOverlappingSourcesTask>("overlap") {
                    classesDirs.from("classes/one", "classes/two")
                    outputFile.set(layout.buildDirectory.file("discovered.txt"))
                }
                tasks.register<GenerateOptionalJenkinsManifestTask>("manifest") {
                    inspectionDirectories.from("classes/one", "classes/two")
                    outputFile.set(layout.buildDirectory.file("optional.mf"))
                }
                """);
        var classes = ith.mkDirInProjectDir("classes/one").toPath();
        var runner = ith.gradleRunner().withArguments("overlap", "manifest", "--configuration-cache");
        var first = runner.build();
        assertThat(first.task(":overlap").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.task(":manifest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        Files.writeString(classes.resolve("Example.class"), "new class bytes");
        var unchanged = runner.build();
        assertThat(unchanged.getOutput()).contains("Reusing configuration cache.");
        assertThat(unchanged.task(":overlap").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(unchanged.task(":manifest").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        var index = classes.resolve("META-INF/annotations/hudson.Extension.txt");
        Files.createDirectories(index.getParent());
        Files.writeString(index, "com.example.Extension{dynamicLoadable=NO}");
        var changed = runner.build();
        assertThat(changed.task(":overlap").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(changed.task(":manifest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(ith.inProjectDir("build/optional.mf").toPath()))
                .contains("Support-Dynamic-Loading: false");

        var duplicate = ith.inProjectDir("classes/two/META-INF/annotations/hudson.Extension.txt")
                .toPath();
        Files.createDirectories(duplicate.getParent());
        Files.copy(index, duplicate);
        assertThat(runner.buildAndFail().getOutput()).contains("Found overlapping Sezpoz file");
        Files.delete(duplicate);
        Files.delete(index);
        var removed = runner.build();
        assertThat(removed.task(":manifest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(ith.inProjectDir("build/optional.mf").toPath()))
                .contains("Support-Dynamic-Loading: true");
    }
}
