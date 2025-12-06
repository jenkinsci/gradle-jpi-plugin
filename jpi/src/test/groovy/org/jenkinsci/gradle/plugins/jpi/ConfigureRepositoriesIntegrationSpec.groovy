package org.jenkinsci.gradle.plugins.jpi

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult

class ConfigureRepositoriesIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build

    def setup() {
        settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }

            tasks.create('discoverRepos') {
                doLast {
                    def repos = repositories
                        .findAll { it instanceof org.gradle.api.artifacts.repositories.MavenArtifactRepository }
                        .collect { [name: it.name, uri: it.url.toASCIIString()] }
                        .toSet()
                    println groovy.json.JsonOutput.toJson([repositories: repos])
                }
            }
            '''.stripIndent()
    }

    def 'adds repositories by default'() {
        when:
        def result = gradleRunner()
                .withArguments('discoverRepos', '--quiet')
                .build()
        def actual = deserializeReposFrom(result)

        then:
        actual.size() == 2
        actual.contains('MavenRepo')
        actual.contains('jenkins')
    }

    def 'adds repositories if enabled'() {
        given:
        build << '''
            jenkinsPlugin {
                configureRepositories = true
            }
            '''.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('discoverRepos', '--quiet')
                .build()
        def actual = deserializeReposFrom(result)

        then:
        actual.size() == 2
        actual.contains('MavenRepo')
        actual.contains('jenkins')
    }

    def 'does not add repositories if disabled'() {
        given:
        build << '''
            jenkinsPlugin {
                configureRepositories = false
            }
            '''.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('discoverRepos', '--quiet')
                .build()
        def actual = deserializeReposFrom(result)

        then:
        actual.isEmpty()
    }

    private static List<String> deserializeReposFrom(BuildResult result) {
        new JsonSlurper().parseText(result.output)['repositories']*.name
    }
}
