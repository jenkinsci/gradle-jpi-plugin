package org.jenkinsci.gradle.plugins.manifest

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport

import java.nio.file.Path
import java.util.jar.Manifest

import static java.util.jar.Attributes.Name.MANIFEST_VERSION

class GeneratePluginClassManifestTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskPath = ':' + GeneratePluginClassManifestTask.NAME
    private File build
    private Path srcMainJava
    private Path srcMainGroovy

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()
        srcMainJava = inProjectDir('src/main/java').toPath()
        srcMainGroovy = inProjectDir('src/main/groovy').toPath()
    }

    def 'should list legacy hudson.Plugin implementation'() {
        given:
        def expected = new Manifest()
        expected.mainAttributes[MANIFEST_VERSION] = '1.0'
        expected.mainAttributes.putValue('Plugin-Class', 'my.example.TestPlugin')
        def plugin = TypeSpec.classBuilder('TestPlugin')
                .superclass(ClassName.get('hudson', 'Plugin'))
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        actualManifest() == expected

        when:
        def rerunResult = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.UP_TO_DATE
    }

    def 'should work without any legacy hudson.Plugin implementation'() {
        given:
        def expected = new Manifest()
        expected.mainAttributes[MANIFEST_VERSION] = '1.0'
        def plugin = TypeSpec.classBuilder('SomeClass').build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        actualManifest() == expected

        when:
        def rerunResult = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.UP_TO_DATE
    }

    def 'should error on multiple legacy hudson.Plugin implementations'() {
        given:
        def plugin = TypeSpec.classBuilder('TestPlugin')
                .superclass(ClassName.get('hudson', 'Plugin'))
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)
        def plugin2 = TypeSpec.classBuilder('TestPlugin2')
                .superclass(ClassName.get('hudson', 'Plugin'))
                .build()
        def myExamplePlugin2 = JavaFile.builder('my.example', plugin2).build()
        myExamplePlugin2.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME)
                .buildAndFail()

        then:
        result.output.contains('javax.annotation.processing.FilerException: Attempt to reopen a file for path')
    }

    def 'should error on multiple source sets legacy hudson.Plugin implementations'() {
        given:
        def plugin = TypeSpec.classBuilder('TestPlugin')
                .superclass(ClassName.get('hudson', 'Plugin'))
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)
        def plugin2 = TypeSpec.classBuilder('TestPlugin2')
                .superclass(ClassName.get('hudson', 'Plugin'))
                .build()
        def myExamplePlugin2 = JavaFile.builder('my.example', plugin2).build()
        myExamplePlugin2.writeTo(srcMainGroovy)

        when:
        def result = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME)
                .buildAndFail()

        then:
        !existsRelativeToProjectDir('build/hudson/plugins.txt')
        result.output.contains('Must not have more than 1 legacy hudson.Plugin subclass')
    }

    def 'should support configuration cache'() {
        given:
        def plugin = TypeSpec.classBuilder('TestPlugin')
                .superclass(ClassName.get('hudson', 'Plugin'))
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(GeneratePluginClassManifestTask.NAME, '--configuration-cache')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def actualManifest() {
        new Manifest(inProjectDir('build/jenkins-manifests/plugin-class.mf').newInputStream())
    }
}
