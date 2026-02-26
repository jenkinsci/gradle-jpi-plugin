package org.jenkinsci.gradle.plugins.jpi

import com.gradle.develocity.testing.annotations.LocalOnly
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

/**
 * Tests for backwards-compatibility.
 * @deprecated To be removed in 1.0.0
 */
@Deprecated
@LocalOnly
class JpiLocalizerTaskIntegrationSpec extends IntegrationSpec {
    static final LEGACY_TASK = 'localizer'
    static final LEGACY_TASK_PATH = ':' + LEGACY_TASK
    static final REPLACEMENT_TASK_PATH = ':localizeMessages'

    @Unroll
    def 'single-module project should be able to run LocalizerTask (#dir)'(String dir, String expected) {
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
                .withArguments(LEGACY_TASK)
                .build()

        then:
        result.task(LEGACY_TASK_PATH).outcome == TaskOutcome.SKIPPED
        result.task(REPLACEMENT_TASK_PATH).outcome == TaskOutcome.SUCCESS
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
                .withArguments('--configuration-cache', LEGACY_TASK, '-i')
                .build()

        then:
        result.task(LEGACY_TASK_PATH).outcome == TaskOutcome.SKIPPED
        result.task(REPLACEMENT_TASK_PATH).outcome == TaskOutcome.SUCCESS
    }

    def 'multi-module project should be able to run LocalizerTask'() {
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
        String namespacedLegacyTask = ':plugin' + LEGACY_TASK_PATH

        when:
        def result = gradleRunner()
                .withArguments(namespacedLegacyTask)
                .build()

        then:
        result.task(namespacedLegacyTask).outcome == TaskOutcome.SKIPPED
        result.task(':plugin' + REPLACEMENT_TASK_PATH).outcome == TaskOutcome.SUCCESS
        def generatedJavaFile = inProjectDir('plugin/build/generated-src/localizer/Messages.java')
        generatedJavaFile.exists()
        generatedJavaFile.text.contains('public static String key3()')
        generatedJavaFile.text.contains('public static String key4()')
    }
}
