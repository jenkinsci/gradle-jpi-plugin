package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.DomainObjectSet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext

class JpiComponent implements SoftwareComponentInternal {
    private final UsageContext jpiUsage = new JpiUsage()
    private final Set<PublishArtifact> jpiArtifacts
    private final DomainObjectSet jpiDependencies

    final String name = 'jpi'
    final Set<UsageContext> usages = [jpiUsage]

    JpiComponent(PublishArtifact jpiArtifact, DomainObjectSet jpiDependencies) {
        this.jpiArtifacts = [jpiArtifact]
        this.jpiDependencies = jpiDependencies
    }

    private class JpiUsage implements UsageContext {
        final Usage usage = Usage.FOR_RUNTIME

        Set<PublishArtifact> getArtifacts() {
            jpiArtifacts
        }

        Set<ModuleDependency> getDependencies() {
            // copy to new set, otherwise the result will contain duplicates
            new HashSet<ModuleDependency>(jpiDependencies.withType(ModuleDependency))
        }
    }
}
