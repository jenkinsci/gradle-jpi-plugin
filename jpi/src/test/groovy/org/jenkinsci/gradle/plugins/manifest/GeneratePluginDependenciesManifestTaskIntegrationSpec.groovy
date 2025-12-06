package org.jenkinsci.gradle.plugins.manifest

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Unroll

import static org.jenkinsci.gradle.plugins.jpi.TestSupport.ant
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.git
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.log4jApi

class GeneratePluginDependenciesManifestTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskName = GeneratePluginDependenciesManifestTask.NAME
    private final String taskPath = ':' + taskName
    private static final String MIN_BUILD_FILE = """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            """.stripIndent()
    private static final String BUILD_FILE = """\
            $MIN_BUILD_FILE
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()
    private File build

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
    }

    @Unroll
    def 'should rerun only if #config plugin dependencies change'(String config, String before, String after, TaskOutcome secondRun) {
        given:
        build.text = """\
            $BUILD_FILE
            java {
                registerFeature('ant') {
                    usingSourceSet(sourceSets.create('ant'))
                }
            }
            dependencies {
                $config $before
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskName)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        build.text = """\
            $BUILD_FILE
            java {
                registerFeature('ant') {
                    usingSourceSet(sourceSets.create('ant'))
                }
            }
            dependencies {
                $config $after
            }
            """.stripIndent()
        def rerunResult = gradleRunner()
                .withArguments(taskName)
                .build()

        then:
        rerunResult.task(taskPath).outcome == secondRun

        where:
        config              | before             | after              | secondRun
        'api'               | ant('1.10')        | ant('1.11')        | TaskOutcome.SUCCESS
        'implementation'    | ant('1.10')        | ant('1.11')        | TaskOutcome.SUCCESS
        'runtimeOnly'       | ant('1.10')        | ant('1.11')        | TaskOutcome.SUCCESS
        'antApi'            | ant('1.10')        | ant('1.11')        | TaskOutcome.SUCCESS
        'antImplementation' | ant('1.10')        | ant('1.11')        | TaskOutcome.SUCCESS
        // non-plugin changes shouldn't force this to rerun
        'api'               | log4jApi('2.13.0') | log4jApi('2.14.0') | TaskOutcome.UP_TO_DATE
        'implementation'    | log4jApi('2.13.0') | log4jApi('2.14.0') | TaskOutcome.UP_TO_DATE
        'runtimeOnly'       | log4jApi('2.13.0') | log4jApi('2.14.0') | TaskOutcome.UP_TO_DATE
        'antApi'            | log4jApi('2.13.0') | log4jApi('2.14.0') | TaskOutcome.UP_TO_DATE
        'antImplementation' | log4jApi('2.13.0') | log4jApi('2.14.0') | TaskOutcome.UP_TO_DATE
    }

    @Unroll
    @Issue('https://github.com/gradle/gradle/issues/13278')
    @SuppressWarnings('UnnecessaryGetter')
    @IgnoreIf({ getGradleVersionForTest() < GradleVersion.version('6.7') })
    def 'should rerun only if config plugin dependencies change 6.7+'(String before, String after, TaskOutcome secondRun) {
        given:
        build.text = """\
            $BUILD_FILE
            java {
                registerFeature('ant') {
                    usingSourceSet(sourceSets.create('ant'))
                }
            }
            dependencies {
                antRuntimeOnly $before
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskName)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        build.text = """\
            $BUILD_FILE
            java {
                registerFeature('ant') {
                    usingSourceSet(sourceSets.create('ant'))
                }
            }
            dependencies {
                antRuntimeOnly $after
            }
            """.stripIndent()
        def rerunResult = gradleRunner()
                .withArguments(taskName)
                .build()

        then:
        rerunResult.task(taskPath).outcome == secondRun

        where:
        before             | after              | secondRun
        ant('1.10')        | ant('1.11')        | TaskOutcome.SUCCESS
        // non-plugin changes shouldn't force this to rerun
        log4jApi('2.13.0') | log4jApi('2.14.0') | TaskOutcome.UP_TO_DATE
    }

    @PendingFeature
    def 'should support configuration cache'() {
        given:
        build.text = """\
            $BUILD_FILE
            java {
                registerFeature('ant') {
                    usingSourceSet(sourceSets.create('ant'))
                }
            }
            dependencies {
                antImplementation ${ant('1.10')}
                implementation ${git('4.5.2')}
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskName, '--configuration-cache')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }
}
