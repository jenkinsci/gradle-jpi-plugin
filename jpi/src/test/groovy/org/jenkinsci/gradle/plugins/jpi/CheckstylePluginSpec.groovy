package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.TaskOutcome

class CheckstylePluginSpec extends IntegrationSpec {

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

    def "should not run checkstyle tasks by default"() {
        given:
        build << """jenkinsPlugin {
            jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
        }""".stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('build')
            .build()

        then:
        result.task(':checkstyleMain') == null
        result.task(':checkstyleTest') == null
    }

    def "should run checkstyle tasks with default sun-checks and generate only xml"() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                enableCheckstyle()
            }""".stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('build')
            .buildAndFail()

        then:
        result.task(':checkstyleMain').outcome == TaskOutcome.FAILED
        existsRelativeToProjectDir('build/reports/checkstyle/main.xml')
        !existsRelativeToProjectDir('build/reports/checkstyle/main.html')
    }

    def "should override default checkstyle xml config"() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                enableCheckstyle()
            }

            checkstyle {
                config = resources.text.fromString('''<!DOCTYPE module PUBLIC
                          "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                          "https://checkstyle.org/dtds/configuration_1_3.dtd">
                            <module name="Checker">
                                <module name="TreeWalker">
                                    <module name="ConstantName"/>
                                </module>
                            </module>''')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('build')
            .build()

        then:
        result.task(':checkstyleMain').outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('build/reports/checkstyle/main.xml')
    }

}
