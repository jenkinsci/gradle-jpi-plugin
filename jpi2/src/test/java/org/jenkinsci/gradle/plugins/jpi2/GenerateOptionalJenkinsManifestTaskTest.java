package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jenkinsci.gradle.plugins.jpi2.GenerateOptionalJenkinsManifestTask.ExtensionEntry;
import static org.jenkinsci.gradle.plugins.jpi2.GenerateOptionalJenkinsManifestTask.resolveDynamicLoadingSupport;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerateOptionalJenkinsManifestTaskTest {

    @Test
    void parseReadsDynamicLoadableValue() {
        assertThat(ExtensionEntry.parse("com.example.Extension{dynamicLoadable=YES}")
                        .dynamicLoadable())
                .isEqualTo("YES");
        assertThat(ExtensionEntry.parse("com.example.Extension{dynamicLoadable=NO}")
                        .dynamicLoadable())
                .isEqualTo("NO");
        assertThat(ExtensionEntry.parse("com.example.Extension{dynamicLoadable=MAYBE}")
                        .dynamicLoadable())
                .isEqualTo("MAYBE");
    }

    @Test
    void parseDefaultsToMaybeWhenAttributeIsMissing() {
        assertThat(ExtensionEntry.parse("com.example.Extension").dynamicLoadable())
                .isEqualTo("MAYBE");
        assertThat(ExtensionEntry.parse("com.example.Extension{other=YES}").dynamicLoadable())
                .isEqualTo("MAYBE");
    }

    @Test
    void parseHandlesMissingClosingBraceAndWhitespace() {
        assertThat(ExtensionEntry.parse("com.example.Extension{dynamicLoadable=YES")
                        .dynamicLoadable())
                .isEqualTo("YES");
        assertThat(ExtensionEntry.parse("com.example.Extension{dynamicLoadable= YES }")
                        .dynamicLoadable())
                .isEqualTo("YES");
    }

    @Test
    void resolveReturnsTrueWhenAllEntriesAreYesOrEmpty() {
        assertThat(resolveDynamicLoadingSupport(List.of())).isTrue();
        assertThat(resolveDynamicLoadingSupport(List.of(new ExtensionEntry("YES"))))
                .isTrue();
        assertThat(resolveDynamicLoadingSupport(List.of(new ExtensionEntry("YES"), new ExtensionEntry("YES"))))
                .isTrue();
    }

    @Test
    void resolveReturnsNullWhenAnyEntryIsMaybeAndNoneAreNo() {
        assertThat(resolveDynamicLoadingSupport(List.of(new ExtensionEntry("MAYBE"))))
                .isNull();
        assertThat(resolveDynamicLoadingSupport(List.of(new ExtensionEntry("YES"), new ExtensionEntry("MAYBE"))))
                .isNull();
    }

    @Test
    void resolveReturnsFalseWhenAnyEntryIsNo() {
        assertThat(resolveDynamicLoadingSupport(List.of(new ExtensionEntry("NO"))))
                .isFalse();
        assertThat(resolveDynamicLoadingSupport(
                        List.of(new ExtensionEntry("YES"), new ExtensionEntry("MAYBE"), new ExtensionEntry("NO"))))
                .isFalse();
    }
}
