package org.jenkinsci.gradle.plugins.jpi.discovery

import hudson.Extension
import jenkins.YesNoMaybe
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Ignore
import spock.lang.PendingFeature
import spock.lang.Unroll

class DiscoverDynamicLoadingSupportTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = projectDir.newFile('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
    }

    @Unroll
    def 'should call dynamic loading #secondary .#language in #dir'(String dir, String language, YesNoMaybe secondary) {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        String expectedText = """\
            ${secondary.name()}
            """.stripIndent().denormalize()
        projectDir.newFolder('src', 'main', dir, 'my', 'example')
        projectDir.newFile("src/main/${dir}/my/example/${name}.${language}") << """\
            package $pkg;

            @hudson.Extension(dynamicLoadable = jenkins.YesNoMaybe.${secondary.name()})
            public class $name {
            }
            """.stripIndent()
        projectDir.newFile("src/main/${dir}/my/example/A.${language}") << """\
            package $pkg;

            @hudson.Extension(dynamicLoadable = jenkins.YesNoMaybe.YES)
            public class A {
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(DiscoverDynamicLoadingSupportTask.TASK_NAME)
                .build()

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS
        new File(projectDir.root, 'build/discovered/dynamic-loading-support.txt').text == expectedText

        and:
        def rerunResult = gradleRunner()
                .withArguments(DiscoverDynamicLoadingSupportTask.TASK_NAME)
                .build()

        then:
        rerunResult.task(taskPath()).outcome == TaskOutcome.UP_TO_DATE
        new File(projectDir.root, 'build/discovered/dynamic-loading-support.txt').text == expectedText

        where:
        dir      | language | secondary
        'java'   | 'java'   | YesNoMaybe.YES
        'groovy' | 'groovy' | YesNoMaybe.YES
        'groovy' | 'java'   | YesNoMaybe.YES
        'java'   | 'java'   | YesNoMaybe.MAYBE
        'groovy' | 'groovy' | YesNoMaybe.MAYBE
        'groovy' | 'java'   | YesNoMaybe.MAYBE
        'java'   | 'java'   | YesNoMaybe.NO
        'groovy' | 'groovy' | YesNoMaybe.NO
        'groovy' | 'java'   | YesNoMaybe.NO
    }

    private static String taskPath() {
        ':' + DiscoverDynamicLoadingSupportTask.TASK_NAME
    }
}
