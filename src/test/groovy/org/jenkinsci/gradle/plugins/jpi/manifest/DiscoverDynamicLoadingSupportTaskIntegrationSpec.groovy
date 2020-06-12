package org.jenkinsci.gradle.plugins.jpi.manifest

import jenkins.YesNoMaybe
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

import java.util.jar.Manifest

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
    def 'should call dynamic loading #secondary .#language in #dir'(String dir,
                                                                    String language,
                                                                    YesNoMaybe secondary,
                                                                    boolean expected) {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        Manifest expectedManifest = new Manifest()
        expectedManifest.mainAttributes.putValue('Manifest-Version', '1.0')
        expectedManifest.mainAttributes.putValue('Support-Dynamic-Loading', expected.toString())
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
        def actual = new File(projectDir.root, 'build/discovered/dynamic-loading-support.mf')

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS
        new Manifest(actual.newInputStream()) == expectedManifest

        and:
        def rerunResult = gradleRunner()
                .withArguments(DiscoverDynamicLoadingSupportTask.TASK_NAME)
                .build()

        then:
        rerunResult.task(taskPath()).outcome == TaskOutcome.UP_TO_DATE
        new Manifest(actual.newInputStream()) == expectedManifest

        where:
        dir      | language | secondary      | expected
        'java'   | 'java'   | YesNoMaybe.YES | true
        'groovy' | 'groovy' | YesNoMaybe.YES | true
        'groovy' | 'java'   | YesNoMaybe.YES | true
        'java'   | 'java'   | YesNoMaybe.NO  | false
        'groovy' | 'groovy' | YesNoMaybe.NO  | false
        'groovy' | 'java'   | YesNoMaybe.NO  | false
    }

    @Unroll
    def 'should not store dynamic loading if MAYBE .#language in #dir'(String dir, String language) {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        Manifest expectedManifest = new Manifest()
        expectedManifest.mainAttributes.putValue('Manifest-Version', '1.0')
        projectDir.newFolder('src', 'main', dir, 'my', 'example')
        projectDir.newFile("src/main/${dir}/my/example/${name}.${language}") << """\
            package $pkg;

            @hudson.Extension(dynamicLoadable = jenkins.YesNoMaybe.MAYBE)
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
        def actual = new File(projectDir.root, 'build/discovered/dynamic-loading-support.mf')

        then:
        result.task(taskPath()).outcome == TaskOutcome.SUCCESS
        new Manifest(actual.newInputStream()) == expectedManifest

        and:
        def rerunResult = gradleRunner()
                .withArguments(DiscoverDynamicLoadingSupportTask.TASK_NAME)
                .build()

        then:
        rerunResult.task(taskPath()).outcome == TaskOutcome.UP_TO_DATE
        new Manifest(actual.newInputStream()) == expectedManifest

        where:
        dir      | language
        'java'   | 'java'
        'groovy' | 'groovy'
        'groovy' | 'java'
    }

    private static String taskPath() {
        ':' + DiscoverDynamicLoadingSupportTask.TASK_NAME
    }
}
