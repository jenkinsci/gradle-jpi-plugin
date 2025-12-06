package org.jenkinsci.gradle.plugins.testing

import groovy.text.SimpleTemplateEngine
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.PendingFeature
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.ANT_1_10
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.ANT_1_11
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.LOG4J_API_2_13_0
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.LOG4J_API_2_14_0
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.q

class CopyGeneratedJenkinsTestPluginDependenciesTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskPath = ':copyGeneratedJenkinsTestPluginDependencies'
    private File build
    private File settings
    private final engine = new SimpleTemplateEngine()
    @SuppressWarnings('GStringExpressionWithinString')
    private final buildTemplate = '''\
        plugins {
            id 'org.jenkins-ci.jpi'
        }
        jenkinsPlugin {
            jenkinsVersion = '$jenkinsVersion'
        }
        dependencies {
        <% for (dep in dependencies) { %>
            ${dep.configuration} ${dep.coordinate}
        <% } %>
        }
        '''.stripIndent()
    private final template = engine.createTemplate(buildTemplate)

    def setup() {
        settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
    }

    @Unroll
    @SuppressWarnings('ParameterCount')
    def 'should rerun #rerunOutcome when #configuration dependencies #change'(String configuration,
                                                                              List<String> before,
                                                                              List<String> after,
                                                                              String change,
                                                                              TaskOutcome rerunOutcome,
                                                                              List<String> lines) {
        given:
        def made = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : before.collect { ['configuration': configuration, 'coordinate': q(it)] },
        ])
        build.withWriter { made.writeTo(it) }

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == SUCCESS

        when:
        def remade = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : after.collect { ['configuration': configuration, 'coordinate': q(it)] },
        ])
        build.withWriter { remade.writeTo(it) }
        def rerunResult = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        rerunResult.task(taskPath).outcome == rerunOutcome
        def index = 'build/jpi-plugin/generatedJenkinsTest/test-dependencies/index'
        def actual = inProjectDir(index)
        if (existsRelativeToProjectDir(index)) {
            actual.readLines().toSorted() == lines.toSorted()
            actual.eachLine {
                assert inProjectDir("build/jpi-plugin/generatedJenkinsTest/test-dependencies/${it}.jpi")
            }
        }

        where:
        configuration        | before             | after              | change            | rerunOutcome | lines
        'compileOnly'        | []                 | [LOG4J_API_2_13_0] | 'added library'   | UP_TO_DATE   | []
        'compileOnly'        | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed library' | UP_TO_DATE   | []
        'compileOnly'        | [LOG4J_API_2_13_0] | []                 | 'removed library' | UP_TO_DATE   | []

        'compileOnly'        | []                 | [ANT_1_11]         | 'added plugin'    | UP_TO_DATE   | []
        'compileOnly'        | [ANT_1_10]         | [ANT_1_11]         | 'changed plugin'  | UP_TO_DATE   | []
        'compileOnly'        | [ANT_1_10]         | []                 | 'removed plugin'  | UP_TO_DATE   | []

        'implementation'     | []                 | [LOG4J_API_2_13_0] | 'added library'   | UP_TO_DATE   | []
        'implementation'     | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed library' | UP_TO_DATE   | []
        'implementation'     | [LOG4J_API_2_13_0] | []                 | 'removed library' | UP_TO_DATE   | []

        'implementation'     | []                 | [ANT_1_11]         | 'added plugin'    | SUCCESS      | ['ant', 'structs']
        'implementation'     | [ANT_1_10]         | [ANT_1_11]         | 'changed plugin'  | SUCCESS      | ['ant', 'structs']
        'implementation'     | [ANT_1_10]         | []                 | 'removed plugin'  | SUCCESS      | []

        'runtimeOnly'        | []                 | [LOG4J_API_2_13_0] | 'added library'   | UP_TO_DATE   | []
        'runtimeOnly'        | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed library' | UP_TO_DATE   | []
        'runtimeOnly'        | [LOG4J_API_2_13_0] | []                 | 'removed library' | UP_TO_DATE   | []

        'runtimeOnly'        | []                 | [ANT_1_11]         | 'added plugin'    | SUCCESS      | ['ant', 'structs']
        'runtimeOnly'        | [ANT_1_10]         | [ANT_1_11]         | 'changed plugin'  | SUCCESS      | ['ant', 'structs']
        'runtimeOnly'        | [ANT_1_10]         | []                 | 'removed plugin'  | SUCCESS      | []

        'testCompileOnly'    | []                 | [LOG4J_API_2_13_0] | 'added library'   | UP_TO_DATE   | []
        'testCompileOnly'    | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed library' | UP_TO_DATE   | []
        'testCompileOnly'    | [LOG4J_API_2_13_0] | []                 | 'removed library' | UP_TO_DATE   | []

        'testCompileOnly'    | []                 | [ANT_1_11]         | 'added plugin'    | UP_TO_DATE   | []
        'testCompileOnly'    | [ANT_1_10]         | [ANT_1_11]         | 'changed plugin'  | UP_TO_DATE   | []
        'testCompileOnly'    | [ANT_1_10]         | []                 | 'removed plugin'  | UP_TO_DATE   | []

        'testImplementation' | []                 | [LOG4J_API_2_13_0] | 'added library'   | UP_TO_DATE   | []
        'testImplementation' | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed library' | UP_TO_DATE   | []
        'testImplementation' | [LOG4J_API_2_13_0] | []                 | 'removed library' | UP_TO_DATE   | []

        'testImplementation' | []                 | [ANT_1_11]         | 'added plugin'    | SUCCESS      | ['ant', 'structs']
        'testImplementation' | [ANT_1_10]         | [ANT_1_11]         | 'changed plugin'  | SUCCESS      | ['ant', 'structs']
        'testImplementation' | [ANT_1_10]         | []                 | 'removed plugin'  | SUCCESS      | []

        'testRuntimeOnly'    | []                 | [LOG4J_API_2_13_0] | 'added library'   | UP_TO_DATE   | []
        'testRuntimeOnly'    | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed library' | UP_TO_DATE   | []
        'testRuntimeOnly'    | [LOG4J_API_2_13_0] | []                 | 'removed library' | UP_TO_DATE   | []

        'testRuntimeOnly'    | []                 | [ANT_1_11]         | 'added plugin'    | SUCCESS      | ['ant', 'structs']
        'testRuntimeOnly'    | [ANT_1_10]         | [ANT_1_11]         | 'changed plugin'  | SUCCESS      | ['ant', 'structs']
        'testRuntimeOnly'    | [ANT_1_10]         | []                 | 'removed plugin'  | SUCCESS      | []
    }

    def 'should work with configuration cache'() {
        given:
        def made = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [
                        ['configuration': 'implementation', 'coordinate': q(ANT_1_11)]
                ],
        ])
        build.withWriter { made.writeTo(it) }

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '--configuration-cache')
                .build()

        then:
        result.task(taskPath).outcome == SUCCESS
    }

    def 'should work with project dependencies'() {
        given:
        def (dep, consumer) = ['my-dep-plugin-one', 'my-consumer'].collect {
            def f = inProjectDir(it)
            f.mkdirs()
            settings << "\ninclude '$it'"
            f
        }
        def depBuild = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [],
        ])
        new File(dep, 'build.gradle').withWriter { depBuild.writeTo(it) }
        def consumerBuild = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [
                        ['configuration': 'implementation', 'coordinate': "project(':my-dep-plugin-one')"]
                ],
        ])
        new File(consumer, 'build.gradle').withWriter { consumerBuild.writeTo(it) }

        when:
        gradleRunner()
                .withArguments("my-consumer$taskPath")
                .build()

        then:
        def actual = new File(consumer, 'build/jpi-plugin/generatedJenkinsTest/test-dependencies/index')
        actual.exists()
        actual.readLines() == ['my-dep-plugin-one']
        new File(consumer, 'build/jpi-plugin/generatedJenkinsTest/test-dependencies/my-dep-plugin-one.jpi').exists()
    }

    @PendingFeature
    def 'should work with project dependencies that have transitive plugins'() {
        given:
        def (dep, consumer) = ['my-dep-plugin-one', 'my-consumer'].collect {
            def f = inProjectDir(it)
            f.mkdirs()
            settings << "\ninclude '$it'"
            f
        }
        def depBuild = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [['configuration': 'implementation', 'coordinate': q(ANT_1_11)]],
        ])
        new File(dep, 'build.gradle').withWriter { depBuild.writeTo(it) }
        def consumerBuild = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [
                        ['configuration': 'implementation', 'coordinate': "project(':my-dep-plugin-one')"]
                ],
        ])
        new File(consumer, 'build.gradle').withWriter { consumerBuild.writeTo(it) }

        when:
        gradleRunner()
                .withArguments("my-consumer$taskPath")
                .build()

        then:
        def actual = new File(consumer, 'build/jpi-plugin/generatedJenkinsTest/test-dependencies/index')
        actual.exists()
        actual.readLines().toSorted() == ['ant', 'my-dep-plugin-one', 'structs']
        actual.eachLine {
            assert new File(consumer, "build/jpi-plugin/generatedJenkinsTest/test-dependencies/${it}.jpi").exists()
        }
    }
}
