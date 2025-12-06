package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.TaskOutcome

class JacocoPluginSpec extends IntegrationSpec {

    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build

    def setup() {
        settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }

            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        TestSupport.CALCULATOR.writeTo(inProjectDir('src/main/java'))
        TestSupport.PASSING_TEST.writeTo(inProjectDir('src/test/java'))
    }

    def "should not run jacoco report task by default"() {
        given:
        build << """jenkinsPlugin {
            jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
        }""".stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('build')
            .build()

        then:
        result.task(':jacocoTestReport') == null
    }

    def "should run jacoco task and generate only xml report"() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                enableJacoco()
            }""".stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('build')
            .build()

        then:
        result.task(':jacocoTestReport').outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('build/jacoco/test.exec')
        existsRelativeToProjectDir('build/reports/jacoco/test/jacocoTestReport.xml')
        !existsRelativeToProjectDir('build/reports/jacoco/test/html')
    }

    def "should (re)define jacoco settings"() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                enableJacoco()
            }

            jacocoTestReport {
                reports {
                    html.required = true
                    html.outputLocation = layout.buildDirectory.dir('jacocoHtml')
                }
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('build')
            .build()

        then:
        result.task(':jacocoTestReport').outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('build/jacoco/test.exec')
        existsRelativeToProjectDir('build/reports/jacoco/test/jacocoTestReport.xml')
        existsRelativeToProjectDir('build/jacocoHtml')
    }

}
