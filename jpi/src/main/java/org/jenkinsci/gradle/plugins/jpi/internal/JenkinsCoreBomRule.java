package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;

/**
 * Component metadata rule that adds Jenkins BOM alignment for versions that support it.
 */
@CacheableRule
public class JenkinsCoreBomRule implements ComponentMetadataRule {
    @Override
    public void execute(ComponentMetadataContext ctx) {
        ComponentMetadataDetails details = ctx.getDetails();
        String version = details.getId().getVersion();
        if (JenkinsVersions.beforeBomExists(version)) {
            return;
        }
        String notation = "org.jenkins-ci.main:jenkins-bom:" + version;
        details.belongsTo(notation, false);
    }
}
