package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import static org.jenkinsci.gradle.plugins.jpi.ClasspathExpectations.COMPILE_ONLY
import static org.jenkinsci.gradle.plugins.jpi.ClasspathExpectations.EVERYWHERE_BUT_RUNTIME
import static org.jenkinsci.gradle.plugins.jpi.ClasspathExpectations.NOWHERE
import static org.jenkinsci.gradle.plugins.jpi.ClasspathExpectations.TEST_IMPLEMENTATION_ONLY

class JenkinsVersionIntegrationSpec extends IntegrationSpec {
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

            class DirectDependencies extends DefaultTask {
                @org.gradle.api.tasks.options.Option(option='config', description='configuration to list directs for')
                @Input
                String config

                @Internal
                def configurations = project.configurations

                @TaskAction
                void run() {
                    configurations.getByName(config).resolvedConfiguration.firstLevelModuleDependencies.each {
                        println(it.module.id)
                    }
                }
            }

            tasks.register('directs', DirectDependencies)
            '''.stripIndent()
    }

    @Unroll
    @Issue('JENKINS-26331')
    @Ignore('Sunset: workaround last applied to version released in 2014')
    def 'should handle target directory creation for #version'(String version, boolean targetShouldExist) {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${version}'
            }
            repositories {
                maven { url = 'https://repo.jenkins-ci.org/incrementals' }
            }
            """.stripIndent()
        TestSupport.PASSING_TEST.writeTo(inProjectDir('src/test/java'))

        expect:
        def result = gradleRunner()
                .withArguments('test', '-s')
                .build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('target') == targetShouldExist

        where:
        version                         | targetShouldExist
        '1.544'                         | false
        '1.545'                         | true
        '1.591'                         | true
        '1.592'                         | false
        '2.361.2-rc32710.c1a_5e8c179f6' | false
        '2.369-rc32854.076293e36922'    | false
    }

    @Unroll
    @Issue('JENKINS-26331')
    def 'should handle target directory deletion for #version'(String version, boolean targetShouldBeCleaned, TaskOutcome expected) {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${version}'
            }
            repositories {
                maven { url = 'https://repo.jenkins-ci.org/incrementals' }
            }
            """.stripIndent()
        def target = inProjectDir('target')
        target.mkdirs()
        assert existsRelativeToProjectDir('target')

        expect:
        def result = gradleRunner()
                .withArguments('clean', '-s')
                .build()
        result.task(':clean').outcome == expected
        existsRelativeToProjectDir('target') != targetShouldBeCleaned

        where:
        version                         | targetShouldBeCleaned | expected
        '1.597'                         | true                  | TaskOutcome.SUCCESS
        '1.598'                         | false                 | TaskOutcome.UP_TO_DATE
        '2.361.2-rc32710.c1a_5e8c179f6' | false                 | TaskOutcome.UP_TO_DATE
        '2.369-rc32854.076293e36922'    | false                 | TaskOutcome.UP_TO_DATE
    }

    @Unroll
    def 'should add dependencies for #version'(String version, Map<String, ClasspathExpectations> expectations) {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${version}'
            }
            """.stripIndent()

        expect:
        expectations.each { module, expected ->
            expected.on.each { config ->
                assert gradleRunner()
                        .withArguments('directs', "--config=${config}")
                        .build()
                        .output
                        .contains(module)
            }
            expected.off.each { config ->
                assert !gradleRunner()
                        .withArguments('directs', "--config=${config}")
                        .build()
                        .output
                        .contains(module)
            }
        }

        where:
        version   | expectations
        '2.222.3' | [
                'org.jenkins-ci.main:jenkins-core:2.222.3'                   : EVERYWHERE_BUT_RUNTIME,
                'com.github.spotbugs:spotbugs-annotations'                   : COMPILE_ONLY,
                'com.google.code.findbugs:annotations:3.0.0'                 : NOWHERE,
                'findbugs:annotations:1.0.0'                                 : NOWHERE,
                'javax.servlet:servlet-api:2.4'                              : NOWHERE,
                'javax.servlet:javax.servlet-api:3.1.0'                      : COMPILE_ONLY,
                'jakarta.servlet:jakarta.servlet-api:5.0.0'                  : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:2112.ve584e0edc63b_': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'                  : NOWHERE,
                'junit:junit-dep:4.10'                                       : NOWHERE,
        ]
        '2.150.3' | [
                'org.jenkins-ci.main:jenkins-core:2.150.3'                   : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'                 : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                                 : NOWHERE,
                'javax.servlet:servlet-api:2.4'                              : NOWHERE,
                'javax.servlet:javax.servlet-api:3.1.0'                      : COMPILE_ONLY,
                'jakarta.servlet:jakarta.servlet-api:5.0.0'                  : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:2112.ve584e0edc63b_': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'                  : NOWHERE,
                'junit:junit-dep:4.10'                                       : NOWHERE,
        ]
        '2.64'    | [
                'org.jenkins-ci.main:jenkins-core:2.64'                      : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'                 : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                                 : NOWHERE,
                'javax.servlet:servlet-api:2.4'                              : NOWHERE,
                'javax.servlet:javax.servlet-api:3.1.0'                      : COMPILE_ONLY,
                'org.jenkins-ci.main:jenkins-test-harness:2112.ve584e0edc63b_': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'                  : NOWHERE,
                'junit:junit-dep:4.10'                                       : NOWHERE,
        ]
        '2.63'    | [
                'org.jenkins-ci.main:jenkins-core:2.63'       : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'  : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                  : NOWHERE,
                'javax.servlet:servlet-api:2.4'               : NOWHERE,
                'javax.servlet:javax.servlet-api:3.1.0'       : COMPILE_ONLY,
                'org.jenkins-ci.main:jenkins-test-harness:2.0': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'   : NOWHERE,
                'junit:junit-dep:4.10'                        : NOWHERE,
        ]
        '2.0'     | [
                'org.jenkins-ci.main:jenkins-core:2.0'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'  : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                  : NOWHERE,
                'javax.servlet:servlet-api:2.4'               : NOWHERE,
                'javax.servlet:javax.servlet-api:3.1.0'       : COMPILE_ONLY,
                'org.jenkins-ci.main:jenkins-test-harness:2.0': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'   : NOWHERE,
                'junit:junit-dep:4.10'                        : NOWHERE,
        ]
        '1.658'   | [
                'org.jenkins-ci.main:jenkins-core:1.658'      : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'  : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                  : NOWHERE,
                'javax.servlet:servlet-api:2.4'               : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'       : NOWHERE,
                'jakarta.servlet:jakarta.servlet-api:5.0.0'   : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:2.0': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'   : NOWHERE,
                'junit:junit-dep:4.10'                        : NOWHERE,
        ]
        '1.645'   | [
                'org.jenkins-ci.main:jenkins-core:1.645'      : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'  : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                  : NOWHERE,
                'javax.servlet:servlet-api:2.4'               : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'       : NOWHERE,
                'jakarta.servlet:jakarta.servlet-api:5.0.0'   : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:2.0': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'   : NOWHERE,
                'junit:junit-dep:4.10'                        : NOWHERE,
        ]
        '1.644'   | [
                'org.jenkins-ci.main:jenkins-core:1.644'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                    : NOWHERE,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'jakarta.servlet:jakarta.servlet-api:5.0.0'     : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.644': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'     : NOWHERE,
                'junit:junit-dep:4.10'                          : NOWHERE,
        ]
        '1.618'   | [
                'org.jenkins-ci.main:jenkins-core:1.618'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : COMPILE_ONLY,
                'findbugs:annotations:1.0.0'                    : NOWHERE,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'jakarta.servlet:jakarta.servlet-api:5.0.0'     : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.618': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'     : NOWHERE,
                'junit:junit-dep:4.10'                          : NOWHERE,
        ]
        '1.617'   | [
                'org.jenkins-ci.main:jenkins-core:1.617'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : NOWHERE,
                'findbugs:annotations:1.0.0'                    : COMPILE_ONLY,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.617': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'     : NOWHERE,
                'junit:junit-dep:4.10'                          : NOWHERE,
        ]
        '1.533'   | [
                'org.jenkins-ci.main:jenkins-core:1.533'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : NOWHERE,
                'findbugs:annotations:1.0.0'                    : COMPILE_ONLY,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.533': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:2.0'     : NOWHERE,
                'junit:junit-dep:4.10'                          : NOWHERE,
        ]
        '1.532'   | [
                'org.jenkins-ci.main:jenkins-core:1.532'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : NOWHERE,
                'findbugs:annotations:1.0.0'                    : COMPILE_ONLY,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.532': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:1.532'   : NOWHERE,
                'junit:junit-dep:4.10'                          : NOWHERE,
        ]
        '1.505'   | [
                'org.jenkins-ci.main:jenkins-core:1.505'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : NOWHERE,
                'findbugs:annotations:1.0.0'                    : COMPILE_ONLY,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.505': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:1.505'   : NOWHERE,
                'junit:junit-dep:4.10'                          : NOWHERE,
        ]
        '1.504'   | [
                'org.jenkins-ci.main:jenkins-core:1.504'        : EVERYWHERE_BUT_RUNTIME,
                'com.google.code.findbugs:annotations:3.0.0'    : NOWHERE,
                'findbugs:annotations:1.0.0'                    : COMPILE_ONLY,
                'javax.servlet:servlet-api:2.4'                 : COMPILE_ONLY,
                'javax.servlet:javax.servlet-api:3.1.0'         : NOWHERE,
                'org.jenkins-ci.main:jenkins-test-harness:1.504': TEST_IMPLEMENTATION_ONLY,
                'org.jenkins-ci.main:ui-samples-plugin:1.504'   : NOWHERE,
                'junit:junit-dep:4.10'                          : TEST_IMPLEMENTATION_ONLY,
        ]
    }
}
