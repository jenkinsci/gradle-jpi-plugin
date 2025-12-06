package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.TaskOutcome

class SpotBugsPluginSpec extends IntegrationSpec {

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
            """.stripIndent()
        TestSupport.CALCULATOR.writeTo(inProjectDir('src/main/java'))
        TestSupport.PASSING_TEST.writeTo(inProjectDir('src/test/java'))
    }

    def "should not run SpotBugs tasks by default (Gradle 7)"() {
        given:
        build << """jenkinsPlugin {
            jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
        }""".stripIndent()

        when:
        def result = gradleRunner(WarningMode.ALL) // spotbugs plugin 5.x uses deprecated org.gradle.util.ClosureBackedAction
            .withArguments('build')
            .build()

        then:
        result.task(':spotbugsMain') == null
        result.task(':spotbugsTest') == null
    }

    def "should run SpotBugs tasks with default and generate only xml"() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                enableSpotBugs()
            }""".stripIndent()

        when:
        def result = gradleRunner(WarningMode.ALL) // spotbugs plugin 5.x uses deprecated org.gradle.util.ClosureBackedAction
            .withArguments('build')
            .build()

        then:
        result.task(':spotbugsMain').outcome == TaskOutcome.SUCCESS
        result.task(':spotbugsTest').outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('build/reports/spotbugs/main.xml')
        !existsRelativeToProjectDir('build/reports/spotbugs/main.html')
    }

    def "should override default SpotBugs config"() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                enableSpotBugs()
            }
            spotbugsMain {
                reports {
                    html {
                        required = true
                        outputLocation = file("\$buildDir/reports/spotbugs/main/spotbugs.html")
                    }
                }
            }

            """.stripIndent()

        when:
        def result = gradleRunner(WarningMode.ALL) // spotbugs plugin 5.x uses deprecated org.gradle.util.ClosureBackedAction
            .withArguments('build')
            .build()

        then:
        result.task(':spotbugsMain').outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('build/reports/spotbugs/main/spotbugs.html')
    }
}
