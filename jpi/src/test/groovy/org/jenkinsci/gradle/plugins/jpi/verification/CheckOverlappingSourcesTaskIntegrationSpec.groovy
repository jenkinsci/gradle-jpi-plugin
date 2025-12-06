package org.jenkinsci.gradle.plugins.jpi.verification

import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

class CheckOverlappingSourcesTaskIntegrationSpec extends IntegrationSpec {
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
            '''.stripIndent()
    }

    def 'should be dependency of check'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('check')
                .build()

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS

        when:
        def rerunResult = gradleRunner()
                .withArguments('check')
                .build()

        then:
        rerunResult.task(taskPath()).outcome == TaskOutcome.UP_TO_DATE
    }

    @Unroll
    def 'should pass if sole legacy plugin implemented as .#language in #dir'(String dir, String language) {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        mkDirInProjectDir("src/main/${dir}/my/example")
        touchInProjectDir("src/main/${dir}/my/example/TestPlugin.${language}") << """\
            package $pkg;

            public class $name extends hudson.Plugin {
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(CheckOverlappingSourcesTask.TASK_NAME)
                .build()

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS

        where:
        dir      | language
        'java'   | 'java'
        'groovy' | 'groovy'
        'groovy' | 'java'
    }

    def 'should fail if legacy plugins implemented in java and groovy dirs'() {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        ['java', 'groovy'].eachWithIndex { dir, idx ->
            mkDirInProjectDir("src/main/${dir}/my/example")
            touchInProjectDir("src/main/${dir}/my/example/TestPlugin${idx}.java") << """\
            package $pkg;

            public class ${name}${idx} extends hudson.Plugin {
            }
            """.stripIndent()
        }

        when:
        def result = gradleRunner()
                .withArguments(CheckOverlappingSourcesTask.TASK_NAME)
                .buildAndFail()

        then:
        result.task(taskPath()).outcome == TaskOutcome.FAILED
        result.output.contains('Found multiple directories containing Jenkins plugin implementations ')
    }

    @Unroll
    def 'should pass with extensions defined in #dir'(String dir, String language) {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        mkDirInProjectDir("src/main/${dir}/my/example")
        touchInProjectDir("src/main/${dir}/my/example/TestPlugin.${language}") << """\
            package $pkg;

            @hudson.Extension
            public class $name {
            }
            """.stripIndent()
        touchInProjectDir("src/main/${dir}/my/example/OtherTestPlugin.java") << """\
            package $pkg;

            @hudson.Extension
            public class OtherTestPlugin {
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(CheckOverlappingSourcesTask.TASK_NAME)
                .build()

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS

        where:
        dir      | language
        'java'   | 'java'
        'groovy' | 'groovy'
        'groovy' | 'java'
    }

    def 'should fail with extensions defined in java and groovy dirs'() {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        ['java', 'groovy'].eachWithIndex { dir, idx ->
            mkDirInProjectDir("src/main/${dir}/my/example")
            touchInProjectDir("src/main/${dir}/my/example/TestPlugin${idx}.java") << """\
            package $pkg;

            @hudson.Extension
            public class ${name}${idx} {
            }
            """.stripIndent()
        }

        when:
        def result = gradleRunner()
                .withArguments(CheckOverlappingSourcesTask.TASK_NAME)
                .buildAndFail()

        then:
        result.task(taskPath()).outcome == TaskOutcome.FAILED
        result.output.contains('Found overlapping Sezpoz file: ')
    }

    private static String taskPath() {
        ':' + CheckOverlappingSourcesTask.TASK_NAME
    }
}
