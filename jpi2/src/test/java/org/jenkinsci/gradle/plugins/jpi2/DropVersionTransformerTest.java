package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DropVersionTransformerTest {

    @Test
    void stripsVersionAndNormalizesExtensionToJpi() {
        var transformer = new DropVersionTransformer("git", "5.7.0", "jpi");

        assertThat(transformer.transform("git-5.7.0.hpi")).isEqualTo("git.jpi");
        assertThat(transformer.transform("git-5.7.0.jpi")).isEqualTo("git.jpi");
    }

    @Test
    void stripsVersionAndNormalizesExtensionToHpi() {
        var transformer = new DropVersionTransformer("git", "5.7.0", "hpi");

        assertThat(transformer.transform("git-5.7.0.jpi")).isEqualTo("git.hpi");
        assertThat(transformer.transform("git-5.7.0.hpi")).isEqualTo("git.hpi");
    }

    @Test
    void normalizesAlreadyUnversionedFilenames() {
        var transformer = new DropVersionTransformer("git", "5.7.0", "jpi");

        assertThat(transformer.transform("git.hpi")).isEqualTo("git.jpi");
        assertThat(transformer.transform("git.jpi")).isEqualTo("git.jpi");
    }

    @Test
    void leavesFilenameUnchangedWhenNameVersionFragmentIsAbsent() {
        var transformer = new DropVersionTransformer("git", "5.7.0", "jpi");

        assertThat(transformer.transform("other-1.0.hpi")).isEqualTo("other-1.0.jpi");
    }

    @Test
    void replaceIsSubstringBasedNotWordBounded() {
        var transformer = new DropVersionTransformer("git", "5.7.0", "jpi");

        // name-version is replaced wherever it occurs as a substring.
        assertThat(transformer.transform("mygit-5.7.0.hpi")).isEqualTo("mygit.jpi");
    }
}
