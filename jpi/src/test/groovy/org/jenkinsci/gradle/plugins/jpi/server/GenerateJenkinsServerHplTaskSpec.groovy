package org.jenkinsci.gradle.plugins.jpi.server

class GenerateJenkinsServerHplTaskSpec extends GenerateHplTaskSpec {
    @Override
    String taskName() {
        GenerateHplTask.TASK_NAME
    }

    @Override
    String expectedRelativeHplLocation() {
        'build/hpl/strawberry.hpl'
    }
}
