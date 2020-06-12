package org.jenkinsci.gradle.plugins.jpi.discovery

import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

import java.util.jar.Manifest

class DiscoverPluginClassTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = projectDir.newFile('build.gradle')
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
        Manifest expected = new Manifest()
        expected.mainAttributes.putValue('Manifest-Version', '1.0')
        expected.mainAttributes.putValue('Plugin-Class', 'my.example.TestPlugin')
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        projectDir.newFolder('src', 'main', dir, 'my', 'example')
        projectDir.newFile("src/main/${dir}/my/example/${name}.${language}") << """\
            package $pkg;

            public class $name extends hudson.Plugin {
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(DiscoverPluginClassTask.TASK_NAME)
                .build()
        def actual = new File(projectDir.root, 'build/discovered/plugin-class.mf')

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS
        new Manifest(actual.newInputStream()) == expected

        and:
        def rerunResult = gradleRunner()
                .withArguments(DiscoverPluginClassTask.TASK_NAME)
                .build()

        then:
        rerunResult.task(taskPath()).outcome == TaskOutcome.UP_TO_DATE
        new Manifest(actual.newInputStream()) == expected

        where:
        dir      | language
        'java'   | 'java'
        'groovy' | 'groovy'
        'groovy' | 'java'
    }

    @Unroll
    def 'should pass if no legacy plugin implemented as .#language in #dir'(String dir, String language) {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        Manifest expected = new Manifest()
        expected.mainAttributes.putValue('Manifest-Version', '1.0')
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        projectDir.newFolder('src', 'main', dir, 'my', 'example')
        projectDir.newFile("src/main/${dir}/my/example/${name}.${language}") << """\
            package $pkg;

            @hudson.Extension
            public class $name {
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(DiscoverPluginClassTask.TASK_NAME)
                .build()
        def actual = new File(projectDir.root, 'build/discovered/plugin-class.mf')

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS
        new Manifest(actual.newInputStream()) == expected

        and:
        def rerunResult = gradleRunner()
                .withArguments(DiscoverPluginClassTask.TASK_NAME)
                .build()

        then:
        rerunResult.task(taskPath()).outcome == TaskOutcome.UP_TO_DATE
        new Manifest(actual.newInputStream()) == expected

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
            projectDir.newFolder('src', 'main', dir, 'my', 'example')
            projectDir.newFile("src/main/${dir}/my/example/TestPlugin${idx}.java") << """\
            package $pkg;

            public class ${name}${idx} extends hudson.Plugin {
            }
            """.stripIndent()
        }

        when:
        def result = gradleRunner()
                .withArguments(DiscoverPluginClassTask.TASK_NAME)
                .buildAndFail()

        then:
        result.task(taskPath()).outcome == TaskOutcome.FAILED
        result.output.contains('Found multiple directories containing Jenkins plugin implementations ')
    }

    private static String taskPath() {
        ':' + DiscoverPluginClassTask.TASK_NAME
    }
}
