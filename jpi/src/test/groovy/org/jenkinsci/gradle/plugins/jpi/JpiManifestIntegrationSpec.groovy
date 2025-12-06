package org.jenkinsci.gradle.plugins.jpi

class JpiManifestIntegrationSpec extends AbstractManifestIntegrationSpec {
    @Override
    String taskToRun() {
        'jpi'
    }

    @Override
    String generatedFileName(String base = projectName) {
        "${base}.hpi"
    }
}
