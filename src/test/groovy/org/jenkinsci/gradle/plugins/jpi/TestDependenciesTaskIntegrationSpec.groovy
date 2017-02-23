package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class TestDependenciesTaskIntegrationSpec extends Specification {
    @Rule
    final TemporaryFolder project = new TemporaryFolder()

    def 'resolves test dependencies'() {
        given:
        project.newFile('build.gradle') << getClass().getResource('resolveTestDependencies.gradle').text

        when:
        def result = GradleRunner.create()
                .withProjectDir(project.root)
                .withPluginClasspath()
                .withArguments('processTestResources', '--stacktrace')
                .build()

        then:
        result.task(':resolveTestDependencies').outcome == TaskOutcome.SUCCESS
        result.task(':processTestResources').outcome == TaskOutcome.NO_SOURCE
        File dir = new File(project.root, 'build/resources/test/test-dependencies')
        dir.directory
        new File(dir, 'index').text == [
                'structs', 'config-file-provider', 'cloudbees-folder',
                'token-macro', 'credentials',
        ].join('\n')
        new File(dir, 'structs.hpi').exists()
        new File(dir, 'config-file-provider.hpi').exists()
        new File(dir, 'cloudbees-folder.hpi').exists()
        new File(dir, 'token-macro.hpi').exists()
        new File(dir, 'credentials.hpi').exists()
    }
}
