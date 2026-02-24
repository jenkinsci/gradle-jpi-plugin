package org.jenkinsci.gradle.plugins.jpi.localization

import com.gradle.develocity.testing.annotations.LocalOnly
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

@LocalOnly
class LocalizationTaskIntegrationSpec extends IntegrationSpec {
    static final String TASK_NAME = 'localizeMessages'

    @Unroll
    def '#task should run LocalizationTask'(String task) {
        given:
        String path = ':' + task
        touchInProjectDir('build.gradle') << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        mkDirInProjectDir('src/main/java/org/example')
        touchInProjectDir('src/main/java/org/example/Calculator.java') << 'package org.example;\n\nclass Calculator {}'
        mkDirInProjectDir('src/main/resources/org/example')
        touchInProjectDir('src/main/resources/org/example/Messages.properties') << 'key1=value1\nkey2=value2'
        touchInProjectDir('src/main/resources/Messages.properties') << 'key3=value1\nkey4=value2'

        when:
        def result = gradleRunner()
                .withArguments(task)
                .build()

        then:
        result.task(':' + TASK_NAME).outcome == TaskOutcome.SUCCESS
        result.task(path).outcome == TaskOutcome.SUCCESS

        where:
        task << ['classes', 'sourcesJar']
    }

    @Unroll
    def 'single-module project should be able to run LocalizationTask (#dir)'(String dir, String expected) {
        given:
        touchInProjectDir('build.gradle') << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                localizerOutputDir = $dir
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        mkDirInProjectDir('src/main/resources/org/example')
        touchInProjectDir('src/main/resources/org/example/Messages.properties') << 'key1=value1\nkey2=value2'
        touchInProjectDir('src/main/resources/Messages.properties') << 'key3=value1\nkey4=value2'

        when:
        def result = gradleRunner()
                .withArguments(TASK_NAME)
                .build()

        then:
        result.task(':' + TASK_NAME).outcome == TaskOutcome.SUCCESS
        def orgExampleMessagesPath = "$expected/org/example/Messages.java"
        def orgExampleMessages = inProjectDir(orgExampleMessagesPath)
        existsRelativeToProjectDir(orgExampleMessagesPath)
        orgExampleMessages.text.contains('public static String key1()')
        orgExampleMessages.text.contains('public static String key2()')
        def messagesPath = "$expected/Messages.java"
        def messages = inProjectDir(messagesPath)
        existsRelativeToProjectDir(messagesPath)
        messages.text.contains('public static String key3()')
        messages.text.contains('public static String key4()')

        where:
        dir     | expected
        null    | 'build/generated-src/localizer'
        "''"    | 'build/generated-src/localizer'
        "'foo'" | 'foo'
    }

    def 'should support configuration cache'() {
        given:
        touchInProjectDir('build.gradle') << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        mkDirInProjectDir('src/main/resources/org/example')
        touchInProjectDir('src/main/resources/org/example/Messages.properties') << 'key1=value1\nkey2=value2'

        when:
        def result = gradleRunner()
                .withArguments('--configuration-cache', TASK_NAME, '-i')
                .build()

        then:
        result.task(':' + TASK_NAME).outcome == TaskOutcome.SUCCESS
    }

    def 'multi-module project should be able to run LocalizationTask'() {
        given:
        touchInProjectDir('build.gradle') << ''
        touchInProjectDir('settings.gradle') << 'include ":plugin"'
        mkDirInProjectDir('plugin/src/main/resources')
        touchInProjectDir('plugin/build.gradle') << """\
            plugins { id 'org.jenkins-ci.jpi' }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        touchInProjectDir('plugin/src/main/resources/Messages.properties') << 'key3=value3\nkey4=value4'

        when:
        def result = gradleRunner()
                .withArguments(':plugin:' + TASK_NAME)
                .build()

        then:
        result.task(':plugin:' + TASK_NAME).outcome == TaskOutcome.SUCCESS
        def generatedJavaFile = inProjectDir('plugin/build/generated-src/localizer/Messages.java')
        generatedJavaFile.exists()
        generatedJavaFile.text.contains('public static String key3()')
        generatedJavaFile.text.contains('public static String key4()')
    }
}
