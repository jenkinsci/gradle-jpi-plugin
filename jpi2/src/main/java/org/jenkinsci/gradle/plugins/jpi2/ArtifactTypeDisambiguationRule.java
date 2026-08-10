package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

/**
 * Third-party plugins (e.g. {@code pmd}) resolve their own configurations over hpi/jpi-packaged
 * dependencies without requesting {@link ArtifactType#ARTIFACT_TYPE_ATTRIBUTE}, which otherwise
 * leaves {@link HpiMetadataRule}'s "runtime" and "defaultRuntime" variants ambiguous. When no
 * value is requested, prefer the plain jar variant, matching what jenkinsPlugin-owned
 * configurations already request explicitly.
 */
abstract class ArtifactTypeDisambiguationRule implements AttributeDisambiguationRule<ArtifactType> {

    @Inject
    public ArtifactTypeDisambiguationRule() {
    }

    @Override
    public void execute(@NotNull MultipleCandidatesDetails<ArtifactType> details) {
        if (details.getConsumerValue() != null) {
            return;
        }
        details.getCandidateValues().stream()
                .filter(candidate -> ArtifactType.PLUGIN_JAR.equals(candidate.getName()))
                .findFirst()
                .ifPresent(details::closestMatch);
    }
}
