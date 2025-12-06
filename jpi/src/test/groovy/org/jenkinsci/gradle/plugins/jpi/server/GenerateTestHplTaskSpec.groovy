package org.jenkinsci.gradle.plugins.jpi.server

class GenerateTestHplTaskSpec extends GenerateHplTaskSpec {
    @Override
    String taskName() {
        'generateTestHpl'
    }

    @Override
    String expectedRelativeHplLocation() {
        'build/generated-resources/test/the.hpl'
    }
}
