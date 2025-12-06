package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.Action;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.model.ObjectFactory;
import shaded.hudson.util.VersionNumber;

import javax.inject.Inject;

import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;

@CacheableRule
public abstract class JenkinsWarRule implements ComponentMetadataRule {
    public static final String JENKINS_WAR_COORDINATES = "org.jenkins-ci.main:jenkins-war";

    @Inject
    public abstract ObjectFactory getObjects();

    /**
     * A Jenkins 'war' or 'war-for-test' is required on the Jenkins test classpath. This classpath expects JPI
     * variants. This rule adds such a variant to the Jenkins war module pointing at the right artifact depending
     * on the version of the module.
     */
    @Override
    public void execute(ComponentMetadataContext ctx) {
        ComponentMetadataDetails details = ctx.getDetails();
        ModuleVersionIdentifier id = details.getId();
        details.addVariant("jenkinsTestRuntimeElements", "runtime", new Action<VariantMetadata>() {
            @Override
            public void execute(VariantMetadata v) {
                v.attributes(new Action<AttributeContainer>() {
                    @Override
                    public void execute(AttributeContainer c) {
                        c.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.class, "jpi"));
                    }
                });
                v.withDependencies(new Action<DirectDependenciesMetadata>() {
                    @Override
                    public void execute(DirectDependenciesMetadata d) {
                        // Dependencies with a classifier point at JARs and can be removed
                        d.removeIf(m -> m.getArtifactSelectors().stream()
                                .map(DependencyArtifact::getClassifier)
                                .anyMatch(String::isEmpty));
                    }
                });
                if (new VersionNumber(id.getVersion()).isOlderThan(new VersionNumber("2.64"))) {
                    v.withFiles(new Action<MutableVariantFilesMetadata>() {
                        @Override
                        public void execute(MutableVariantFilesMetadata f) {
                            f.removeAllFiles();
                            f.addFile(String.join("-", id.getName(), id.getVersion(), "war-for-test.jar"));
                        }
                    });
                }
            }
        });
    }
}
